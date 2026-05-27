# Idempotency Rules

## Why idempotency matters

Test runners retry on network failure. CI pipelines re-run jobs. Without idempotency, retried
submissions produce duplicate records, inflating failure counts and corrupting dashboards.

---

## Identity keys

| Resource | Identity key | Behaviour on duplicate |
|---|---|---|
| Device | `external_device_id` | Return existing record (HTTP 200) |
| FirmwareBuild | `version` | Return existing record (HTTP 200) |
| TestRun | `(device_id, firmware_build_id, suite_id, external_run_id)` | Return existing record (HTTP 200) |
| TestResult | `(run_id, case_id, attempt_number)` | Upsert: merge fields in place (HTTP 200) |

New resources are returned as HTTP 201. Existing resources are returned as HTTP 200.
The response body is identical either way — callers do not need to branch on status code.

---

## TestResult upsert semantics

`TestResult` is the one resource that supports a true upsert rather than a pure create-once
idempotency. If a result with the same `(run_id, case_id, attempt_number)` already exists,
all mutable fields (`status`, `duration_ms`, `failure_category`, `failure_message`, `log_reference`)
are overwritten with the new values.

**Rationale:** test runners occasionally emit partial payloads first and complete payloads later
(e.g., streaming mode). An update in place is safer than a conflict error that requires caller-side
recovery.

---

## Example: retried test run submission

```
POST /api/test-runs
{
  "externalRunId": "ci-run-00042",
  "deviceExternalId": "DEV-RACK-03",
  "firmwareVersion": "v2.8.4",
  "suiteName": "PowerCycleValidation"
}
→ 201 Created  { "id": "019xxx-...", "status": "QUEUED", ... }

# Network drops. CI retries the same payload:
POST /api/test-runs
{
  "externalRunId": "ci-run-00042",
  "deviceExternalId": "DEV-RACK-03",
  "firmwareVersion": "v2.8.4",
  "suiteName": "PowerCycleValidation"
}
→ 200 OK  { "id": "019xxx-...", "status": "QUEUED", ... }  ← same record, no duplicate
```

---

## Example: out-of-order result ingestion

```
# Attempt 2 arrives before attempt 1 (e.g., parallel test shards):
POST /api/test-runs/{runId}/results
{ "results": [{ "caseName": "UART_LOOPBACK", "attemptNumber": 2, "status": "PASSED", ... }] }
→ 200 OK  [{ "attemptNumber": 2, ... }]

POST /api/test-runs/{runId}/results
{ "results": [{ "caseName": "UART_LOOPBACK", "attemptNumber": 1, "status": "FAILED",
               "failureCategory": "FLAKY_TEST", ... }] }
→ 200 OK  [{ "attemptNumber": 1, "failureCategory": "FLAKY_TEST", ... }]

# Two independent records exist — one per attempt number.
```

---

## Implementation location

The idempotency check for each resource lives in `IngestionService`:
- `registerDevice` → `DeviceRepository.findByExternalDeviceId`
- `registerFirmwareBuild` → `FirmwareBuildRepository.findByVersion`
- `createTestRun` → `TestRunRepository.findByDeviceIdAndFirmwareBuildIdAndSuiteIdAndExternalRunId`
- `upsertResult` → `TestResultRepository.findByRunIdAndTestCaseIdAndAttemptNumber`

Database-level unique constraints enforce the same rules at the storage layer, ensuring
correctness even under concurrent requests from different application instances.
