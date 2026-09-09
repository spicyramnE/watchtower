import type { IncidentStatus, Severity } from "@/lib/types";

const STATUS_STYLES: Record<IncidentStatus, string> = {
  NEW: "bg-slate-100 text-slate-700",
  DIAGNOSING: "bg-blue-100 text-blue-700",
  AWAITING_APPROVAL: "bg-amber-100 text-amber-800",
  RESOLVED: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
};

const SEVERITY_STYLES: Record<Severity, string> = {
  LOW: "bg-slate-100 text-slate-600",
  MEDIUM: "bg-yellow-100 text-yellow-700",
  HIGH: "bg-orange-100 text-orange-700",
  CRITICAL: "bg-red-100 text-red-700",
};

function Badge({ text, className }: { text: string; className: string }) {
  return (
    <span
      className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${className}`}
    >
      {text}
    </span>
  );
}

export function StatusBadge({ status }: { status: IncidentStatus }) {
  return <Badge text={status.replace("_", " ")} className={STATUS_STYLES[status]} />;
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  return <Badge text={severity} className={SEVERITY_STYLES[severity]} />;
}
