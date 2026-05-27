// Hand-maintained until `npm run gen:api` is run against a live backend.

export interface HealthResponse {
  status: string;
  build: string;
  time: string;
}

// ── Dashboard ──────────────────────────────────────────────────────────────

export interface SuiteFailureRate {
  suiteName: string;
  total: number;
  failed: number;
  failureRate: number;
}

export interface FirmwareFailureCount {
  version: string;
  failureCount: number;
}

export interface FailingDeviceSummary {
  deviceId: string;
  externalDeviceId: string;
  lastFailedAt: string | null;
  failureCount: number;
}

export interface SuiteDurationSummary {
  suiteName: string;
  avgDurationMs: number;
}

export interface ActivityEntry {
  runId: string;
  deviceExternalId: string;
  suiteName: string;
  status: string;
  startedAt: string | null;
}

export interface DashboardSummaryResponse {
  totalRuns: number;
  passedRuns: number;
  failedRuns: number;
  timedOutRuns: number;
  blockedRuns: number;
  needsReviewRuns: number;
  passRate: number;
  failureRateBySuite: SuiteFailureRate[];
  failureCountByFirmware: FirmwareFailureCount[];
  recentFailingDevices: FailingDeviceSummary[];
  slowestSuites: SuiteDurationSummary[];
  latestActivity: ActivityEntry[];
}

// ── Test runs ──────────────────────────────────────────────────────────────

export interface TestRunResponse {
  id: string;
  externalRunId: string;
  deviceId: string;
  deviceExternalId: string;
  firmwareVersion: string;
  suiteName: string;
  status: string;
  environment: string;
  correlationId: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ── Test run detail ────────────────────────────────────────────────────────

export interface TestResultResponse {
  id: string;
  runId: string;
  caseId: string;
  caseName: string;
  attemptNumber: number;
  status: string;
  durationMs: number | null;
  failureCategory: string | null;
  failureMessage: string | null;
  logReference: string | null;
  createdAt: string;
  updatedAt: string;
}

export type TestRunDetailResponse = TestRunResponse & {
  results: TestResultResponse[];
};
