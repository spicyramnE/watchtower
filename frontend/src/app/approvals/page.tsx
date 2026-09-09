"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import type { Incident } from "@/lib/types";
import { useAuth } from "@/lib/auth-context";
import { RequireAuth } from "@/components/RequireAuth";
import { SeverityBadge } from "@/components/Badges";

function ApprovalQueueContent() {
  const { role } = useAuth();
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await api.listAwaitingApproval();
      setIncidents(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleApprove(id: number) {
    setBusyId(id);
    try {
      await api.approveIncident(id);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setBusyId(null);
    }
  }

  async function handleReject(id: number) {
    if (!rejectReason.trim()) return;
    setBusyId(id);
    try {
      await api.rejectIncident(id, rejectReason);
      setRejectingId(null);
      setRejectReason("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setBusyId(null);
    }
  }

  if (role !== "APPROVER") {
    return (
      <p className="text-sm text-slate-500">
        Only the APPROVER role can view the approval queue.
      </p>
    );
  }

  return (
    <div>
      <h1 className="mb-4 text-lg font-semibold text-slate-900">Approval Queue</h1>
      {error && <p className="mb-4 text-sm text-red-600">{error}</p>}

      {!loading && incidents.length === 0 && (
        <p className="text-sm text-slate-400">Nothing awaiting approval right now.</p>
      )}

      <div className="space-y-3">
        {incidents.map((incident) => (
          <div key={incident.id} className="rounded-lg border border-slate-200 bg-white p-4">
            <div className="mb-2 flex items-center justify-between">
              <Link href={`/incidents/${incident.id}`} className="font-medium text-slate-900 hover:underline">
                #{incident.id} - {incident.serviceName}
              </Link>
              <SeverityBadge severity={incident.severity} />
            </div>
            {incident.proposedAction && (
              <div className="mb-3 rounded-md bg-amber-50 p-2 text-sm text-amber-900">
                <p className="font-medium">{incident.proposedAction}</p>
                {incident.confidenceScore != null && (
                  <p className="text-xs text-amber-700">
                    Confidence: {(incident.confidenceScore * 100).toFixed(0)}%
                  </p>
                )}
                {incident.rationale && <p className="text-xs text-amber-700">{incident.rationale}</p>}
              </div>
            )}

            {rejectingId === incident.id ? (
              <div className="flex gap-2">
                <input
                  type="text"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Reason for rejection"
                  className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
                />
                <button
                  onClick={() => handleReject(incident.id)}
                  disabled={busyId === incident.id || !rejectReason.trim()}
                  className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                >
                  Confirm
                </button>
                <button
                  onClick={() => setRejectingId(null)}
                  className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <div className="flex gap-2">
                <button
                  onClick={() => handleApprove(incident.id)}
                  disabled={busyId === incident.id}
                  className="rounded-md bg-green-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
                >
                  Approve
                </button>
                <button
                  onClick={() => setRejectingId(incident.id)}
                  disabled={busyId === incident.id}
                  className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
                >
                  Reject
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

export default function ApprovalQueuePage() {
  return (
    <RequireAuth>
      <ApprovalQueueContent />
    </RequireAuth>
  );
}
