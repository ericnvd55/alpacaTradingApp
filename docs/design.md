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

### System diagram

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
