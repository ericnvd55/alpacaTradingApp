# Trading Application — Claude Code Context

## Project summary
Java 21 / Spring Boot 3 algorithmic trading application running on GCP GKE Autopilot.
Connects to Alpaca Markets REST API for order execution and WebSocket for market data.
Moving average crossover strategy. Paper trading during development.

## Module structure
- trading-core       — domain records + port interfaces (no framework deps)
- trading-market-data — Alpaca WebSocket gateway (Spring WebSocket client)
- trading-strategy   — strategy engine + REST order gateway (Spring Boot)
- trading-consumers  — stateless event consumers (Spring Boot)
- trading-observability — OTel config + TradingMetrics (Micrometer)
- trading-infra      — Terraform (GCP) + Helm chart + GitHub Actions

## Key architectural decisions
- Port/adapter pattern: business logic depends only on interfaces in trading-core
- Spring profiles control which adapter is active: gcp-gke, gcp-cloudrun, aws-lambda
- OrderGateway uses write-ahead client_order_id for idempotency (no FIX)
- OTel Java agent attached to all JVMs via JAVA_TOOL_OPTIONS env var
- e2-micro VM runs PostgreSQL + PgBouncer (always-free tier)
- Pods scale to 0 off market hours via Cloud Scheduler + KEDA

## Environment
- Java 21 (Eclipse Temurin)
- Spring Boot 3.3.x
- Maven multi-module
- GCP project: trading-app-nguyee, region: us-central1
- GKE cluster: trading-cluster
- Paper trading: SPRING_PROFILES_ACTIVE=gcp-gke,paper

## Commands
- Build: mvn compile
- Test: mvn test (uses Testcontainers — Docker required)
- Local run: docker-compose up -d && mvn spring-boot:run -pl trading-strategy
- Deploy: helm upgrade trading-app ./trading-infra/helm/trading-app

## Current status
[Update this as you progress through phases]
Phase 1 complete. Phase 2 in progress — domain model done, JPA implementations pending.
GCP infra (VPC, GKE Autopilot, Postgres VM, Artifact Registry, Secret Manager, Workload Identity
Federation, Cloud Scheduler) provisioned via Terraform against trading-app-nguyee. CI → GKE deploy
pipeline is green end-to-end: trading-strategy Helm chart deployed and running (1/1) on trading-cluster.

## Do not touch
- Never commit .env files or service account keys
- Never hardcode API keys — always read from SecretStore interface
- Never add SQL aggregates to port interfaces (must stay Firestore-compatible)