# Embedded TestFlow Console

An internal engineering platform for tracking firmware test runs across embedded hardware devices. CI pipelines and on-device test harnesses report results via a REST API; engineers monitor pass rates, failure trends, and device health through a live dashboard.

---

## What it does

- **Ingest** test runs from CI pipelines or on-device harnesses via a typed REST API
- **Track** state transitions through a strict state machine (QUEUED → RUNNING → PASSED / FAILED / TIMED_OUT / NEEDS_REVIEW)
- **Visualise** pass rates, failure distributions by suite and firmware version, device health, and run duration on a live dashboard
- **Stream** real-time run events to the browser over Server-Sent Events — no polling required
- **Export** filtered result sets as CSV or JSON with a single button click
- **Observe** service health through a pre-built Grafana dashboard backed by Prometheus and Micrometer

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser                                                        │
│  React 18 · TanStack Query · React Router · Tailwind CSS        │
│  served by nginx on port 5173                                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP / SSE  (proxied by nginx)
┌───────────────────────────▼─────────────────────────────────────┐
│  Backend  ·  port 8080                                          │
│  Spring Boot 3.3 · Java 21 · Hibernate 6 · Flyway              │
│                                                                 │
│  platform/    API key auth · rate limiter · SSE fanout          │
│  ingestion/   idempotent create + state-machine transitions      │
│  dashboard/   native SQL queries · Redis cache                   │
│  testrun/     JPA Specifications · streaming export             │
│  device/      device registry                                   │
│  firmware/    firmware build registry + validation              │
│  testsuite/   suite and case registry                           │
└──────────┬──────────────────────────┬──────────────────────────┘
           │                          │
    ┌──────▼──────┐            ┌──────▼──────┐
    │ PostgreSQL  │            │    Redis    │
    │  port 5432  │            │  port 6379  │
    └─────────────┘            └─────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Observability                                                  │
│  Prometheus (9090) ← scrapes /actuator/prometheus               │
│  Grafana    (3000) ← pre-provisioned ETFC Overview dashboard    │
└─────────────────────────────────────────────────────────────────┘
```

### Technology choices

| Layer | Technology | Reason |
|---|---|---|
| Backend | Spring Boot 3.3 / Java 21 | Mature ecosystem, virtual threads, strong JPA |
| Database | PostgreSQL 16 | Native `timestamptz`, UUID generation, JSONB for device metadata |
| Cache | Redis 7 | Dashboard query cache + rate-limit sliding windows via Lua scripts |
| Migrations | Flyway | Version-controlled schema with reproducible deploys |
| Frontend | React 18 + TanStack Query | Declarative cache invalidation on SSE events |
| Metrics | Micrometer → Prometheus → Grafana | Standard Spring Boot observability stack |
| Proxy | nginx | `proxy_buffering off` required for SSE streaming |

---

## Project structure

```
embedded-testflow-console/
├── backend/
│   ├── src/main/java/com/etfc/
│   │   ├── platform/          # Auth, rate limiter, SSE, error envelopes
│   │   ├── ingestion/         # Idempotent ingest + state-machine validation
│   │   ├── dashboard/         # Summary queries, Redis caching layer
│   │   ├── testrun/           # Run list, detail, export, JPA Specifications
│   │   ├── device/            # Device registry and query
│   │   ├── firmware/          # Firmware build registry + validation summary
│   │   └── testsuite/         # Test suite and case registry
│   └── src/main/resources/
│       ├── db/migration/      # Flyway SQL migrations (V1, V2)
│       └── application.yml    # Spring configuration
├── frontend/
│   └── src/
│       ├── api/               # Typed fetch client + generated API types
│       ├── components/        # Shell (SSE listener), StatCard, StatusBadge
│       └── pages/             # Dashboard, RunsPage, RunDetailPage
├── ops/
│   ├── grafana/               # Dashboard JSON + datasource provisioning
│   └── prometheus/            # prometheus.yml scrape config
├── scripts/
│   ├── seed.py                # Generates ~200 realistic test runs
│   └── seed.sh                # Shell entry point for seed.py
└── docker-compose.yml
```

---

## Quick start

### Prerequisites

- **Docker Desktop** (or Docker Engine + Compose v2)
- **Python 3.9+** — only needed for the seed script, no extra packages required

### 1. Start the stack

```bash
git clone <repo-url>
cd embedded-testflow-console
docker compose up --build
```

First build takes 3–5 minutes (Gradle fetches dependencies, npm installs packages). All six services start with health-check ordering so the frontend only comes up after the backend is ready.

| Service | URL |
|---|---|
| **Dashboard** | http://localhost:5173 |
| **Backend API** | http://localhost:8080/api |
| **Grafana** | http://localhost:3000 |
| **Prometheus** | http://localhost:9090 |

### 2. Load demo data

```bash
python3 scripts/seed.py
```

Populates the database with:
- 6 test suites (Boot Sequence, Peripheral Bus, Power Management, Network Connectivity, OTA Update, Sensor Fusion)
- 50 test cases across those suites
- 10 devices across 4 board families (STM32H7, STM32F4, NRF52840, ESP32S3, RP2040)
- 5 firmware builds (`v1.0.0` through `v2.0.0-rc1`)
- ~200 test runs with a realistic 60 % PASSED / 20 % FAILED / 10 % TIMED_OUT / 10 % NEEDS_REVIEW split

The script is **idempotent** — running it twice produces the same data. It automatically backs off on 429 rate-limit responses.

### 3. Open the app

Visit http://localhost:5173. The green **Live** badge in the header means Server-Sent Events are active — dashboard data updates automatically when runs are created or change state.

---

## Using the application

### Dashboard

| Panel | What it shows |
|---|---|
| Pass rate & status counts | Overall health at a glance |
| Failure rate by suite | Which test suites are least reliable |
| Failures by firmware | Which firmware version introduced regressions |
| Recently failing devices | Hardware with the most recent failures |
| Slowest suites | Average run duration per suite |
| Latest activity | Last 10 run events, live-updating |

Use the filter bar at the top to narrow by firmware version, environment (`production` / `staging` / `ci`), or date range. All panels update together.

### Test Runs page

Paginated, filterable table of all runs. Available filters: suite, status, device, firmware, failure category, date range. Click any row for the full per-case result breakdown including failure messages and log references. Click **↓ Export CSV** to download the current filtered set.

### Grafana (http://localhost:3000)

Login: **admin / admin**

The **ETFC Overview** dashboard shows:
- HTTP request rate and latency (P50 / P95 / P99)
- Ingestion throughput and invalid state-transition error rate
- Dashboard cache hit rate, rate-limit events, query duration
- JVM heap usage, HikariCP connection pool, GC pause time

---

## REST API

All endpoints except `/api/health` and `/api/events/runs` require:

```
Authorization: Bearer <api-key>
```

The default development key is `demo-key`.

### Endpoints

```
# Registration (idempotent)
POST   /api/devices
POST   /api/firmware-builds
POST   /api/test-suites
POST   /api/test-suites/{suiteId}/cases

# Test run lifecycle
POST   /api/test-runs                        Create a run (starts QUEUED)
PATCH  /api/test-runs/{id}/status            Advance state
POST   /api/test-runs/{id}/results           Ingest per-case results

# Queries
GET    /api/test-runs                        List runs (paginated + filtered)
GET    /api/test-runs/{id}                   Run detail with per-case results
GET    /api/test-runs/export?format=csv      Streaming export (csv or json)
GET    /api/dashboard/summary                Summary (Redis-cached, 30 s TTL)
GET    /api/devices
GET    /api/firmware-builds

# Infrastructure
GET    /api/events/runs                      SSE stream (no auth required)
GET    /api/health
```

### Example: full ingestion flow

```bash
BASE="http://localhost:8080/api"
AUTH="Authorization: Bearer demo-key"

# 1. Register resources (safe to call every CI run — idempotent)
curl -s -X POST $BASE/devices \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"externalDeviceId":"DEV-001","boardRevision":"rev-A","mcuFamily":"STM32H7","environment":"ci"}'

curl -s -X POST $BASE/firmware-builds \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"version":"v1.5.0","branch":"main","commitHash":"abc123","releaseCandidate":false}'

# 2. Create a run
RUN_ID=$(curl -s -X POST $BASE/test-runs \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"externalRunId":"ci-run-001","deviceExternalId":"DEV-001",
       "firmwareVersion":"v1.5.0","suiteName":"Boot Sequence","environment":"ci"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# 3. Mark it running
curl -s -X PATCH $BASE/test-runs/$RUN_ID/status \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"status":"RUNNING"}'

# 4. Ingest per-case results
curl -s -X POST $BASE/test-runs/$RUN_ID/results \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"results":[
    {"caseName":"cold-boot-timing","attemptNumber":1,"status":"PASSED","durationMs":312},
    {"caseName":"watchdog-recovery","attemptNumber":1,"status":"FAILED",
     "durationMs":9800,"failureCategory":"COMMUNICATION_TIMEOUT",
     "failureMessage":"Device did not respond within timeout window"}
  ]}'

# 5. Close the run
curl -s -X PATCH $BASE/test-runs/$RUN_ID/status \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"status":"FAILED","completedAt":"2026-01-01T12:05:00Z"}'
```

### State machine

```
              ┌─────────┐
     ┌───────►│ RUNNING ├───────────────────────┐
     │        └────┬────┘                       │
     │             │                            ▼
┌────┴───┐         │                       ┌────────┐
│ QUEUED │         │──────────────────────►│ PASSED │
└────┬───┘         │                       └────────┘
     │             │──────────────────────►┌────────┐
     │             │                       │ FAILED │
     │             │                       └────────┘
     │             │──────────────────────►┌──────────┐
     │             │                       │ TIMED_OUT│
     │             │                       └──────────┘
     │             │──────────────────────►┌─────────────┐   ┌────────┐
     │             │                       │NEEDS_REVIEW ├──►│ PASSED │
     │             │                       └─────────────┤   └────────┘
     │             │                                     └──►┌────────┐
     └────────────►┌─────────┐                              │ FAILED │
                   │ BLOCKED ├──────────────────────────────┘────────┘
                   └─────────┘
```

Invalid transitions return HTTP 422 with a descriptive message showing which transitions are allowed from the current state.

### Failure categories

| Value | Meaning |
|---|---|
| `FIRMWARE_RESPONSE_MISMATCH` | Observed output differs from expected |
| `DEVICE_UNAVAILABLE` | Device not reachable or did not enumerate |
| `ENVIRONMENT_FAILURE` | Test infrastructure problem (power, cables, host) |
| `COMMUNICATION_TIMEOUT` | Protocol-level timeout (UART, SPI, I2C, USB) |
| `THRESHOLD_MISMATCH` | Measured value outside spec (voltage, timing, etc.) |
| `TEST_HARNESS_FAILURE` | Bug in the test framework itself |
| `FLAKY_TEST` | Non-deterministic — requires manual review |
| `UNCLASSIFIED` | Catch-all for unrecognised strings |

---

## Configuration

| Variable | Default | Description |
|---|---|---|
| `ETFC_API_KEY` | `demo-key` | Bearer token for all API requests |
| `etfc.rate-limit.enabled` | `true` | Enable Redis-backed rate limiter |
| `etfc.rate-limit.limit` | `100` | Max write requests per window per IP per endpoint group |
| `etfc.rate-limit.window-seconds` | `60` | Rate-limit window duration |
| `etfc.cache.ttl-seconds` | `30` | Dashboard summary Redis cache TTL |

To disable authentication in local development, set `ETFC_API_KEY` to an empty string in `docker-compose.yml`.

---

## Local development (outside Docker)

### Backend

Requires Java 21 and access to a running PostgreSQL + Redis (start them with `docker compose up postgres redis -d`).

```bash
cd backend
./gradlew bootRun
```

### Frontend

Requires Node 20+. The Vite dev server proxies `/api` to `localhost:8080`.

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
```

### Tests

Backend tests use Testcontainers — a real PostgreSQL instance spins up automatically. No manual database setup needed.

```bash
cd backend
./gradlew test
```

---

## Idempotency

All registration endpoints are safe to call multiple times with the same natural key:

| Endpoint | Natural key |
|---|---|
| `POST /devices` | `externalDeviceId` |
| `POST /firmware-builds` | `version` |
| `POST /test-suites` | `name` |
| `POST /test-suites/{id}/cases` | `suiteId + name` |
| `POST /test-runs` | `deviceId + firmwareBuildId + suiteId + externalRunId` |

Duplicate calls return the existing record (HTTP 200, not 201). CI pipelines can register resources at the top of every run without coordination or deduplication logic.

---

## Observability

### Micrometer metrics exposed at `/actuator/prometheus`

| Metric | Tags | Description |
|---|---|---|
| `etfc_ingestion_total` | `entity`, `outcome` | Ingestion operations split by created vs existing |
| `etfc_transition_errors_total` | — | Invalid state transition attempts |
| `etfc_rate_limited_total` | — | Requests rejected by the rate limiter |
| `etfc_dashboard_query_duration_seconds` | — | P50 / P95 / P99 dashboard query latency |

The Grafana dashboard and Prometheus scrape config are provisioned automatically from `ops/` — no manual import needed.

---

## License

MIT
# embedded-testflow-console
