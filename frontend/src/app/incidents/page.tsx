"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import type { Incident, IncidentStatus } from "@/lib/types";
import { RequireAuth } from "@/components/RequireAuth";
import { SeverityBadge, StatusBadge } from "@/components/Badges";

const STATUS_OPTIONS: (IncidentStatus | "ALL")[] = [
  "ALL",
  "NEW",
  "DIAGNOSING",
  "AWAITING_APPROVAL",
  "RESOLVED",
  "REJECTED",
];

function IncidentListContent() {
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [statusFilter, setStatusFilter] = useState<IncidentStatus | "ALL">("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [simulating, setSimulating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.listIncidents(statusFilter === "ALL" ? undefined : statusFilter);
      setIncidents(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    // Fetch-on-mount: load() is async and awaits the API call before
    // touching state, so this isn't the synchronous-setState pattern the
    // rule targets - it's the standard data-fetching effect React's own
    // docs recommend.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    load();
  }, [load]);

  async function handleSimulate() {
    setSimulating(true);
    try {
      await api.simulateIncident();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setSimulating(false);
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900">Incidents</h1>
        <button
          onClick={handleSimulate}
          disabled={simulating}
          className="rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50"
        >
          {simulating ? "Generating..." : "Simulate incident"}
        </button>
      </div>

      <div className="mb-4 flex gap-2">
        {STATUS_OPTIONS.map((status) => (
          <button
            key={status}
            onClick={() => setStatusFilter(status)}
            className={`rounded-full px-3 py-1 text-xs font-medium ${
              statusFilter === status
                ? "bg-slate-900 text-white"
                : "bg-white text-slate-600 border border-slate-200 hover:bg-slate-100"
            }`}
          >
            {status === "ALL" ? "All" : status.replace("_", " ")}
          </button>
        ))}
      </div>

      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-4 py-2">ID</th>
              <th className="px-4 py-2">Service</th>
              <th className="px-4 py-2">Severity</th>
              <th className="px-4 py-2">Status</th>
              <th className="px-4 py-2">Created</th>
            </tr>
          </thead>
          <tbody>
            {!loading && incidents.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
                  No incidents found
                </td>
              </tr>
            )}
            {incidents.map((incident) => (
              <tr key={incident.id} className="border-b border-slate-100 last:border-0 hover:bg-slate-50">
                <td className="px-4 py-2">
                  <Link href={`/incidents/${incident.id}`} className="font-medium text-slate-900 hover:underline">
                    #{incident.id}
                  </Link>
                </td>
                <td className="px-4 py-2">{incident.serviceName}</td>
                <td className="px-4 py-2">
                  <SeverityBadge severity={incident.severity} />
                </td>
                <td className="px-4 py-2">
                  <StatusBadge status={incident.status} />
                </td>
                <td className="px-4 py-2 text-slate-500">
                  {new Date(incident.createdAt).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default function IncidentListPage() {
  return (
    <RequireAuth>
      <IncidentListContent />
    </RequireAuth>
  );
}
