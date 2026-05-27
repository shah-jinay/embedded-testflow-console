# Build Progress

## Phase 0 — Scaffold ✅

**Summary**

Monorepo initialized at `embedded-testflow-console/`. Docker Compose brings up all six services from a fresh clone with no manual steps beyond `docker compose up`.

**What was built**

- `docker-compose.yml` orchestrates: `postgres` (16-alpine), `redis` (7-alpine), `backend` (Spring Boot 3.3.5 / Java 21), `frontend` (React 18 + nginx), `prometheus`, `grafana`.
- Startup ordering enforced via `healthcheck` + `depends_on` conditions: backend waits for healthy postgres + redis; frontend waits for healthy backend.
- Backend (`GET /api/health`) returns `{status, build, time}`. Build SHA injected via `GIT_SHA` env var (defaults to `"dev"`).
- Frontend: single-page React app (Vite + TypeScript + Tailwind) served by nginx. nginx proxies `/api/*` → `backend:8080`. Page calls `/api/health` via TanStack Query and renders the response with loading/error states. Quick-links to Swagger UI, Prometheus, and Grafana.
- Prometheus scrapes `/actuator/prometheus` on the backend every 15s.
- Grafana auto-provisions the Prometheus datasource; dashboard directory mounted for Phase 7.
- Logback configured for human-readable output in `dev` profile and JSON (logstash encoder) in `docker`/`prod` profiles — MDC `correlationId` field included from Phase 2 onward.

**Ports**

| Service    | Host port |
|------------|-----------|
| Frontend   | 5173      |
| Backend    | 8080      |
| Prometheus | 9090      |
| Grafana    | 3000      |
| Postgres   | 5432      |
| Redis      | 6379      |

**DoD check**

- `docker compose up` brings all services to healthy state.
- `curl http://localhost:8080/api/health` returns `{"status":"UP","build":"dev","time":"..."}`.
- Frontend at `http://localhost:5173` displays the health panel with green status indicator.

---

## Phase 1 — Data model & migrations ✅

**Summary**

Flyway migration creates all 6 tables with constraints, FKs, and indexes. JPA entities, enums, and repositories are in place. Repository-level integration tests run against a real Postgres 16 container via Testcontainers and cover: save, find-by-natural-key, unique-constraint enforcement, retry semantics (attempt_number), and failure-category lookup.

**What was built**

- `V1__init.sql` — tables: `firmware_build`, `device`, `test_suite`, `test_case`, `test_run`, `test_result`. All unique constraints and FKs named explicitly. All five indexes from §6.
- Enums: `DeviceStatus` (ACTIVE/INACTIVE/OFFLINE/DECOMMISSIONED), `TestRunStatus` (QUEUED/RUNNING/PASSED/FAILED/TIMED_OUT/BLOCKED/NEEDS_REVIEW), `FailureCategory` (8 values including UNCLASSIFIED).
- JPA entities in feature packages (`device`, `firmware`, `testsuite`, `testrun`), stored as `VARCHAR` via `@Enumerated(EnumType.STRING)`. `metadata` JSONB on `Device` via `@JdbcTypeCode(SqlTypes.JSON)`. Timestamps via `@CreationTimestamp`/`@UpdateTimestamp`.
- Spring Data repositories with natural-key lookups for idempotency checks (Phase 2).
- Lombok used for `@Getter`, field-level `@Setter`, `@NoArgsConstructor` — compile-time only.
- `AbstractRepositoryTest` base: `@SpringBootTest` + `@Testcontainers` + `@Rollback`; single static Postgres container shared across all test classes.
- Test coverage: `DeviceRepositoryTest` (4 tests), `FirmwareBuildRepositoryTest` (3), `TestRunRepositoryTest` (4), `TestResultRepositoryTest` (5) — 16 tests total.

**DoD check**

- All migrations apply cleanly against Testcontainers Postgres 16.
- All 16 repository tests pass.
- No controllers yet — domain model only.

---

## Phase 2 — Ingestion APIs ✅

**Summary**

Five idempotent ingestion endpoints with a translator layer, state-machine transition guard, correlation ID filter, and unified error envelope. 14 integration tests cover all four DoD scenarios plus edge cases.

**What was built**

- `CorrelationIdFilter` — reads `X-Correlation-ID` header, generates UUID if absent, writes to MDC, echoes on response. Highest-precedence filter.
- `GlobalExceptionHandler` — `@RestControllerAdvice` mapping validation errors (400), entity not found (404), invalid transitions (422), and unexpected exceptions (500) to the spec's `{error:{code,message,correlationId,details}}` envelope.
- `FailureCategoryTranslator` — single authoritative point: unknown strings → `UNCLASSIFIED`, blank → `null`. Does not throw.
- `StatusTranslator` — rejects unknown statuses with `IllegalArgumentException`.
- `StatusTransitionValidator` — state machine: QUEUED→{RUNNING,BLOCKED}; RUNNING→{PASSED,FAILED,TIMED_OUT,BLOCKED,NEEDS_REVIEW}; NEEDS_REVIEW→{PASSED,FAILED}; BLOCKED→{QUEUED,FAILED}. PASSED/FAILED/TIMED_OUT are terminal.
- `IngestionService` — all five operations are `@Transactional`. Idempotency via `findByNaturalKey().orElseGet(create)`. `TestResult` upserts in place on duplicate `(runId, caseId, attemptNumber)`. Correlation ID propagated from MDC to `TestRun.correlationId`.
- `RegisterResult<T>` — thin wrapper so controllers choose 201 vs 200 without service-layer leakage.
- Three controllers: `DeviceIngestionController`, `FirmwareBuildIngestionController`, `TestRunIngestionController`.
- `docs/idempotency.md` documents the identity key rules with worked examples.
- 14 integration tests in `IngestionIntegrationTest` covering: new/duplicate device, firmware, run; result upsert; out-of-order attempts; invalid transition → 422; unknown category → UNCLASSIFIED; null category → null; correlation ID echoed; correlation ID auto-generated; missing field → 400 envelope; unknown device reference → 404.

**DoD check**

- (a) Successful ingest: device → 201, firmware → 201, run → 201, status patch → 200, results → 200.
- (b) Duplicate device/firmware/run → 200 with same `id`; `count()` confirms no duplicate row.
- (c) Out-of-order attempt numbers stored as independent records; upsert of same attempt merges fields in place.
- (d) `"TOTALLY_UNKNOWN_CATEGORY_XYZ"` → `"UNCLASSIFIED"` with HTTP 200.

---

## Phase 3 — Query APIs ✅

**Summary**

All five query endpoints implemented with pagination, full filter set, and DB indexes. Dashboard summary runs six queries (JPQL for counts/aggregates, native SQL for avg-duration and recent-device lookups). springdoc publishes OpenAPI at `/v3/api-docs`. 16 integration tests cover every filter and all summary sections.

**What was built**

- `V2__additional_indexes.sql` — indexes on `test_run.environment`, `test_run.status`, `test_run.created_at`, `device.board_revision`, `device.environment`, `device.status`, `firmware_build.is_release_candidate`.
- `PagedResponse<T>` — thin wrapper over Spring `Page<T>` for consistent `{content, page, size, totalElements, totalPages}`.
- `TestRunRepository` — added `JpaSpecificationExecutor<TestRun>` + four `@Query` methods for dashboard aggregations.
- `TestRunSpecification` — JPA Criteria Specification covering all nine filter fields; device/firmware/suite joins created lazily and not duplicated; `failureCategory` uses an EXISTS subquery; `query.distinct(true)` prevents double-counting.
- `TestRunQueryController` (`GET /api/test-runs`, `GET /api/test-runs/{id}`) — pageable, size capped at 200, results fetched separately to avoid N+1.
- `TestRunDetailResponse` — embeds the run's `List<TestResultResponse>`.
- `DashboardQueryService` — JPQL for status counts, suite failure counts, firmware failure counts, and latest activity; native SQL (via `EntityManager`) for recent failing devices (GROUP BY + MAX) and slowest suites (EXTRACT EPOCH AVG).
- `DashboardController` (`GET /api/dashboard/summary?firmwareVersion=&from=&to=&environment=`).
- `DeviceQueryController` (`GET /api/devices`, `GET /api/devices/{id}`) — detail includes current firmware version, total/failed run counts, 10 most-recent runs.
- `FirmwareQueryController` (`GET /api/firmware-builds/{version}/validation-summary`) — breakdown by device and by suite.
- springdoc auto-generates OpenAPI spec at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

**Tests**
- `TestRunQueryTest` — 13 tests: no-filter, filter by deviceId, firmwareVersion, boardRevision, suite, status, failureCategory (subquery), environment, date range, pagination, size cap, run detail, unknown ID → 404.
- `DashboardQueryTest` — 4 tests: empty DB → zeros; status counts; firmwareVersion filter; failureCountByFirmware ordering; latestActivity count.

**DoD check**
- All list endpoints paginated, filters honored, swagger spec published at `/v3/api-docs`.

---

## Phase 4 — Redis: caching & rate control ✅

**Summary**

Dashboard summary responses cached in Redis with a 15-second TTL. Token-bucket rate limiter guards all ingestion endpoints (POST/PATCH) using an atomic Lua script. Both Redis integrations are fail-open: if Redis is unavailable, requests proceed normally with a log warning. Custom Micrometer counters `etfc_cache_hits_total` and `etfc_rate_limited_total` are registered and scraped by Prometheus.

**What was built**

- `spring-boot-starter-data-redis` (Lettuce) + `spring-boot-starter-cache` added to `build.gradle.kts`. `@EnableCaching` on `EtfcApplication`.
- `application.yml` — `spring.data.redis.host/port` (defaults `localhost:6379`; Docker overrides via `SPRING_DATA_REDIS_HOST/PORT` env vars already present in `docker-compose.yml`). `etfc.rate-limit.enabled=true`, `limit=100`, `window-seconds=60`.
- `CacheConfig` — `RedisCacheManager` bean with 15-second TTL for the `dashboard-summary` cache. Keys serialized as `StringRedisSerializer`; values as `Jackson2JsonRedisSerializer<DashboardSummaryResponse>` (ObjectMapper with `JavaTimeModule`, ISO-8601 timestamps).
- `DashboardCacheService` — thin caching façade over `DashboardQueryService`. Manual get/put against the `dashboard-summary` cache. Fail-open on any Redis exception. Increments `etfc_cache_hits_total` counter on hit. `DashboardController` updated to call this façade.
- `RateLimitFilter` — `@ConditionalOnProperty(etfc.rate-limit.enabled)`, `@Order(HIGHEST_PRECEDENCE + 1)`. Watches POST + PATCH to `/api/devices`, `/api/firmware-builds`, `/api/test-runs`. Rate-limit key: `rl:{remoteAddr}:{group}`. Lua script atomically increments counter, sets TTL on first hit, returns remaining TTL when limit exceeded. Returns 429 with `Retry-After` header and the standard error envelope. Increments `etfc_rate_limited_total` counter. Fail-open on Redis errors.

**Tests**

- `CacheTest` (2 tests): Redis + Postgres Testcontainers; `@SpyBean DashboardQueryService` verifies service called exactly once after two identical dashboard requests; separate filter combos produce separate cache entries.
- `RateLimitTest` (2 tests): Redis + Postgres Testcontainers; `limit=3` via `@SpringBootTest(properties)`; 4th POST to `/api/devices` returns 429 with `Retry-After` and `RATE_LIMITED` code; separate endpoint group (`/api/firmware-builds`) uses independent bucket and is not affected.

**DoD check**

- Repeated dashboard calls with same params hit cache (service called once instead of N times).
- Rate-limit counter `etfc_rate_limited_total` and cache counter `etfc_cache_hits_total` visible at `/actuator/prometheus`.
- 4th POST to a watched endpoint with `limit=3` returns `HTTP 429` + `Retry-After` header.
- All existing tests pass (fail-open when Redis not available in Testcontainers-only environments).

---

## Phase 5 — Frontend shell + dashboard ✅

**Summary**

Full React SPA replacing the Phase 0 placeholder. Typed API client over the existing REST endpoints. Shell layout with top navigation. Dashboard page with stats, inline bar charts, activity feed, and device/suite breakdowns. Paginated Test Runs list. `npm run gen:api` script wired up for future OpenAPI type regeneration.

**What was built**

- `src/api/types.ts` — hand-maintained TypeScript interfaces for all backend responses (`DashboardSummaryResponse` and all nested records, `TestRunResponse`, `PagedResponse<T>`, `HealthResponse`).
- `src/api/client.ts` — typed `apiFetch<T>` wrapper; `api.health()`, `api.getDashboardSummary(params?)`, `api.getTestRuns(params?)`. Query-string builder for filter params.
- `src/lib/utils.ts` — `fmtPct`, `fmtMs`, `fmtRelative`, `fmtDateTime` formatters.
- `src/components/StatCard.tsx` — stat tile with label, large value, accent colour.
- `src/components/StatusBadge.tsx` — colour-coded pill for run statuses.
- `src/components/Shell.tsx` — sticky top bar with `NavLink` navigation (Dashboard / Test Runs), live health dot (30 s poll), API Docs and Grafana links. Uses React Router `<Outlet />`.
- `src/pages/DashboardPage.tsx` — 6 stat cards; suite failure rate inline bars; firmware failure table; latest activity list with status badge + relative time; slowest suites; recently failing devices. 30 s auto-refresh. Skeleton loader and error state.
- `src/pages/RunsPage.tsx` — paginated table (20 rows/page) showing all test runs with status badge, relative time. Previous/Next pagination with `placeholderData` for smooth transitions.
- `src/App.tsx` — `QueryClientProvider` + `BrowserRouter` + `Routes`; `Shell` wraps both pages; `*` redirect to `/`.
- `package.json` — `"gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/api/schema.d.ts"` script; `openapi-typescript ^7.4.0` devDependency.

**DoD check**

- `http://localhost:5173` shows Shell nav + Dashboard page (stats, panels, activity).
- Navigating to `/runs` shows paginated test run table.
- Health dot in the top bar updates every 30 s.
- TypeScript build (`tsc && vite build`) passes with no type errors.

---

## Phase 6 — Filters, list, drill-down ✅

**Summary**

Runs page gains a live filter bar with URL-synced state; every filter combo is bookmarkable and sharable. Clicking any row navigates to the run detail page which shows metadata, summary stat cards, and a full results table per test case.

**What was built**

- `src/api/types.ts` — added `TestResultResponse` (caseName, attemptNumber, status, durationMs, failureCategory, failureMessage, logReference) and `TestRunDetailResponse = TestRunResponse & { results }`.
- `src/api/client.ts` — `buildQs` helper; `TestRunsParams` extended with all 10 filter fields; `api.getTestRun(id)` added.
- `src/pages/RunsPage.tsx` — rewritten with filter bar (firmware version, suite name, status select, environment); text inputs debounced 300 ms to URL via `useSearchParams`; status select updates URL immediately; filters reset pagination to page 0; `hasFilters` flag drives "× Clear" button visibility; empty-state message adapts to whether filters are active; rows are `cursor-pointer` and navigate to `/runs/{id}` on click. Query keyed by URL params so TanStack Query refetches only after debounce settles.
- `src/pages/RunDetailPage.tsx` — breadcrumb "← Test Runs / {externalRunId}"; run header card with suite, device, firmware, status badge, and a `<dl>` grid for environment / duration / started / completed / correlationId; 3 stat cards (total, passed, failed results); results table with test case name, attempt number, status badge, duration, failure category pill, truncated failure message; skeleton loader; 404 error state.
- `src/App.tsx` — `/runs/:id` route added inside Shell.

**DoD check**

- Typing a firmware version in the filter bar updates the URL and re-fetches after 300 ms.
- Selecting a status from the dropdown immediately filters the list.
- Clicking "× Clear" resets all filters and URL.
- Clicking a row navigates to `/runs/{uuid}` showing the full run detail.
- Browser back returns to the filtered list.

---

## Phase 7 — Observability ✅

**Summary**

`IngestionService` and `DashboardQueryService` instrumented with Micrometer counters and a timer. Pre-built Grafana dashboard auto-provisioned on `docker compose up`.

**What was built**

- `IngestionService` — added `MeterRegistry` constructor injection + `@PostConstruct initMetrics()` registering nine Micrometer instruments:
  - `etfc_ingestion_total{entity, outcome}` — counters for device/firmware/run/result operations; outcome is `created`, `existing`, `updated`.
  - `etfc_transition_errors_total` — incremented inside the `catch` block whenever `StatusTransitionValidator.validate()` throws `IllegalStateException`.
- `DashboardQueryService` — added `MeterRegistry` constructor injection + `@PostConstruct` registering `etfc_dashboard_query_duration_seconds` (Micrometer `Timer`). `getSummary()` delegates to private `doGetSummary()` wrapped in `queryTimer.record(...)`.
- `ops/grafana/dashboards/etfc.json` — Grafana 10.x dashboard (uid `etfc-main`) auto-loaded via the existing provisioning config. Four row sections:
  - **HTTP** — request rate by uri/method/status; P95 latency histogram.
  - **Ingestion** — `etfc_ingestion_total` by entity/outcome; `etfc_transition_errors_total`.
  - **Cache & Rate Limiting** — `etfc_cache_hits_total`; `etfc_rate_limited_total`; `etfc_dashboard_query_duration_seconds` P50/P95/P99.
  - **JVM Health** — heap used vs committed; HikariCP active/pending/idle connections; GC pause rate.

**DoD check**

- All nine custom metrics visible at `/actuator/prometheus` after first ingestion + dashboard request.
- `docker compose up` → Grafana at `http://localhost:3000` → "ETFC — Embedded TestFlow Console" dashboard loads with all panels populated after seeding data.
- Existing tests unaffected (Micrometer registers a `SimpleMeterRegistry` in the test context automatically).

---

## Phase 8 — Seed data & demo script ✅

**Summary**

Two new ingestion endpoints register test suites and test cases. A single idempotent Python seed script populates the stack with 6 suites, 50 test cases, 10 devices, 5 firmware builds, and 203 test runs (with full per-case results) spread across the past 28 days.

**What was built**

- New DTOs: `CreateTestSuiteRequest`, `TestSuiteResponse`, `CreateTestCaseRequest`, `TestCaseResponse`.
- `IngestionService.registerSuite()` / `registerCase()` — find-or-create with idempotency by natural key (suite name; suite_id + case name). Instrumented with same `etfc_ingestion_total` counter pattern.
- `TestSuiteIngestionController`:
  - `POST /api/test-suites` → 201 on create, 200 on existing.
  - `POST /api/test-suites/{suiteId}/cases` → 201 on create, 200 on existing.
- `scripts/seed.py` — stdlib-only Python 3 script (no pip deps):
  - 6 suites with owner, severity, targetComponent, description.
  - 50 test cases distributed across suites (8–9 per suite), each with expectedBehavior, timeoutMs, criticality.
  - 10 devices across 5 MCU families (STM32H7, STM32F4, NRF52840, ESP32S3, RP2040), 3 board revisions, 3 environments.
  - 5 firmware builds: v1.0.0, v1.1.0, v1.2.0 (stable) + v1.3.0-rc1, v2.0.0-rc1 (RC).
  - 203 test runs with status distribution: 60 % PASSED · 20 % FAILED · 10 % TIMED_OUT · 10 % NEEDS_REVIEW · 5 in-flight (RUNNING).
  - Timestamps spread across the past 28 days for realistic dashboard charts.
  - Deterministic RNG (seed=42) so runs are identical across invocations.
  - Fail-fast HTTP error reporting; progress counter every 20 runs.
- `scripts/seed.sh` — thin wrapper: `exec python3 scripts/seed.py "$@"`.

**DoD check**

- `./scripts/seed.sh` (or `python3 scripts/seed.py`) exits 0 against a running stack.
- Running it a second time produces no duplicate rows (all operations are idempotent).
- Frontend dashboard at `http://localhost:5173` shows populated stats, charts, activity feed, and 200+ rows in the Test Runs list.

---

## Phase 9 — Auth, export, SSE ✅

**Summary**

API-key auth guards all ingestion and query endpoints (fail-open when unconfigured, so dev mode and tests need no changes). CSV/JSON export streams all matching runs without a row cap. Server-Sent Events push `RUN_CREATED` and `RUN_STATUS_CHANGED` to all connected browser tabs, which auto-invalidate their TanStack Query caches.

**What was built**

**Auth**
- `ApiKeyAuthFilter` — `OncePerRequestFilter`, `@ConditionalOnProperty(name="etfc.security.api-key")`, `@Order(HIGHEST_PRECEDENCE + 2)`. Reads `Authorization: Bearer <key>`, returns 401 error envelope on mismatch. Permits `/api/health`, `/api/events/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/actuator/**` unconditionally.
- `application.yml` — `etfc.security.api-key: ${ETFC_API_KEY:}` (empty default = filter not registered → no auth in dev/test).
- `docker-compose.yml` — `ETFC_API_KEY: ${ETFC_API_KEY:-demo-key}` on the backend service.
- `frontend/.env.development` — `VITE_API_KEY=demo-key`. `apiFetch` in `client.ts` sends `Authorization: Bearer ${VITE_API_KEY}` on every request when the env var is set. `exportFetch` helper exposes the same headers for the blob-download flow.

**Export**
- `GET /api/test-runs/export` — accepts all existing filter params + `format=csv|json` (default csv). Streams results in chunks of 500 using `StreamingResponseBody` + paginated JPA queries; no row cap. CSV has nine quoted columns; JSON emits a flat array. `Content-Disposition: attachment; filename=test-runs.csv`.
- Frontend "↓ Export CSV" button in the Test Runs header: triggers `exportFetch`, converts the response to a blob URL, clicks a synthetic `<a>` to download, then revokes the URL.

**SSE**
- `RunEvent` record — `{type, runId, externalRunId, deviceExternalId, firmwareVersion, suiteName, status, environment, timestamp}`.
- `SseService` — maintains a `CopyOnWriteArrayList<SseEmitter>` (5-minute timeout). `subscribe()` creates and registers an emitter, sends an initial `connected` event. `broadcast(RunEvent)` serializes to JSON, fans out to all live emitters, removes dead ones silently.
- `SseController` — `GET /api/events/runs` returns `text/event-stream` `SseEmitter`.
- `IngestionService` — injects `SseService`; broadcasts `RUN_CREATED` after a new run is saved; broadcasts `RUN_STATUS_CHANGED` after a status transition. Broadcasting is synchronous and exception-safe.
- `nginx.conf` — `/api/events/` location with `proxy_buffering off`, `proxy_cache off`, `Connection: ""`, `proxy_read_timeout 310s` (longer than emitter timeout). Uses longest-prefix matching, so it wins over the general `/api/` block.
- `Shell.tsx` — `useEffect` opens `EventSource("/api/events/runs")` on mount; `RUN_CREATED` / `RUN_STATUS_CHANGED` events call `queryClient.invalidateQueries` for `test-runs` and `dashboard`. A pulsing emerald "Live" badge appears in the top bar while the SSE connection is open.

**DoD check**

- `docker compose up` — `curl -H "Authorization: Bearer wrong-key" http://localhost:8080/api/test-runs` → 401.
- `curl -H "Authorization: Bearer demo-key" http://localhost:8080/api/test-runs/export` → CSV download.
- Open two browser tabs at `http://localhost:5173/runs`; POST a new test run via the API; both tabs refresh within a second.
- Existing tests pass (auth filter not loaded because `etfc.security.api-key` is empty in the test `application.yml` default).
