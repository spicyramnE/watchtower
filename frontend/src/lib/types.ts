export type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type IncidentStatus =
  | "NEW"
  | "DIAGNOSING"
  | "AWAITING_APPROVAL"
  | "RESOLVED"
  | "REJECTED";

export type Role = "VIEWER" | "APPROVER";

export interface Incident {
  id: number;
  source: string;
  serviceName: string;
  severity: Severity;
  rawPayload: string;
  status: IncidentStatus;
  createdAt: string;
  updatedAt: string;
  proposedAction: string | null;
  confidenceScore: number | null;
  rationale: string | null;
}

export interface DecisionLogEntry {
  stepNumber: number;
  toolName: string | null;
  toolInput: string | null;
  toolOutput: string | null;
  reasoning: string | null;
  createdAt: string;
}

export interface DiagnosisResult {
  incidentId: number;
  incidentStatus: IncidentStatus;
  iterationsUsed: number;
  concluded: boolean;
  summary: string;
}

export interface LoginResponse {
  token: string;
  role: Role;
}
