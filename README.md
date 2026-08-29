# Watchtower

**Agentic AI CI/CD Incident Response Platform**

Watchtower is an agentic AI incident-response backend for CI/CD pipelines. It ingests
pipeline failures and production alerts, reasons over logs, pipeline history, and
runbooks using a ReAct-style agent loop powered by the Anthropic Claude API and the
Model Context Protocol (MCP), and proposes remediation actions that require human
approval before execution. It is built on Spring Boot, deployed to GCP Cloud Run via a
GitHub Actions CI/CD pipeline, and backed by PostgreSQL and Redis.

> **Not to be confused with** [Containrrr/Watchtower](https://github.com/containrrr/watchtower),
> an unrelated open-source Docker container auto-updater.

## Design Principles

- **Interview defensibility over feature count** — every component must be something
  the builder can explain in depth, not just demo.
- **Human-in-the-loop by default** — the agent proposes, a human approves; nothing
  destructive executes autonomously.
- **Explainability as a first-class feature** — every agent decision (tool calls,
  reasoning, confidence) is logged and viewable, not just the final answer.
- **Incremental, always-working milestones** — each phase ends in something that runs
  and can be demoed, even if later phases are incomplete.
- **Real protocols over shortcuts** — MCP is used as an actual tool-calling protocol,
  not simulated with plain function calls.

## Build Phases

| # | Phase | Status |
|---|-------|--------|
| 0 | Environment & Tooling Setup | ✅ |
| 1 | Spring Boot Skeleton & Core CRUD | ⬜ |
| 2 | Data Model Completion & Synthetic Incident Generation | ⬜ |
| 3 | MCP Tool Layer | ⬜ |
| 4 | Retrieval-Augmented Generation (Runbook Search) | ⬜ |
| 5 | Agent Reasoning Core (ReAct Loop) | ⬜ |
| 6 | Approval Gating & Remediation Execution | ⬜ |
| 7 | Authentication & RBAC | ⬜ |
| 8 | Frontend Dashboard | ⬜ |
| 9 | CI/CD & GCP Deployment | ⬜ |
| 10 | Testing, Documentation & Interview Readiness | ⬜ |

## Local Development Setup

### Prerequisites

- JDK 21+ (project targets 21; a newer installed JDK is fine)
- Docker Desktop
- Git

### Infrastructure

Postgres and Redis run as Docker containers:

```bash
docker compose up -d
docker ps                 # both containers should show 'Up'
docker exec -it watchtower-postgres psql -U watchtower_user -d watchtower
```

Further setup instructions are added as each phase lands.
