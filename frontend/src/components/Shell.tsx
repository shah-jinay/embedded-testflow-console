import { useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";

const NAV = [
  { to: "/", label: "Dashboard" },
  { to: "/runs", label: "Test Runs" },
];

export function Shell() {
  const queryClient = useQueryClient();

  const { data: health, isError } = useQuery({
    queryKey: ["health"],
    queryFn: api.health,
    refetchInterval: 30_000,
    retry: false,
  });

  const isUp = !isError && health?.status === "UP";

  // ── SSE: live run updates ──────────────────────────────────────────────────
  const [liveConnected, setLiveConnected] = useState(false);

  useEffect(() => {
    const es = new EventSource("/api/events/runs");

    es.addEventListener("connected", () => {
      setLiveConnected(true);
    });

    es.addEventListener("RUN_CREATED", () => {
      queryClient.invalidateQueries({ queryKey: ["test-runs"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    });

    es.addEventListener("RUN_STATUS_CHANGED", () => {
      queryClient.invalidateQueries({ queryKey: ["test-runs"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    });

    es.onerror = () => {
      setLiveConnected(false);
    };

    return () => {
      es.close();
      setLiveConnected(false);
    };
  }, [queryClient]);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Top bar */}
      <header className="sticky top-0 z-10 bg-white border-b border-slate-200 px-6 h-14 flex items-center justify-between">
        <div className="flex items-center gap-6">
          <span className="font-bold text-slate-900 text-sm tracking-tight whitespace-nowrap">
            Embedded TestFlow
          </span>
          <nav className="flex gap-1">
            {NAV.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                end={to === "/"}
                className={({ isActive }) =>
                  `px-3 py-1.5 rounded text-sm font-medium transition-colors ${
                    isActive
                      ? "bg-slate-100 text-slate-900"
                      : "text-slate-500 hover:text-slate-800 hover:bg-slate-50"
                  }`
                }
              >
                {label}
              </NavLink>
            ))}
          </nav>
        </div>

        <div className="flex items-center gap-4 text-xs text-slate-400">
          {/* Live SSE indicator */}
          {liveConnected && (
            <div className="flex items-center gap-1.5" title="Live updates connected">
              <span className="inline-block h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-emerald-600 font-medium">Live</span>
            </div>
          )}

          {/* Backend health */}
          <div className="flex items-center gap-1.5">
            <span
              className={`inline-block h-2 w-2 rounded-full ${
                isUp ? "bg-green-500" : "bg-red-400 animate-pulse"
              }`}
            />
            <span>{isUp ? "Backend healthy" : health ? "Degraded" : "Connecting…"}</span>
          </div>

          <a
            href="/swagger-ui.html"
            className="hover:text-slate-700 transition-colors"
            title="OpenAPI / Swagger UI"
          >
            API Docs
          </a>
          <a
            href="http://localhost:3000"
            target="_blank"
            rel="noreferrer"
            className="hover:text-slate-700 transition-colors"
          >
            Grafana
          </a>
        </div>
      </header>

      {/* Page content */}
      <main className="flex-1 px-6 py-6 mx-auto w-full max-w-7xl">
        <Outlet />
      </main>
    </div>
  );
}
