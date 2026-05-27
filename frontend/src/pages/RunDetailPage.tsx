import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { StatCard } from "../components/StatCard";
import { StatusBadge } from "../components/StatusBadge";
import { fmtDateTime, fmtMs } from "../lib/utils";
import type { TestResultResponse } from "../api/types";

export function RunDetailPage() {
  const { id } = useParams<{ id: string }>();

  const { data, isLoading, isError } = useQuery({
    queryKey: ["test-run", id],
    queryFn: () => api.getTestRun(id!),
    enabled: !!id,
  });

  if (isLoading) return <Skeleton />;

  if (isError || !data) {
    return (
      <div className="space-y-4">
        <Breadcrumb runId={id} />
        <div className="rounded-lg bg-red-50 border border-red-200 p-6 text-sm text-red-700">
          Run not found or the backend is unavailable.
        </div>
      </div>
    );
  }

  const durationMs =
    data.startedAt && data.completedAt
      ? new Date(data.completedAt).getTime() -
        new Date(data.startedAt).getTime()
      : null;

  const passed = data.results.filter((r) => r.status === "PASSED").length;
  const failed = data.results.filter((r) => r.status === "FAILED").length;
  const total = data.results.length;

  return (
    <div className="space-y-6">
      <Breadcrumb runId={data.externalRunId} />

      {/* Run header */}
      <div className="bg-white rounded-lg border border-slate-200 p-6">
        <div className="flex items-start justify-between gap-4 mb-5">
          <div>
            <h1 className="text-lg font-semibold text-slate-900">
              {data.suiteName}
            </h1>
            <p className="text-sm text-slate-500 mt-0.5">
              {data.deviceExternalId} &middot;{" "}
              <code className="font-mono text-xs">{data.firmwareVersion}</code>
            </p>
          </div>
          <StatusBadge status={data.status} />
        </div>

        <dl className="grid grid-cols-2 sm:grid-cols-4 gap-x-6 gap-y-4 text-sm">
          <Field label="Environment" value={data.environment} />
          <Field
            label="Duration"
            value={durationMs != null ? fmtMs(durationMs) : "—"}
          />
          <Field label="Started" value={fmtDateTime(data.startedAt)} />
          <Field label="Completed" value={fmtDateTime(data.completedAt)} />
          {data.correlationId && (
            <Field label="Correlation ID" value={data.correlationId} mono />
          )}
        </dl>
      </div>

      {/* Summary stats */}
      {total > 0 && (
        <div className="grid grid-cols-3 gap-3">
          <StatCard label="Total Results" value={total} />
          <StatCard label="Passed" value={passed} accent="green" />
          <StatCard label="Failed" value={failed} accent="red" />
        </div>
      )}

      {/* Results table */}
      <div>
        <h2 className="text-sm font-semibold text-slate-700 mb-3">
          Test Results
          <span className="ml-2 text-slate-400 font-normal">({total})</span>
        </h2>

        {total === 0 ? (
          <p className="text-sm text-slate-400">No results recorded for this run.</p>
        ) : (
          <div className="bg-white rounded-lg border border-slate-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-100 bg-slate-50">
                    {[
                      "Test Case",
                      "Attempt",
                      "Status",
                      "Duration",
                      "Failure Category",
                      "Message",
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
                  {data.results.map((r) => (
                    <ResultRow key={r.id} result={r} />
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── Sub-components ─────────────────────────────────────────────────────────

function ResultRow({ result: r }: { result: TestResultResponse }) {
  return (
    <tr className="hover:bg-slate-50 transition-colors">
      <td className="px-4 py-3 text-slate-700 font-medium">{r.caseName}</td>
      <td className="px-4 py-3 text-slate-400 text-center tabular-nums">
        {r.attemptNumber}
      </td>
      <td className="px-4 py-3">
        <StatusBadge status={r.status} />
      </td>
      <td className="px-4 py-3 text-slate-500 tabular-nums whitespace-nowrap">
        {r.durationMs != null ? fmtMs(r.durationMs) : "—"}
      </td>
      <td className="px-4 py-3">
        {r.failureCategory ? (
          <span className="inline-block rounded bg-slate-100 px-1.5 py-0.5 text-xs font-mono text-slate-600">
            {r.failureCategory.replace(/_/g, " ")}
          </span>
        ) : (
          <span className="text-slate-300">—</span>
        )}
      </td>
      <td
        className="px-4 py-3 text-slate-500 text-xs max-w-xs truncate"
        title={r.failureMessage ?? undefined}
      >
        {r.failureMessage ?? <span className="text-slate-300">—</span>}
      </td>
    </tr>
  );
}

function Breadcrumb({ runId }: { runId: string | undefined }) {
  return (
    <div className="flex items-center gap-2 text-sm">
      <Link to="/runs" className="text-slate-400 hover:text-slate-600 transition-colors">
        ← Test Runs
      </Link>
      {runId && (
        <>
          <span className="text-slate-300">/</span>
          <code className="font-mono text-slate-600 text-xs">{runId}</code>
        </>
      )}
    </div>
  );
}

function Field({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div>
      <dt className="text-xs text-slate-400 uppercase tracking-wide font-medium">
        {label}
      </dt>
      <dd
        className={`mt-0.5 text-slate-700 truncate ${mono ? "font-mono text-xs" : "text-sm"}`}
      >
        {value || "—"}
      </dd>
    </div>
  );
}

function Skeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-5 w-48 bg-slate-200 rounded" />
      <div className="h-40 bg-slate-200 rounded-lg" />
      <div className="grid grid-cols-3 gap-3">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-20 bg-slate-200 rounded-lg" />
        ))}
      </div>
      <div className="h-64 bg-slate-200 rounded-lg" />
    </div>
  );
}
