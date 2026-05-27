interface StatCardProps {
  label: string;
  value: string | number;
  sub?: string;
  accent?: "green" | "red" | "orange" | "blue" | "gray";
}

const ACCENTS: Record<string, string> = {
  green: "text-green-700",
  red: "text-red-600",
  orange: "text-orange-600",
  blue: "text-blue-600",
  gray: "text-slate-800",
};

export function StatCard({ label, value, sub, accent = "gray" }: StatCardProps) {
  return (
    <div className="rounded-lg bg-white border border-slate-200 p-4">
      <p className="text-xs font-medium text-slate-500 uppercase tracking-wide leading-none">
        {label}
      </p>
      <p className={`mt-2 text-3xl font-bold tabular-nums ${ACCENTS[accent]}`}>{value}</p>
      {sub && <p className="mt-0.5 text-xs text-slate-400">{sub}</p>}
    </div>
  );
}
