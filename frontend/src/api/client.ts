import type {
  DashboardSummaryResponse,
  HealthResponse,
  PagedResponse,
  TestRunDetailResponse,
  TestRunResponse,
} from "./types";

const BASE = "/api";
const API_KEY = import.meta.env.VITE_API_KEY as string | undefined;

function authHeaders(): HeadersInit {
  return API_KEY ? { Authorization: `Bearer ${API_KEY}` } : {};
}

async function apiFetch<T>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { headers: authHeaders() });
  if (!res.ok) throw new Error(`HTTP ${res.status} ${res.statusText}`);
  return res.json() as Promise<T>;
}

export function exportFetch(path: string): Promise<Response> {
  return fetch(BASE + path, { headers: authHeaders() });
}

// ── Parameter shapes ───────────────────────────────────────────────────────

export interface DashboardParams {
  firmwareVersion?: string;
  environment?: string;
  from?: string;
  to?: string;
}

export interface TestRunsParams {
  page?: number;
  size?: number;
  firmwareVersion?: string;
  boardRevision?: string;
  suite?: string;
  status?: string;
  failureCategory?: string;
  environment?: string;
  from?: string;
  to?: string;
}

// ── Helpers ────────────────────────────────────────────────────────────────

function buildQs(params: object): string {
  const qs = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== "") qs.set(k, String(v));
  }
  const s = qs.toString();
  return s ? `?${s}` : "";
}

// ── API surface ────────────────────────────────────────────────────────────

export const api = {
  health: () => apiFetch<HealthResponse>("/health"),

  getDashboardSummary: (params: DashboardParams = {}) =>
    apiFetch<DashboardSummaryResponse>(
      `/dashboard/summary${buildQs(params)}`,
    ),

  getTestRuns: (params: TestRunsParams = {}) =>
    apiFetch<PagedResponse<TestRunResponse>>(
      `/test-runs${buildQs(params)}`,
    ),

  getTestRun: (id: string) =>
    apiFetch<TestRunDetailResponse>(`/test-runs/${id}`),
};
