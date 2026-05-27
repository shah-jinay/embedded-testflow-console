import type { ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { StatCard } from "../components/StatCard";
import { StatusBadge } from "../components/StatusBadge";
import { fmtMs, fmtPct, fmtRelative } from "../lib/utils";
import type {
  FirmwareFailureCount,
  FailingDeviceSummary,
  SuiteFailureRate,
  SuiteDurationSummary,
  ActivityEntry,
} from "../api/types";

export function DashboardPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["dashboard", "summary"],
    queryFn: () => api.getDashboardSummary(),
    refetchInterval: 30_000,
  });

  if (isLoading) return <Skeleton />;
  if (isError || !data)
    return (
      <div className="rounded-lg bg-red-50 border border-red-200 p-6 text-sm text-red-700">
        Failed to load dashboard data. Is the backend running?
      </div>
    );

  const passRateGood = data.passRate >= 0.8;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">Dashboard</h1>
        <span className="text-xs text-slate-400">Refreshes every 30 s</span>
      </div>

      {/* ── Stats row ──────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-6 gap-3">
        <StatCard label="Total Runs" value={data.totalRuns.toLocaleString()} />
        <StatCard
          label="Passed"
          value={data.passedRuns.toLocaleString()}
          accent="green"
        />
        <StatCard
          label="Failed"
          value={data.failedRuns.toLocaleString()}
          accent="red"
        />
        <StatCard
          label="Timed Out"
          value={data.timedOutRuns.toLocaleString()}
          accent="orange"
        />
        <StatCard
          label="Blocked"
          value={data.blockedRuns.toLocaleString()}
          accent="gray"
        />
        <StatCard
          label="Pass Rate"
          value={fmtPct(data.passRate)}
          accent={passRateGood ? "green" : "red"}
          sub={`${data.needsReviewRuns} needs review`}
        />
      </div>

      {/* ── Middle row ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <SuiteFailurePanel suites={data.failureRateBySuite} />
        <FirmwareFailurePanel firmware={data.failureCountByFirmware} />
      </div>

      {/* ── Bottom row ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <ActivityPanel activity={data.latestActivity} />
        </div>
        <div className="space-y-6">
          <SlowestSuitesPanel suites={data.slowestSuites} />
          <RecentFailingDevicesPanel devices={data.recentFailingDevices} />
        </div>
      </div>
    </div>
  );
}

// ── Sub-panels ─────────────────────────────────────────────────────────────

function SuiteFailurePanel({ suites }: { suites: SuiteFailureRate[] }) {
  return (
    <Panel title="Failure Rate by Suite">
      {suites.length === 0 ? (
        <Empty />
      ) : (
        <div className="space-y-3">
          {suites.map((s) => (
            <div key={s.suiteName} className="flex items-center gap-3 text-sm">
              <span
                className="w-28 truncate text-slate-600 text-xs"
                title={s.suiteName}
              >
                {s.suiteName}
              </span>
              <div className="flex-1 h-2.5 bg-slate-100 rounded-full overflow-hidden">
                <div
                  className={`h-2.5 rounded-full transition-all ${
                    s.failureRate > 0.5 ? "bg-red-500" : "bg-red-300"
                  }`}
                  style={{ width: `${Math.max(2, Math.round(s.failureRate * 100))}%` }}
                />
              </div>
              <span className="w-9 text-right text-xs text-slate-500 tabular-nums">
                {fmtPct(s.failureRate)}
              </span>
              <span className="w-16 text-right text-xs text-slate-400 tabular-nums">
                {s.failed}/{s.total}
              </span>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

function FirmwareFailurePanel({ firmware }: { firmware: FirmwareFailureCount[] }) {
  return (
    <Panel title="Failures by Firmware Version">
      {firmware.length === 0 ? (
        <Empty />
      ) : (
        <div className="divide-y divide-slate-50">
          {firmware.map((f) => (
            <div
              key={f.version}
              className="flex items-center justify-between py-2 text-sm"
            >
              <code className="font-mono text-slate-700 text-xs">{f.version}</code>
              <span className="font-semibold text-red-600 tabular-nums">
                {f.failureCount}{" "}
                <span className="font-normal text-slate-400">failures</span>
              </span>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

function ActivityPanel({ activity }: { activity: ActivityEntry[] }) {
  return (
    <Panel title="Latest Activity">
      {activity.length === 0 ? (
        <Empty />
      ) : (
        <div className="divide-y divide-slate-50">
          {activity.map((a) => (
            <div key={a.runId} className="flex items-center gap-3 py-2.5 text-sm">
              <StatusBadge status={a.status} />
              <code className="font-mono text-xs text-slate-600 truncate max-w-[120px]">
                {a.deviceExternalId}
              </code>
              <span className="text-slate-500 truncate">{a.suiteName}</span>
              <span className="ml-auto text-slate-400 text-xs whitespace-nowrap">
                {fmtRelative(a.startedAt)}
              </span>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

function SlowestSuitesPanel({ suites }: { suites: SuiteDurationSummary[] }) {
  return (
    <Panel title="Slowest Suites">
      {suites.length === 0 ? (
        <Empty />
      ) : (
        <div className="divide-y divide-slate-50">
          {suites.map((s) => (
            <div key={s.suiteName} className="flex items-center justify-between py-1.5 text-sm">
              <span className="text-slate-600 text-xs truncate">{s.suiteName}</span>
              <span className="text-slate-500 text-xs tabular-nums ml-2">
                {fmtMs(s.avgDurationMs)}
              </span>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

function RecentFailingDevicesPanel({ devices }: { devices: FailingDeviceSummary[] }) {
  return (
    <Panel title="Recently Failing Devices">
      {devices.length === 0 ? (
        <Empty />
      ) : (
        <div className="divide-y divide-slate-50">
          {devices.map((d) => (
            <div key={d.deviceId} className="py-1.5 text-xs">
              <div className="flex items-center justify-between">
                <code className="font-mono text-slate-700 truncate">
                  {d.externalDeviceId}
                </code>
                <span className="text-red-600 font-semibold tabular-nums ml-2">
                  {d.failureCount}×
                </span>
              </div>
              <div className="text-slate-400 mt-0.5">
                Last: {fmtRelative(d.lastFailedAt)}
              </div>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

// ── Primitives ─────────────────────────────────────────────────────────────

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="bg-white rounded-lg border border-slate-200 p-5">
      <h2 className="text-sm font-semibold text-slate-700 mb-4">{title}</h2>
      {children}
    </div>
  );
}

function Empty() {
  return <p className="text-sm text-slate-400">No data yet</p>;
}

function Skeleton() {
  return (
    <div className="space-y-6 animate-pulse">
      <div className="h-7 w-28 bg-slate-200 rounded" />
      <div className="grid grid-cols-6 gap-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-24 bg-slate-200 rounded-lg" />
        ))}
      </div>
      <div className="grid grid-cols-2 gap-6">
        <div className="h-52 bg-slate-200 rounded-lg" />
        <div className="h-52 bg-slate-200 rounded-lg" />
      </div>
      <div className="grid grid-cols-3 gap-6">
        <div className="col-span-2 h-48 bg-slate-200 rounded-lg" />
        <div className="h-48 bg-slate-200 rounded-lg" />
      </div>
    </div>
  );
}
