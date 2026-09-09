import type {
  DecisionLogEntry,
  DiagnosisResult,
  Incident,
  IncidentStatus,
  LoginResponse,
} from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const TOKEN_KEY = "watchtower_token";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers as Record<string, string> | undefined),
  };

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = await response.json();
      message = body.messages?.join(", ") || body.error || message;
    } catch {
      // body wasn't JSON - fall back to statusText
    }
    throw new ApiError(response.status, message);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),

  listIncidents: (status?: IncidentStatus) =>
    request<Incident[]>(`/incidents${status ? `?status=${status}` : ""}`),

  getIncident: (id: number) => request<Incident>(`/incidents/${id}`),

  getDecisionLog: (id: number) => request<DecisionLogEntry[]>(`/incidents/${id}/decision-log`),

  simulateIncident: () => request<Incident>("/incidents/simulate", { method: "POST" }),

  diagnoseIncident: (id: number) =>
    request<DiagnosisResult>(`/incidents/${id}/diagnose`, { method: "POST" }),

  listAwaitingApproval: () => request<Incident[]>("/incidents/awaiting-approval"),

  approveIncident: (id: number) => request<Incident>(`/incidents/${id}/approve`, { method: "POST" }),

  rejectIncident: (id: number, reason: string) =>
    request<Incident>(`/incidents/${id}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),
};
