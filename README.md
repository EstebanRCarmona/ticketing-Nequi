# 🎟️ App Ticketing Nequi — Reactive Ticket Purchase System

High-performance, high-concurrency ticketing microservice built with **Java 25**, **Spring Boot 4.x**, **Spring WebFlux**, **AWS DynamoDB** and **Amazon SQS**, following **Bancolombia Clean Architecture**.

---

## 🏗️ Architecture Overview

```
RouterRest → EventHandler / OrderHandler → ValidationHelper → UseCase → Adapters (DynamoDB / SQS)
```

| Layer | Responsibility |
|---|---|
| `entry-points/reactive-web` | HTTP routing, request/response mapping, circuit breakers |
| `domain/usecase` | Business logic, orchestration, state machine |
| `domain/model` | Entities, enums, gateway interfaces |
| `driven-adapters/dynamodb-reactive` | DynamoDB persistence with conditional writes |
| `driven-adapters/sqs-sender` | Async event publishing to SQS |
| `driven-adapters/sqs-listener` | At-least-once SQS consumer |

---

## 🔄 Order Lifecycle

![Order lifecycle sequence diagram](docs/diagramas-secuencia/orders_flow.png)

The sequence diagram shows the main order states and transitions across reservation, confirmation, asynchronous payment, cancellation, expiration, and order history lookup.

States `SOLD`, `COMPLIMENTARY`, `CANCELLED` and `EXPIRED` are **terminal** — no transitions out.

---

## 📝 Technical Documentation

Technical documentation is included as design notes in [docs/technical-documentation.md](docs/technical-documentation.md).

Use this document to review the architectural decisions, concurrency strategy, async processing model, and production trade-offs behind the implementation.

---

## 🚀 Quick Start with Docker

### Prerequisites
- **Docker** and **Docker Compose**
- **Java 25** and **Gradle** (to build the JAR locally)

### 1. Build the JAR locally

The Docker image does **not** compile the project — it only packages a pre-built JAR.
You must generate it before running Docker:

```bash
./gradlew bootJar -x test
```

This produces `applications/app-service/build/libs/app-ticketing-nequi.jar`.

> If you skip this step, Docker will fail with:
> `COPY failed: no files found for applications/app-service/build/libs/*.jar`

### 2. Grant execute permission to the init script

The LocalStack initialization script must be executable before Docker mounts it:

```bash
chmod +x deployment/init-aws.sh
```

> If you skip this step, LocalStack will start but tables and queues will **not** be created, and the app will fail to connect.

### 3. Start the full environment

```bash
docker-compose down && docker-compose up --build -d
```

The `down` ensures any previous containers are removed before starting fresh.

This starts:
- **LocalStack** — emulates DynamoDB (5 tables) and SQS (2 queues + 2 DLQs) on port `4566`
- **App Ticketing Nequi** — reactive microservice on port `8080`

Expected startup times:
- ~16s — LocalStack boots
- ~10s — `init-aws.sh` creates tables and queues
- ~30s — Spring Boot app starts

> Total: ~1 minute until everything is `healthy`. You can monitor with:
> ```bash
> docker-compose ps
> docker logs app_ticketing_nequi -f
> ```

### Rebuild after code changes

```bash
docker-compose down && ./gradlew bootJar -x test && docker-compose up --build -d
```

### Stop

```bash
docker-compose down
```

---

## ⚙️ Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `AWS_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | `test` | AWS access key (LocalStack) |
| `AWS_SECRET_ACCESS_KEY` | `test` | AWS secret key (LocalStack) |
| `AWS_DYNAMODB_ENDPOINT` | `http://localhost:4566` | DynamoDB endpoint |
| `AWS_SQS_ENDPOINT` | `http://localhost:4566` | SQS endpoint |
| `TABLE_EVENTS` | `ticketing-events` | Events table name |
| `TABLE_INVENTORY` | `ticketing-inventory` | Inventory table name |
| `TABLE_ORDERS` | `ticketing-orders` | Orders table name |
| `TABLE_ORDER_HISTORY` | `ticketing-order-history` | Audit history table |
| `TABLE_IDEMPOTENCY` | `ticketing-idempotency` | Idempotency table |
| `AWS_SQS_PROCESSING_QUEUE_URL` | `http://localhost:4566/000000000000/order-processing-queue` | Finalize order queue |
| `AWS_SQS_EXPIRATION_QUEUE_URL` | `http://localhost:4566/000000000000/order-expiration-queue` | Expiration queue |
| `AWS_SQS_POLL_INTERVAL_MS` | `5000` | SQS polling interval (ms) |
| `EXPIRED_RESERVATIONS_INTERVAL_MS` | `60000` | Scheduler sweep interval (ms) |
| `CB_SLIDING_WINDOW_SIZE` | `10` | Circuit breaker window size |
| `CB_MIN_CALLS` | `5` | Minimum calls before CB evaluates |
| `CB_FAILURE_RATE_THRESHOLD` | `50` | Failure % to open circuit |
| `CB_WAIT_DURATION_OPEN` | `30s` | Time in open state |
| `CB_SLOW_CALL_DURATION` | `10s` | Slow call threshold |

---

## 📡 API Reference

All responses follow the envelope:
```json
{
  "statusCode": "0",
  "description": "SUCCESS",
  "data": { ... }
}
```

### Events

#### Create Event — `POST /api/v1/events`
Requires `X-Role: ADMIN`.

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -H "X-Role: ADMIN" \
  -H "X-Trace-Id: trace-001" \
  -d '{
    "name": "Concierto Shakira 2026",
    "eventDate": "2026-11-15T20:00:00Z",
    "venue": "Estadio El Campín",
    "category": "CONCERT",
    "totalCapacity": 50000,
    "ticketPrice": 150000.00
  }'
```

#### List Events — `GET /api/v1/events`

```bash
curl "http://localhost:8080/api/v1/events?limit=10"
```

#### Get Event Detail + Real-time Availability — `GET /api/v1/events/{eventId}`

```bash
curl http://localhost:8080/api/v1/events/<EVENT_ID>
```

### Orders

#### Create Order (Reserve) — `POST /api/v1/orders`
Requires `Idempotency-Key` header.

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-client-key-001" \
  -H "X-User-Id: usr-client-001" \
  -H "X-Trace-Id: trace-order-001" \
  -d '{
    "eventId": "<EVENT_ID>",
    "quantity": 2
  }'
```

Returns HTTP `201` with order in `RESERVED` state. Reservation expires in **10 minutes**.

#### Get Order Status — `GET /api/v1/orders/{orderId}`

```bash
curl http://localhost:8080/api/v1/orders/<ORDER_ID> \
  -H "X-User-Id: usr-client-001"
```

#### Confirm Order (Trigger Payment) — `POST /api/v1/orders/{orderId}/confirm`

```bash
curl -X POST http://localhost:8080/api/v1/orders/<ORDER_ID>/confirm \
  -H "Content-Type: application/json" \
  -H "X-User-Id: usr-client-001" \
  -d '{ "paymentReference": "tok_card_visa_123" }'
```

Returns HTTP `202`. Payment is processed asynchronously via SQS. Poll `GET /orders/{id}` to check final state (`SOLD` or `CANCELLED`).

#### Cancel Order — `POST /api/v1/orders/{orderId}/cancel`

```bash
curl -X POST http://localhost:8080/api/v1/orders/<ORDER_ID>/cancel \
  -H "Content-Type: application/json" \
  -H "X-User-Id: usr-client-001" \
  -d '{ "reason": "CUSTOMER_REQUEST" }'
```

#### Get Order Audit History — `GET /api/v1/orders/{orderId}/history`

```bash
curl http://localhost:8080/api/v1/orders/<ORDER_ID>/history \
  -H "X-User-Id: usr-client-001"
```

#### Issue Complimentary Tickets — `POST /api/v1/events/{eventId}/complimentary`
Requires `X-Role: ADMIN` and `Idempotency-Key`.

```bash
curl -X POST http://localhost:8080/api/v1/events/<EVENT_ID>/complimentary \
  -H "Content-Type: application/json" \
  -H "X-Role: ADMIN" \
  -H "Idempotency-Key: comp-key-001" \
  -d '{
    "quantity": 5,
    "recipient": "Prensa VIP",
    "reason": "Promoción Medios"
  }'
```

---

## 🔐 Security Headers

Every response includes:

| Header | Value |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; frame-ancestors 'self'; form-action 'self'` |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` |
| `X-Content-Type-Options` | `nosniff` |
| `Cache-Control` | `no-store` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

---

## 🧪 Tests and Coverage

```bash
# Run all tests
./gradlew test

# Generate JaCoCo coverage report
# --no-configuration-cache is required due to a known incompatibility between
# the pitest plugin and Gradle 9's configuration cache
./gradlew jacocoMergedReport --no-configuration-cache
```

Report available at `build/reports/jacocoHtml/index.html`. Open it with the command for your OS:

```bash
# Windows
start build/reports/jacocoHtml/index.html

# macOS
open build/reports/jacocoHtml/index.html

# Linux
xdg-open build/reports/jacocoHtml/index.html
```

Test suite covers:
- All use cases with Mockito mocks and `StepVerifier`
- All DynamoDB adapters with mocked AWS SDK clients
- Handler endpoints via `WebTestClient`
- Security headers via `ConfigTest`
- Architecture rules via ArchUnit

---

## 📁 Project Structure

```
├── applications/app-service        # Spring Boot entry point, config beans
├── domain/
│   ├── model                       # Entities, enums, gateway interfaces
│   └── usecase                     # Business logic (no framework dependencies)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── dynamodb-reactive        # DynamoDB adapters with conditional writes
│   │   ├── sqs-sender               # SQS event publisher
│   │   └── sqs-listener             # SQS at-least-once consumer
│   └── entry-points/
│       └── reactive-web             # Handlers, router, filters, mappers
├── terraform/                       # Infrastructure as Code (AWS resources)
│   ├── dynamo/                      # DynamoDB tables (5 tables + GSIs)
│   ├── sqs/                         # SQS queues and DLQs
│   └── api/                         # ECR, ECS Fargate, ALB, API Gateway, IAM
├── docs/
│   ├── diagramas-secuencia/         # High-level sequence diagrams
│   │   ├── events_flow.puml         # Events API flow (PlantUML source)
│   │   ├── events_flow.png          # Events API flow (image)
│   │   ├── orders_flow.puml         # Orders API flow (PlantUML source)
│   │   └── orders_flow.png          # Orders API flow (image)
│   └── diagramas-flujo/             # Per-endpoint flow diagrams
│       ├── mermaid/                 # Mermaid source code (import in Lucidchart)
│       └── images/                  # Rendered diagram images
└── postman_collection.json          # Postman collection with all endpoints
```

---

## 🗂️ Postman Collection

A ready-to-use Postman collection is available at the root of the project:

```
postman_collection.json
```

Import it directly into Postman. It includes all endpoints pre-configured with example request bodies and headers (`X-Role`, `X-User-Id`, `Idempotency-Key`, etc.).

---

## 🏛️ Infrastructure (Terraform)

The `terraform/` folder contains the AWS infrastructure definition organized in three independent modules, deployed in order:

| Module | Resources |
|---|---|
| `terraform/dynamo/` | 5 DynamoDB tables with GSIs and TTL |
| `terraform/sqs/` | 2 SQS queues + 2 DLQs with redrive policy |
| `terraform/api/` | ECR, ECS Fargate, ALB, API Gateway, IAM roles, CloudWatch, Auto Scaling |

Each module has environment-specific variable files under `env/dev/`, `env/qa/` and `env/pdn/`.

See [`terraform/README.md`](terraform/README.md) for deployment instructions.

---

## 📊 Diagrams

All diagrams are in the `docs/` folder.

### High-level sequence diagrams (`docs/diagramas-secuencia/`)

Two PlantUML diagrams showing the interaction between layers at a high level:

| Diagram | Source | Image |
|---|---|---|
| Events API | `events_flow.puml` | `events_flow.png` |
| Orders API | `orders_flow.puml` | `orders_flow.png` |

### Per-endpoint flow diagrams (`docs/diagramas-flujo/`)

Detailed flow diagram for each endpoint and the SQS consumer, showing business decisions, DynamoDB operations, error paths and HTTP responses:

| Endpoint | Mermaid source | Image |
|---|---|---|
| `POST /api/v1/events` | `mermaid/01-post-events.md` | `images/01-post-events.png` |
| `GET /api/v1/events` | `mermaid/02-get-events.md` | `images/02-get-events.png` |
| `GET /api/v1/events/{eventId}` | `mermaid/03-get-event-by-id.md` | `images/03-get-event-by-id.png` |
| `POST /api/v1/events/{eventId}/complimentary` | `mermaid/04-post-complimentary.md` | `images/04-post-complimentary.png` |
| `POST /api/v1/orders` | `mermaid/05-post-orders.md` | `images/05-post-orders.png` |
| `GET /api/v1/orders/{orderId}` | `mermaid/06-get-order-by-id.md` | `images/06-get-order-by-id.png` |
| `POST /api/v1/orders/{orderId}/confirm` | `mermaid/07-post-confirm-order.md` | `images/07-post-confirm-order.png` |
| `POST /api/v1/orders/{orderId}/cancel` | `mermaid/08-post-cancel-order.md` | `images/08-post-cancel-order.png` |
| `GET /api/v1/orders/{orderId}/history` | `mermaid/09-get-order-history.md` | `images/09-get-order-history.png` |
| SQS Consumer | `mermaid/10-sqs-consumer.md` | `images/10-sqs-consumer.png` |

The Mermaid files can be imported directly into **Lucidchart** to edit or regenerate the diagrams.
