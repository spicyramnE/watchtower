"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import type { DecisionLogEntry, Incident } from "@/lib/types";
import { useAuth } from "@/lib/auth-context";
import { RequireAuth } from "@/components/RequireAuth";
import { SeverityBadge, StatusBadge } from "@/components/Badges";

function tryFormatJson(value: string | null): string | null {
  if (!value) return null;
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function TimelineStep({ entry }: { entry: DecisionLogEntry }) {
  const isHuman = entry.toolName === "human_approval" || entry.toolName === "human_rejection";

  return (
    <li className="relative pb-8 pl-8 last:pb-0">
      <span className="absolute left-[7px] top-5 -bottom-2 w-px bg-slate-200 last:hidden" />
      <span
        className={`absolute left-0 top-1 flex h-4 w-4 items-center justify-center rounded-full border-2 ${
          isHuman ? "border-purple-500 bg-purple-100" : "border-slate-400 bg-white"
        }`}
      />
      <div className="rounded-lg border border-slate-200 bg-white p-3">
        <div className="mb-2 flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-500">Step {entry.stepNumber}</span>
          {entry.toolName && (
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
              {entry.toolName}
            </span>
          )}
          <span className="text-xs text-slate-400">
            {new Date(entry.createdAt).toLocaleTimeString()}
          </span>
        </div>

        {entry.toolInput && (
          <div className="mb-2">
            <p className="mb-1 text-xs font-medium text-slate-500">Input</p>
            <pre className="overflow-x-auto rounded bg-slate-50 p-2 text-xs text-slate-700">
              {tryFormatJson(entry.toolInput)}
            </pre>
          </div>
        )}

        {entry.toolOutput && (
          <div className="mb-2">
            <p className="mb-1 text-xs font-medium text-slate-500">Output</p>
            <pre className="overflow-x-auto rounded bg-slate-50 p-2 text-xs text-slate-700">
              {tryFormatJson(entry.toolOutput)}
            </pre>
          </div>
        )}

        {entry.reasoning && (
          <div>
            <p className="mb-1 text-xs font-medium text-slate-500">
              {isHuman ? "Reason" : "Reasoning"}
            </p>
            <p className="text-sm text-slate-700">{entry.reasoning}</p>
          </div>
        )}
      </div>
    </li>
  );
}

function IncidentDetailContent() {
  const params = useParams<{ id: string }>();
  const incidentId = Number(params.id);
  const router = useRouter();
  const { role } = useAuth();

  const [incident, setIncident] = useState<Incident | null>(null);
  const [logEntries, setLogEntries] = useState<DecisionLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [incidentData, logData] = await Promise.all([
        api.getIncident(incidentId),
        api.getDecisionLog(incidentId),
      ]);
      setIncident(incidentData);
      setLogEntries(logData);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    load();
  }, [load]);

  async function handleDiagnose() {
    setBusy(true);
    setError(null);
    try {
      await api.diagnoseIncident(incidentId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setBusy(false);
    }
  }

  async function handleApprove() {
    setBusy(true);
    setError(null);
    try {
      await api.approveIncident(incidentId);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setBusy(false);
    }
  }

  async function handleReject() {
    if (!rejectReason.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await api.rejectIncident(incidentId, rejectReason);
      setShowRejectForm(false);
      setRejectReason("");
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to reach the server");
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <p className="text-sm text-slate-500">Loading...</p>;
  }

  if (!incident) {
    return <p className="text-sm text-red-600">{error ?? "Incident not found"}</p>;
  }

  const canApprove = role === "APPROVER" && incident.status === "AWAITING_APPROVAL";

  return (
    <div>
      <button onClick={() => router.push("/incidents")} className="mb-4 text-sm text-slate-500 hover:underline">
        ← Back to incidents
      </button>

      <div className="mb-6 rounded-lg border border-slate-200 bg-white p-5">
        <div className="mb-3 flex items-center justify-between">
          <h1 className="text-lg font-semibold text-slate-900">
            Incident #{incident.id} - {incident.serviceName}
          </h1>
          <div className="flex gap-2">
            <SeverityBadge severity={incident.severity} />
            <StatusBadge status={incident.status} />
          </div>
        </div>
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
          <div>
            <dt className="text-slate-500">Source</dt>
            <dd className="text-slate-900">{incident.source}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Created</dt>
            <dd className="text-slate-900">{new Date(incident.createdAt).toLocaleString()}</dd>
          </div>
        </dl>
        <div className="mt-3">
          <p className="mb-1 text-sm text-slate-500">Raw payload</p>
          <pre className="overflow-x-auto rounded bg-slate-50 p-3 text-xs text-slate-700">
            {tryFormatJson(incident.rawPayload)}
          </pre>
        </div>

        {incident.proposedAction && (
          <div className="mt-4 rounded-md border border-amber-200 bg-amber-50 p-3">
            <p className="text-sm font-medium text-amber-900">Proposed remediation</p>
            <p className="text-sm text-amber-800">{incident.proposedAction}</p>
            {incident.confidenceScore != null && (
              <p className="mt-1 text-xs text-amber-700">
                Confidence: {(incident.confidenceScore * 100).toFixed(0)}%
              </p>
            )}
            {incident.rationale && <p className="mt-1 text-xs text-amber-700">{incident.rationale}</p>}
          </div>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-2">
          {incident.status === "NEW" && (
            <button
              onClick={handleDiagnose}
              disabled={busy}
              className="rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
            >
              {busy ? "Diagnosing..." : "Diagnose with agent"}
            </button>
          )}

          {canApprove && !showRejectForm && (
            <>
              <button
                onClick={handleApprove}
                disabled={busy}
                className="rounded-md bg-green-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
              >
                Approve
              </button>
              <button
                onClick={() => setShowRejectForm(true)}
                disabled={busy}
                className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
              >
                Reject
              </button>
            </>
          )}

          {role === "VIEWER" && incident.status === "AWAITING_APPROVAL" && (
            <span className="text-xs text-slate-400">Only APPROVER role can act on this proposal</span>
          )}
        </div>

        {canApprove && showRejectForm && (
          <div className="mt-3 flex gap-2">
            <input
              type="text"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Reason for rejection"
              className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm"
            />
            <button
              onClick={handleReject}
              disabled={busy || !rejectReason.trim()}
              className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-50"
            >
              Confirm reject
            </button>
            <button
              onClick={() => setShowRejectForm(false)}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-50"
            >
              Cancel
            </button>
          </div>
        )}

        {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      </div>

      <h2 className="mb-3 text-base font-semibold text-slate-900">Agent decision trace</h2>
      {logEntries.length === 0 ? (
        <p className="text-sm text-slate-400">No reasoning steps yet - click &quot;Diagnose with agent&quot; above.</p>
      ) : (
        <ol>
          {logEntries.map((entry, i) => (
            <TimelineStep key={i} entry={entry} />
          ))}
        </ol>
      )}
    </div>
  );
}

export default function IncidentDetailPage() {
  return (
    <RequireAuth>
      <IncidentDetailContent />
    </RequireAuth>
  );
}
