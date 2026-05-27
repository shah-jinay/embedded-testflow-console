const COLORS: Record<string, string> = {
  PASSED: "bg-green-100 text-green-800",
  FAILED: "bg-red-100 text-red-800",
  TIMED_OUT: "bg-orange-100 text-orange-800",
  RUNNING: "bg-blue-100 text-blue-800",
  QUEUED: "bg-slate-100 text-slate-600",
  BLOCKED: "bg-yellow-100 text-yellow-800",
  NEEDS_REVIEW: "bg-purple-100 text-purple-800",
};

export function StatusBadge({ status }: { status: string }) {
  const cls = COLORS[status] ?? "bg-slate-100 text-slate-600";
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}
    >
      {status.replace(/_/g, " ")}
    </span>
  );
}
