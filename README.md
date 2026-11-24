# QuickBite — Cloud-native Food Delivery System 

A microservices backend for a food delivery platform.

**[Read the QuickBite Handbook (PDF)](docs/QuickBite-Microservices-and-Cloud-Native-Handbook.pdf)**: the complete
reference for the project and a beginner's guide to microservices, Kubernetes and cloud native: every service, design decision and
setting explained, and how to run it all with or without Docker.

## Architecture

```
   Client ──login──► Keycloak :8180 (tokens)
     │ bearer token
     ▼
   ┌──────────────────────┐
   │     API Gateway      │  Spring Cloud Gateway :8080
   └──────────┬───────────┘
   ┌──────────┴───────┬────────────────┬────────────────┐
   ▼                  ▼                ▼                ▼
 Restaurant         Order           Delivery         Customer
 :8081 · gRPC :9091 :8082           :8083            :8084 · gRPC :9094
   ▲                │  │                                ▲
   └── QuoteOrder ──┘  └──────── ResolveCustomer ───────┘

 Apache Kafka 4.1 (KRaft): domain events, transactional outbox + inbox
 PostgreSQL 16: one database per service (+ Keycloak's)
 Prometheus · Tempo · Grafana: metrics, traces, dashboards
```

- **Order placement** is validated and priced server-side: order-service calls restaurant and customer services
  concurrently over gRPC (2 s deadline, circuit breakers).
- **Everything after that is event-driven** over Kafka: driver assignment, order history, the restaurant's
  accept/reject, cancellations. Events go through a transactional outbox and are deduplicated by an inbox.
- **Login and permissions** with Keycloak: the gateway and every service validate the JWT; four roles (`customer`,
  `restaurant`, `driver`, `admin`) plus ownership checks.

## Tech stack

Java 25 · Spring Boot 4 · Spring Cloud Gateway · Spring Security 7 · Keycloak 26 · gRPC · Apache Kafka 4.1 ·
PostgreSQL 16 · Liquibase · Resilience4j · Micrometer, Prometheus, OpenTelemetry, Tempo, Grafana · Docker Compose ·
Kubernetes (Kustomize, k3s) · GitHub Actions

## Quick start

Needs Docker Desktop with about **4 GB** of memory, plus `curl` and `jq` for the smoke test.

```bash
docker compose up --build -d          # first build takes a few minutes
docker compose ps                     # wait until everything is healthy (Keycloak ~1 min)
scripts/smoke-test.sh                 # end-to-end test: login, ordering, gRPC, Kafka, permissions
k6 run scripts/load-test.js           # optional load test: p50/p95/p99 latency per endpoint (brew install k6)
```

| URL | What |
|-----|------|
| http://localhost:8080 | API gateway (all endpoints under `/api`) |
| http://localhost:8180/auth | Keycloak (admin console: `/auth/admin`, `admin` / `admin`) |
| http://localhost:8081-8084/swagger-ui.html | Swagger UI of each service |
| http://localhost:3000 | Grafana dashboards |

Development users (password `quickbite123`): `admin`, `customer1`, `customer2`, `restaurant1`, `driver1`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/auth/realms/quickbite/protocol/openid-connect/token \
  -d grant_type=password -d client_id=quickbite-app -d username=customer1 -d password=quickbite123 | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/customers/me
```

Sample data: `docker exec -i quickbite-postgres psql -U quickbite < scripts/seed-data.sql`.
Stop with `docker compose down` (add `-v` to delete the data).

## Kubernetes

```bash
docker compose build && kubectl apply -k k8s/overlays/local     # Docker Desktop, kind, k3d, minikube
scripts/cloud-deploy.sh root@<vm-ip>                             # fresh Ubuntu VM: k3s, TLS, prod overlay
```

`k8s/base` holds the shared manifests; `k8s/overlays/local` and `k8s/overlays/prod` add what differs per environment;
`observability/` and `keycloak/` are Kustomize components both overlays include.

## Development

```bash
mvn verify                            # all tests (JDK 25; the outbox test uses Docker via Testcontainers)
```

Every pull request runs the tests and the full-stack smoke test in GitHub Actions (`ci.yml`); `master` only accepts
pull requests whose checks pass, and only tested commits are built into images (`images.yml`, published to GHCR).

## Repository layout

```
api-gateway/  restaurant-service/  order-service/  delivery-service/  customer-service/
grpc-contracts/   .proto files and generated stubs
messaging/        transactional outbox, inbox, topic list, retries + dead-letter topics
security/         JWT validation, Keycloak roles, ownership checks
keycloak/         realm (with and without test users) + Kustomize component
observability/    Prometheus, Tempo, Grafana config + dashboard
k8s/              Kustomize base, local and prod overlays, cloud VM scripts
docker/           the one Dockerfile for every service + JVM flags
scripts/          smoke test, seed data, one-command cloud deploy
docs/             the handbook (PDF)
```

## Learn more

The [handbook](docs/QuickBite-Microservices-and-Cloud-Native-Handbook.pdf) covers each topic in depth: the services and
data model, login and permissions, gRPC, Kafka and the outbox, resilience, observability, testing and CI, running
without Docker, the Dockerfile and Compose, JVM tuning, every Kubernetes manifest, cloud deployment, troubleshooting,
and the full API and configuration reference.
