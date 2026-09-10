# Trading Application — Design Document

## 1. Background

### Service

An algorithmic trading application that connects to [Alpaca Markets](https://alpaca.markets/)
to execute a moving-average crossover strategy. It ingests real-time market data,
evaluates the strategy on each bar, and submits orders through Alpaca's brokerage
API. The system runs in paper-trading mode during development, with the same
codebase intended to switch to live trading via configuration only.

### Goal

Automate systematic, rules-based order execution on a fixed schedule aligned to
US market hours, while running as cheaply as possible on GCP — the compute
footprint scales to zero outside the Mon–Fri 9am–5pm ET trading window instead
of running (and billing) 24/7.

### Current status

- **Done:** domain model, port interfaces, Alpaca REST order gateway, Alpaca
  WebSocket market-data gateway, GCP infrastructure (VPC, GKE Autopilot,
  Artifact Registry, Secret Manager, Workload Identity Federation), CI/CD
  pipeline (build → push → Helm deploy), and on/off/schedule control via
  GitHub Actions + Cloud Scheduler.
- **Not yet built:** the moving-average crossover strategy logic itself, JPA
  persistence, and the `trading-consumers` / `trading-observability` modules.
- **Known gap:** the Postgres VM (`trading-db`) is provisioned but has no
  internet egress path, so Postgres/PgBouncer have never actually installed —
  fix deferred until the persistence layer is built.

## 2. Architecture

### Current architecture

```mermaid
flowchart TB
    subgraph GH["GitHub"]
        CI["CI workflow<br/>(build + test)"]
        Deploy["Deploy workflow<br/>(auto, on CI success)"]
        ScaleOn["Scale On workflow<br/>(manual)"]
        ScaleOff["Scale Off workflow<br/>(manual)"]
        Schedule["Enable Schedule workflow<br/>(manual)"]
    end

    subgraph GCP["GCP project: trading-app-nguyee (us-central1)"]
        AR["Artifact Registry<br/>(container images)"]
        SM["Secret Manager<br/>(Alpaca API keys)"]
        WIF["Workload Identity Federation<br/>(keyless GitHub Actions auth)"]

        subgraph VPC["trading-vpc (10.0.0.0/24)"]
            subgraph GKE["GKE Autopilot: trading-cluster"]
                Pod["trading-strategy pod<br/>(Spring Boot)"]
            end
            PG["trading-db VM (e2-micro)<br/>Postgres + PgBouncer<br/>⚠ not yet functional"]
        end

        Sched["Cloud Scheduler<br/>scale-up (9am ET) / scale-down (5pm ET)<br/>Mon–Fri, paused by default"]
    end

    subgraph Alpaca["Alpaca Markets"]
        RESTApi["REST API<br/>(order execution)"]
        WSApi["WebSocket API<br/>(market data)"]
    end

    CI --> Deploy
    Deploy -->|helm upgrade, replicaCount=0| GKE
    Deploy --> AR
    AR --> Pod
    WIF -.auth.-> Deploy
    WIF -.auth.-> ScaleOn
    WIF -.auth.-> ScaleOff
    WIF -.auth.-> Schedule

    ScaleOn -->|kubectl scale --replicas=1| Pod
    ScaleOff -->|kubectl scale --replicas=0| Pod
    Schedule -->|resume jobs| Sched
    Sched -->|patch deployment/scale| Pod

    SM -.secrets.-> Pod
    Pod <-->|orders| RESTApi
    Pod <-->|bars/quotes| WSApi
    Pod -.planned.-> PG
```

### Component breakdown

| Module | Role | Deployed? |
|---|---|---|
| `trading-core` | Domain records (`Bar`, `Order`, `Symbol`, …) and port interfaces (`OrderGateway`, `MarketDataGateway`, `SecretStore`) — no framework dependencies | No (library) |
| `trading-market-data` | Alpaca WebSocket gateway implementing `MarketDataGateway` | No (library, embedded in `trading-strategy`) |
| `trading-strategy` | Alpaca REST order gateway, secret store adapters, Spring Boot entry point | **Yes** — the only Helm-deployed service today |
| `trading-consumers` *(planned)* | Stateless event consumers | Not yet built |
| `trading-observability` *(planned)* | OTel config + Micrometer metrics | Not yet built |
| `trading-infra` | Terraform (GCP) + Helm chart + GitHub Actions workflows | N/A (infra-as-code) |

Only one Kubernetes Deployment exists today (`trading-strategy`), which embeds
both the order-execution and market-data adapters in a single pod. The
port/adapter pattern means the strategy engine (once written) depends only on
`trading-core`'s interfaces, not on Alpaca or Spring directly — swapping
brokers or adding a second venue means writing a new adapter, not touching
the strategy logic.

The **off-by-default + scheduler** design (covered in detail earlier this
session) means the pod normally runs only Mon–Fri 9am–5pm ET; a code deploy
always resets it to 0 replicas, and three manual GitHub Actions workflows
give explicit on/off/schedule control independent of deploys.

### Target architecture

> **Status: not yet built.** This is the intended end-state once the
> strategy engine and event-driven consumers exist — described here for
> planning purposes, not as a reflection of what's deployed today.

![Target architecture: GKE Autopilot with Pub/Sub-decoupled strategy engine, market data gateway, and consumer pods](images/target-architecture.png)

> **Diagram vs. recommended implementation:** the diagram shows Strategy
> engine, Market data gateway, and REST API as three separate always-on
> pods. In practice, **these three are merged into a single always-on pod**
> (see below) — the Pub/Sub and consumer-pod decomposition on the right
> side of the diagram is unaffected and still applies as drawn.

**What changes vs. the current architecture:**

- The single `trading-strategy` pod is retained as **one always-on pod**
  combining the strategy engine (MA crossover + order submission), the
  market data gateway (Alpaca's one-per-account WebSocket connection,
  publishing to Pub/Sub), and the client-facing REST read API — rather than
  splitting these into three separate pods. See "Merged vs. split pod"
  below for the reasoning.
- **Pub/Sub** (`order events`, `market data` topics) replaces direct
  in-process calls to the *consumer* pods, decoupling producers from
  consumers and buffering events so a scaled-to-zero consumer doesn't lose
  fills/bars while it's off. (Within the merged pod itself, strategy ↔
  market-data signaling stays in-process, as it does today.)
- Five single-purpose **consumer pods** (Order state, Portfolio, Risk,
  Notify, Bar store) replace the current all-in-one pod's implicit state,
  each independently deployable and scale-to-zero via Cloud Scheduler (no
  KEDA — kept out of this revision since it isn't installed anywhere in
  this repo today).
- Storage stays on the same free-tier `PostgreSQL on e2-micro` VM (now the
  single source of truth for orders/fills/positions/bars — no Redis/
  Memorystore, which was dropped from this revision specifically to avoid
  its always-on, non-free-tier cost) plus **Cloud Storage** for FIX-log/
  backup archival.

**Merged vs. split pod (Strategy engine + Market data gateway + REST API):**

Merging these three into one pod is the recommended near-term choice:
GKE Autopilot bills per-pod against a per-pod resource-request minimum, so
three separate always-on pods each pay that floor independently — merging
cuts the always-on compute cost to roughly a third to a quarter of the
three-pod variant, which was the dominant cost driver in the ~$11–13/mo
estimate below. It also mirrors what the current architecture already does
successfully (strategy + market data already share one pod today).

The tradeoff is blast radius and coupling: a bug in the read-API code could
crash the process running live order submission and the market-data
WebSocket, and a read-API deploy briefly interrupts trading/market-data
too. At this project's current scale (solo, paper trading, no client UI
yet) that risk is theoretical. **Split triggers** — revisit this decision
if any of these become true: a real client UI drives meaningful read
traffic that needs to scale independently, the app moves off paper trading
onto live capital (where blast-radius isolation starts to matter), or the
read API needs a release cadence independent of the trading engine.

**Other tradeoffs to weigh before building this:**

- Meaningfully more operational surface (6 pods + 2 Pub/Sub topics + mTLS)
  than the current single pod — makes the still-unbuilt
  `trading-observability` module load-bearing rather than optional.
- Splitting Order state / Portfolio / Risk into separate consumers off the
  same topic introduces eventual consistency between them — for
  risk/exposure correctness this needs deliberate idempotency/ordering
  design, not just "add Pub/Sub."
- Not free: Pub/Sub plus the merged pod move this off the ~$0 footprint the
  current architecture and the rest of this project's infra (always-free
  e2-micro, scale-to-zero GKE) have otherwise stuck to. See "Cost
  breakdown" below.
- This event-driven shape is also the natural precursor to the FIX API
  future-consideration noted below — if that migration happens, it would
  build on this architecture rather than the current single-pod one.

**Cost breakdown:**

The diagram's ~$11–13/mo estimate assumed the merged pod runs 24/7
("always-on") with only the 5 consumers scaling to zero. Putting *all six*
pods on the same market-hours schedule `trading-strategy` already uses
today changes that substantially.

Pricing basis: GKE Autopilot in `us-central1` (regular compute class)
charges ~$0.0445/vCPU-hr and ~$0.0049/GiB-hr, with no charge while a pod is
scaled to 0; the cluster's $0.10/hr management fee isn't incremental since
it's already covered on the existing `trading-cluster`.

| Pod | CPU / Mem request (estimate — not yet built) | $/hr |
|---|---|---|
| Merged (strategy + market data + REST) | 500m / 1Gi | $0.027 |
| Order state | 100m / 256Mi | $0.006 |
| Portfolio | 100m / 256Mi | $0.006 |
| Risk | 100m / 256Mi | $0.006 |
| Notify | 50m / 128Mi | $0.003 |
| Bar store | 100m / 256Mi | $0.006 |
| **Combined, all 6 pods** | | **~$0.053/hr** |

| Schedule | Hours/month | Compute cost |
|---|---|---|
| 24/7 (diagram's original assumption) | ~730 | ~$38–39/mo |
| Market-hours only, Mon–Fri 9am–5pm ET (all 6 pods) | ~174 | **~$9/mo** |

Scheduling everything — not just the 5 consumers — cuts compute cost by
~75%. Mechanically this just means extending the existing Cloud Scheduler +
on/off/schedule GitHub Actions pattern to patch all 6 Deployments instead
of 1; no new infrastructure pattern is needed.

Plus the non-compute pieces:

- **Pub/Sub** — first 10 GiB/month of throughput is free, then $40/TiB.
  At this project's scale (order events + a handful of symbols' bars
  during an 8-hour window) this should stay inside the free tier (~$0),
  but message volume can't be pinned down until it's built — tick-level
  market data across many symbols could push it over.
- **Cloud Storage** (FIX logs/backups) — negligible at this scale, ~$0–1/mo.

**Total: roughly $9–11/month** with everything on the market-hours
schedule — under the diagram's original $11–13/mo (which assumed 24/7 for
the always-on pods) and about a quarter of the fully-always-on 6-pod cost.
Pod sizing above is an estimate, not measured — actual CPU/memory needs
could shift this ±30–40% once the strategy/consumer code exists.

## 3. Tech Stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 (Eclipse Temurin) |
| Framework | Spring Boot 3.3.5, Maven multi-module |
| Cloud provider | GCP — GKE Autopilot, Compute Engine (e2-micro, always-free tier), Cloud Scheduler, Secret Manager, Artifact Registry |
| CI/CD | GitHub Actions, authenticated to GCP via Workload Identity Federation (no long-lived service account keys) |
| Deployment | Helm chart applied via `helm upgrade --install` |
| Broker / market data | Alpaca Markets — **REST API** for order execution, **WebSocket API** for market data |
| Persistence | Planned: PostgreSQL + PgBouncer on a standalone Compute Engine VM (not Cloud SQL, to stay in the always-free tier) |

### Alpaca API choice: REST, not FIX

Alpaca exposes both a REST/WebSocket API and a FIX API. This project uses
**REST for orders and WebSocket for market data**, not FIX:

- The strategy (moving-average crossover, evaluated per bar) is not
  latency-sensitive — nothing here approaches the tick-level timing FIX is
  built for.
- REST + WebSocket integrates directly with Spring Boot's standard HTTP/WS
  clients; no FIX engine, session management, or sequence-number recovery
  logic is needed.
- Lower operational overhead: no persistent FIX session to keep alive,
  re-establish after disconnects, or monitor separately from the app itself.

> **Future consideration:** if the architecture evolves toward a pure
> async, event-driven model (e.g. reacting to fills and market data as a
> unified event stream rather than REST request/response + a separate WS
> feed), Alpaca's FIX API would be a better fit — FIX is inherently a
> persistent, message-oriented protocol, which maps more naturally onto an
> event-driven design than polling/request-response REST calls do. Not
> pursued now; noted here for when/if that architectural shift happens.
