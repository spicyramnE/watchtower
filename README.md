# Watchtower

An agentic AI platform that diagnoses CI/CD pipeline failures — and never executes a fix without a human saying yes.

<!-- Screenshots: drop PNGs into docs/screenshots/ and uncomment these lines
![Incident list](docs/screenshots/incident-list.png)
![Decision trace](docs/screenshots/decision-trace.png)
![Approval queue](docs/screenshots/approval-queue.png)
-->

## Why This Exists

When a CI/CD pipeline breaks at 2 AM, an on-call engineer has to read build logs, check deployment history, search internal runbooks, decide what to do, then execute the fix. Steps 1–4 are pattern matching — exactly what LLMs are good at. Step 5 is irreversible — exactly where you don't want an AI acting alone.

Watchtower automates the diagnosis while keeping humans in control of execution.

## What Happens When an Incident Arrives

1. An incident is created (simulated via a webhook-shaped payload for this demo)
2. The agent pulls recent service logs, checks pipeline history, and semantically searches a runbook knowledge base
3. It proposes a remediation — with a confidence score and a rationale citing the evidence it found
4. A human reviewer sees the full reasoning trace: every tool the agent called, every input and output, every step of its logic
5. The reviewer approves or rejects. Only then does anything execute.

Every step — agent and human — writes to one continuous audit log. No cross-referencing two separate systems.

## Key Design Decisions

**The agent structurally cannot execute.** `execute_remediation` is excluded from the agent's tool allowlist — a hard-coded list of four permitted tools, not "everything minus one." If the model hallucinates a call to it anyway, the loop blocks it, logs the attempt, and tells the model it's not permitted. This is the safety boundary enforced in code, not in a prompt.

**Real protocol, not a switch statement.** The five tools are registered through the [Model Context Protocol](https://modelcontextprotocol.io) Java SDK, not a `switch(toolName)` wrapper. The MCP endpoint an external client would connect to is the same one the agent's ReAct loop calls internally.

**Semantic search, not keyword matching.** Runbook retrieval uses [Voyage AI](https://voyageai.com) embeddings with asymmetric query/document encoding. A query like *"my pod won't come up and health checks keep failing"* matches a runbook titled *"What to do when a deployment times out"* — zero shared words, 0.54 cosine similarity.

**One audit trail for everything.** Agent reasoning steps and human decisions (approve/reject with reason) write to the same `AgentDecisionLog` table in the same step-number sequence. The dashboard renders them in one timeline.

## Architecture

```
                    ┌──────────────────────────────────────────────────┐
┌──────────────┐    │  Spring Boot 4 · Java 21                        │
│  Next.js 16  │    │                                                  │
│  Dashboard   │───▶│  REST API ──▶ AgentReasoningService (ReAct loop) │
│  :3000       │    │  JWT Auth     ├─ get_recent_logs                 │
└──────────────┘    │               ├─ get_pipeline_history            │
                    │               ├─ search_runbook (Voyage AI RAG)  │
                    │               └─ propose_remediation             │
                    │                  ✗ execute_remediation (blocked)  │
                    │                                                  │
                    │  ┌──────────┐  ┌─────────┐  ┌────────────────┐  │
                    │  │ Groq API │  │ Voyage  │  │ PostgreSQL 17  │  │
                    │  │ (LLM)    │  │ (embed) │  │ (Docker)       │  │
                    │  └──────────┘  └─────────┘  └────────────────┘  │
                    └──────────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Choice | Why |
|-------|--------|-----|
| Backend | Spring Boot 4.1, Java 21 | Industry standard, real MCP SDK support |
| Database | PostgreSQL 17 | Production-grade, runs via Docker Compose |
| Agent LLM | Groq (`openai/gpt-oss-120b`) | Free tier, OpenAI-compatible tool-calling API — swapping providers is a single-file change |
| Embeddings | Voyage AI (`voyage-3.5-lite`) | Anthropic's recommended embedding partner, asymmetric encoding |
| Tool Protocol | Model Context Protocol (MCP) | Real tool-calling standard, not simulated |
| Auth | JWT (HS256) + BCrypt | Stateless, role claim in token, no DB round-trip for authorization |
| Frontend | Next.js 16, TypeScript, Tailwind | Decision-trace timeline is the centerpiece |
| CI/CD | GitHub Actions → Docker → GCP Cloud Run | Tests against real Postgres in CI |

## Limitations

These are deliberate scope choices, not things nobody thought about:

- **Synthetic data.** Incidents, logs, and pipeline history are generated — no real CI/CD system feeds in. This proves the pattern works correctly, not that it's battle-tested.
- **Groq free tier.** The agent uses an open-weight model, not Claude or GPT-4. Swapping is a one-file change (`GroqClient.java`), but this build prioritized zero cost.
- **In-memory vector search.** 12 runbooks don't justify pgvector — a linear scan over a dozen vectors is microseconds. pgvector is the documented upgrade path.
- **Simulated execution.** "Execute remediation" changes a database status. In production this would call kubectl, restart a service, or trigger a rollback.
- **Redis provisioned but unused.** Docker Compose includes it for future caching; nothing reads from it yet.

## Running Locally

**Prerequisites:** JDK 21 (Temurin), Docker Desktop, Node.js 20.9+

**API keys** (both free tier, set as environment variables):
- `VOYAGE_API_KEY` — [voyageai.com](https://voyageai.com) — needed for semantic runbook search
- `GROQ_API_KEY` — [console.groq.com](https://console.groq.com) — needed for the agent's LLM reasoning

The app runs without either key — those features gracefully degrade to no-ops.

```bash
# Infrastructure
docker compose up -d

# Backend (port 8080)
./mvnw spring-boot:run

# Frontend (port 3000)
cd frontend && npm install && npm run dev
```

**Demo accounts** (seeded on first startup): `viewer` / `viewer123`, `approver` / `approver123`

**Tests** (72 total — unit + integration against real Postgres):
```bash
./mvnw test
```

## API

| Method | Path | Auth | What it does |
|--------|------|------|--------------|
| `POST` | `/auth/login` | — | Get a JWT |
| `GET` | `/health` | — | Liveness probe |
| `POST` | `/incidents` | JWT | Create incident |
| `GET` | `/incidents` | JWT | List (optional `?status=` filter) |
| `GET` | `/incidents/{id}` | JWT | Get one |
| `POST` | `/incidents/simulate` | JWT | Generate synthetic incident |
| `POST` | `/incidents/{id}/diagnose` | JWT | Run the agent |
| `GET` | `/incidents/{id}/decision-log` | JWT | Full reasoning + governance trace |
| `GET` | `/incidents/awaiting-approval` | JWT | Pending human review |
| `POST` | `/incidents/{id}/approve` | APPROVER | Approve & execute |
| `POST` | `/incidents/{id}/reject` | APPROVER | Reject with reason |
