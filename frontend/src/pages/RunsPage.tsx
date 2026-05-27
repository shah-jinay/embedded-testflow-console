import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api, exportFetch } from "../api/client";
import { StatusBadge } from "../components/StatusBadge";
import { fmtRelative } from "../lib/utils";

const PAGE_SIZE = 20;

const STATUSES = [
  "QUEUED",
  "RUNNING",
  "PASSED",
  "FAILED",
  "TIMED_OUT",
  "BLOCKED",
  "NEEDS_REVIEW",
];

// ── Helpers ────────────────────────────────────────────────────────────────

const INPUT =
  "h-8 rounded border border-slate-200 bg-white px-3 text-sm text-slate-700 " +
  "placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-slate-300 " +
  "focus:border-transparent transition";

// ── Component ──────────────────────────────────────────────────────────────

export function RunsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // URL is the source of truth for the active query filters
  const firmwareParam = searchParams.get("firmwareVersion") ?? "";
  const suiteParam = searchParams.get("suite") ?? "";
  const envParam = searchParams.get("environment") ?? "";
  const statusParam = searchParams.get("status") ?? "";
  const pageParam = Number(searchParams.get("page") ?? "0");

  // Local state for text inputs — responsive typing without waiting for debounce
  const [firmware, setFirmware] = useState(firmwareParam);
  const [suite, setSuite] = useState(suiteParam);
  const [env, setEnv] = useState(envParam);

  // Skip debounce effect on the very first render (inputs already match URL)
  const skipFirst = useRef(true);

  useEffect(() => {
    if (skipFirst.current) {
      skipFirst.current = false;
      return;
    }
    const t = setTimeout(() => {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (firmware) next.set("firmwareVersion", firmware);
          else next.delete("firmwareVersion");
          if (suite) next.set("suite", suite);
          else next.delete("suite");
          if (env) next.set("environment", env);
          else next.delete("environment");
          next.delete("page"); // reset pagination on filter change
          return next;
        },
        { replace: true },
      );
    }, 300);
    return () => clearTimeout(t);
  }, [firmware, suite, env, setSearchParams]);

  const handleStatusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (e.target.value) next.set("status", e.target.value);
        else next.delete("status");
        next.delete("page");
        return next;
      },
      { replace: true },
    );
  };

  const changePage = (delta: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set("page", String(Math.max(0, pageParam + delta)));
      return next;
    });
  };

  const clearFilters = () => {
    setFirmware("");
    setSuite("");
    setEnv("");
    setSearchParams({});
  };

  const hasFilters = firmwareParam || suiteParam || envParam || statusParam;

  const exportCsv = async () => {
    const qs = new URLSearchParams();
    if (firmwareParam) qs.set("firmwareVersion", firmwareParam);
    if (suiteParam) qs.set("suite", suiteParam);
    if (statusParam) qs.set("status", statusParam);
    if (envParam) qs.set("environment", envParam);
    const resp = await exportFetch(`/test-runs/export?${qs}`);
    const blob = await resp.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "test-runs.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  // ── Query (keyed by URL params — fires after debounce settles) ──────────
  const { data, isLoading, isFetching, isError } = useQuery({
    queryKey: [
      "test-runs",
      firmwareParam,
      suiteParam,
      envParam,
      statusParam,
      pageParam,
    ],
    queryFn: () =>
      api.getTestRuns({
        page: pageParam,
        size: PAGE_SIZE,
        firmwareVersion: firmwareParam || undefined,
        suite: suiteParam || undefined,
        status: statusParam || undefined,
        environment: envParam || undefined,
      }),
    placeholderData: (prev) => prev,
  });

  // ── Render ─────────────────────────────────────────────────────────────
  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Test Runs</h1>
        <div className="flex items-center gap-3">
          <button
            onClick={exportCsv}
            className="h-8 rounded border border-slate-200 bg-white px-3 text-sm text-slate-500 hover:text-slate-800 hover:bg-slate-50 transition-colors"
            title="Export matching runs as CSV"
          >
            ↓ Export CSV
          </button>
          <span className="text-sm text-slate-400 flex items-center gap-2">
          {data?.totalElements != null && (
            <>{data.totalElements.toLocaleString()} runs</>
          )}
          {isFetching && (
            <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-slate-300 border-t-slate-600" />
          )}
          </span>
        </div>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="text"
          placeholder="Firmware version"
          value={firmware}
          onChange={(e) => setFirmware(e.target.value)}
          className={INPUT + " w-40"}
        />
        <input
          type="text"
          placeholder="Suite name"
          value={suite}
          onChange={(e) => setSuite(e.target.value)}
          className={INPUT + " w-36"}
        />
        <select
          value={statusParam}
          onChange={handleStatusChange}
          className={INPUT + " w-36 cursor-pointer"}
        >
          <option value="">All statuses</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s.replace(/_/g, " ")}
            </option>
          ))}
        </select>
        <input
          type="text"
          placeholder="Environment"
          value={env}
          onChange={(e) => setEnv(e.target.value)}
          className={INPUT + " w-32"}
        />
        {hasFilters && (
          <button
            onClick={clearFilters}
            className="h-8 rounded border border-slate-200 bg-white px-3 text-sm text-slate-400 hover:text-slate-700 hover:bg-slate-50 transition-colors"
          >
            × Clear
          </button>
        )}
      </div>

      {isError && (
        <div className="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-700">
          Failed to load test runs.
        </div>
      )}

      {/* Table */}
      <div className="bg-white rounded-lg border border-slate-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 bg-slate-50">
                {[
                  "Run ID",
                  "Device",
                  "Firmware",
                  "Suite",
                  "Status",
                  "Env",
                  "Started",
                ].map((h) => (
                  <th
                    key={h}
                    className="text-left px-4 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide whitespace-nowrap"
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-50">
              {isLoading && (
                <tr>
                  <td
                    colSpan={7}
                    className="px-4 py-10 text-center text-slate-400 text-sm"
                  >
                    Loading…
                  </td>
                </tr>
              )}

              {!isLoading && data?.content.length === 0 && (
                <tr>
                  <td
                    colSpan={7}
                    className="px-4 py-10 text-center text-slate-400 text-sm"
                  >
                    {hasFilters ? "No runs match the current filters." : "No test runs found."}
                  </td>
                </tr>
              )}

              {data?.content.map((run) => (
                <tr
                  key={run.id}
                  onClick={() => navigate(`/runs/${run.id}`)}
                  className="hover:bg-slate-50 cursor-pointer transition-colors"
                >
                  <td className="px-4 py-3 font-mono text-xs text-slate-600 whitespace-nowrap">
                    {run.externalRunId}
                  </td>
                  <td className="px-4 py-3 text-slate-700 whitespace-nowrap">
                    {run.deviceExternalId}
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-600 whitespace-nowrap">
                    {run.firmwareVersion}
                  </td>
                  <td className="px-4 py-3 text-slate-700 max-w-[160px] truncate">
                    {run.suiteName}
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={run.status} />
                  </td>
                  <td className="px-4 py-3 text-slate-400 text-xs">
                    {run.environment}
                  </td>
                  <td className="px-4 py-3 text-slate-400 text-xs whitespace-nowrap">
                    {fmtRelative(run.startedAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="border-t border-slate-100 px-4 py-3 flex items-center justify-between">
            <button
              onClick={() => changePage(-1)}
              disabled={pageParam === 0}
              className="text-sm px-3 py-1.5 rounded border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              ← Previous
            </button>
            <span className="text-xs text-slate-400">
              Page {pageParam + 1} of {data.totalPages}
            </span>
            <button
              onClick={() => changePage(1)}
              disabled={pageParam >= data.totalPages - 1}
              className="text-sm px-3 py-1.5 rounded border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Next →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
