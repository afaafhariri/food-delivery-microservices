# Go → Spring Boot Migration Plan

## 1. Goals

1. Rewrite `order-service` and `delivery-service` (Go / Chi / GORM) in Java / Spring Boot, keeping their REST contracts (paths, snake_case JSON, status codes, `{message,status}` errors) and their Kafka topics and event shapes.
2. Add gRPC where a synchronous call between services is the right tool, and build the feature that needs it.
3. Put every JVM service on one platform, with images tuned for containers: small, quick to start, and predictable on memory.

## 2. Target platform

| Concern            | Choice                                                    |
|--------------------|-----------------------------------------------------------|
| JDK                | Java 25 (LTS) — compact object headers, AOT cache, no virtual-thread pinning on `synchronized` (JEP 491) |
| Framework          | Spring Boot 4.0.9 (all 5 services; the Java services move up from EOL 3.2.5) |
| Gateway            | Spring Cloud 2025.1.4 / Gateway Server WebFlux 5.0        |
| gRPC               | Spring gRPC 1.0.3 (grpc-java 1.77, protobuf 4.33)         |
| Build              | One Maven multi-module reactor (root `pom.xml`) plus a shared `grpc-contracts` module |
| Migrations         | Liquibase in every service (golang-migrate SQL ported, written to run on existing databases) |
| Messaging          | Kafka 4.1 in KRaft mode (ZooKeeper container removed)     |

## 3. Where gRPC fits and where it doesn't

**Kafka stays for events** (`order.placed`, `delivery.status.updated`, `restaurant.order.response`, …). They are fire-and-forget, fan out to several consumers, and need to be durable and replayable.

**gRPC is used for synchronous, internal queries where the caller needs an answer before it can respond.** The current system has none. That leaves a real problem: `POST /api/orders` trusts the client for `menu_item_name` and `unit_price`, and never checks that the restaurant, menu items or customer exist. Any client can order a pizza for 0.01.

### New feature: server-side order validation and pricing

```
client ──REST──▶ order-service ──gRPC (parallel, 2s deadline)──▶ restaurant-service  RestaurantCatalog.QuoteOrder
                               └─gRPC───────────────────────────▶ customer-service    CustomerDirectory.ResolveCustomer
                 order-service ──Kafka order.placed (authoritative prices)──▶ delivery / customer
```

* `RestaurantCatalog.QuoteOrder(restaurant_id, [menu_item_id, quantity])` checks that the restaurant is active and that every item is on its menu and available. It returns the authoritative name, unit price, line totals and order total, all as integer minor units (cents), so there is no float rounding.
* `CustomerDirectory.ResolveCustomer(customer_id, address_id?)` checks that the customer exists and is active. If an `address_id` is given, it resolves that saved address. This adds a feature: **order to a saved address** (`address_id` instead of free-text `delivery_address`).
* Both calls run concurrently on async gRPC stubs, with a 2-second deadline and transparent retry on `UNAVAILABLE`.
* Error mapping: `NOT_FOUND` / `FAILED_PRECONDITION` / `INVALID_ARGUMENT` → `400`; `UNAVAILABLE` / `DEADLINE_EXCEEDED` → `503`.
* `menu_item_name` and `unit_price` in the request are now ignored, which keeps the API backward compatible.

Why gRPC rather than REST for this hop: a typed contract shared at compile time (`grpc-contracts`), HTTP/2 multiplexing over one long-lived connection, binary protobuf, deadlines that propagate, and built-in health and reflection services.

Ports: restaurant gRPC `9091`, customer gRPC `9094`. Both are internal to the compose network only.

## 4. Service conversion map

| Go                                   | Spring Boot                                                         |
|--------------------------------------|---------------------------------------------------------------------|
| chi router + handlers                | `@RestController` (snake_case DTO records)                          |
| GORM models / repositories           | JPA entities + Spring Data repositories                             |
| golang-migrate `*.up.sql`            | Liquibase formatted-SQL changelog (`IF NOT EXISTS`, safe on existing volumes) |
| segmentio/kafka-go reader / writer   | `@KafkaListener` / `KafkaTemplate`, published **after commit** (`@TransactionalEventListener`) |
| swaggo                               | springdoc (`/swagger-ui.html`)                                      |
| `/health`                            | `/actuator/health` (liveness / readiness probes)                    |
| goroutines                           | virtual threads (Tomcat, Kafka listeners, gRPC server executor)     |

Bugs fixed during the port:

* **Driver double-assignment race.** The Go code ran "find next available driver, then update" without a lock. The port uses `SELECT … FOR UPDATE SKIP LOCKED` inside one transaction.
* **Duplicate `order.placed` deliveries.** The consumer is now idempotent on `order_id`.
* **Invalid order statuses.** Delivery statuses that are not order statuses (`ASSIGNED`, `EN_ROUTE`) were written straight into `orders.status`. They are now mapped: `EN_ROUTE`→`PICKED_UP`, and `ASSIGNED` is ignored.
* **N+1 queries on order and delivery listings.** Fixed with batch fetching and entity graphs.
* **Events published on rollback.** Events were published even when the DB write rolled back. They are now sent only after commit.

## 5. Container and JVM strategy

**One shared Dockerfile** (`docker/jvm.Dockerfile`, `ARG SERVICE`) for all 5 services:

1. **build** (Maven, JDK 25): `mvn -pl $SERVICE -am package`, with a BuildKit cache mount on `~/.m2` shared across all builds.
2. **jre** (`jlink`): a custom runtime with only the modules the services need (≈60 MB instead of ≈200 MB). It is identical for every service, so the layer is shared on disk.
3. **train**: the Spring Boot jar is extracted (`-Djarmode=tools`), then one training run creates a **JDK 25 AOT cache** (classes pre-parsed, pre-linked and pre-profiled) for much faster startup. The DB and Liquibase are switched off for that run only.
4. **runtime**: `alpine` + jlinked JRE + dependency layer + app layer + AOT cache. Runs as a non-root user with a read-only root filesystem.

**JVM flags** (`docker/jvm.options`, passed as an `@argfile`, so the training run and the runtime always match):

| Flag | Why |
|------|-----|
| `-XX:+UseSerialGC` | Lowest footprint and overhead for ≤1 GB heaps on 1–2 CPUs (what ergonomics would pick anyway, made explicit) |
| `-XX:MaxRAMPercentage=60`, `-XX:InitialRAMPercentage=30` | Container-aware heap. Leaves 40% for metaspace, code cache, thread stacks and Netty direct buffers, so the kernel OOM killer never fires |
| `-XX:+UseCompactObjectHeaders` | 8-byte object headers (JEP 519): 10–20% smaller live heap |
| `-XX:+ExitOnOutOfMemoryError` | Fail fast and let Docker restart the container instead of limping on |
| `-XX:-UsePerfData` | No `/tmp/hsperfdata` mmap; smaller RSS; works with a read-only rootfs |
| `-XX:MaxDirectMemorySize=64m` | Caps Netty/NIO off-heap buffers (they default to the heap size), so RSS stays inside the container limit |
| `-XX:AOTCache=app.aot` | Startup from the AOT cache |
| `-Duser.timezone=UTC` | Consistent timestamps |

`JAVA_OPTS`-style overrides are still possible through the standard `JDK_JAVA_OPTIONS` environment variable.

Left out on purpose: `-Xss` and `-XX:ReservedCodeCacheSize`. They shrink *reserved* address space, not committed memory or RSS, and capping the code cache risks the JIT being disabled. With virtual threads, platform thread stacks no longer matter for memory.

Spring settings: virtual threads, `open-in-view=false`, small Hikari pools, JDBC batch inserts, graceful shutdown.

**Compose**: shared `x-jvm` anchor, KRaft Kafka with a capped heap (ZooKeeper removed), memory and CPU limits per service, readiness-based `depends_on`, read-only containers with a `tmpfs` `/tmp`, log rotation, and gRPC ports not published to the host.

## 6. Execution steps

1. Root reactor `pom.xml`, plus a `grpc-contracts` module with `.proto` files and generated stubs.
2. Port `order-service` and `delivery-service`, then delete the Go sources.
3. Upgrade `restaurant-service`, `customer-service` and `api-gateway` to Boot 4 / Jackson 3. Add the gRPC servers to restaurant and customer.
4. Write the shared Dockerfile, the JVM options and the new `docker-compose.yml`.
5. Verify: `docker compose up`, run `scripts/smoke-test.sh` end to end (REST → gRPC → Kafka → DB), and measure image sizes, startup times and memory.

## 7. Risks and rollback

* The existing `postgres-data` volume is reused, because the migrations are idempotent. Postgres stays on 16, so the data directory format is unchanged.
* The Go implementation stays in git history (`git checkout 407fdde -- order-service delivery-service`).
* Callers that relied on client-supplied prices now receive server prices. This is intentional.
