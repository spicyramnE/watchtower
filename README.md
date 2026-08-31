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

## Architecture Notes

### Why MCP instead of plain function calls

Most "agentic AI" student projects fake tool use with an internal `switch` on a
function name, dressed up in a prompt. Watchtower's tools are real
[Model Context Protocol](https://modelcontextprotocol.io) tools: `WatchtowerMcpTools`
(`src/main/java/.../mcp/WatchtowerMcpTools.java`) methods are annotated with
Spring AI's `@McpTool`/`@McpToolParam` and registered by
`spring-ai-starter-mcp-server-webmvc` against the official
`io.modelcontextprotocol.sdk:mcp` Java SDK — the same SDK a real MCP client
(Claude Desktop, another agent) would talk to. The protocol machinery is a thin
wrapper: each tool's actual logic lives in a plain, independently unit-tested
service in `service/` (`LogService`, `PipelineHistoryService`,
`RunbookSearchService`, `RemediationService`), so the business logic can be
proven correct before any protocol concerns are layered on top.

The five tools:

| Tool | Backed by |
|---|---|
| `get_recent_logs` | `LogService` (synthetic log lines) |
| `get_pipeline_history` | `PipelineHistoryService` (synthetic build/deploy outcomes) |
| `search_runbook` | `RunbookSearchService` (keyword-overlap scoring for now — Phase 4 upgrades this to embeddings) |
| `propose_remediation` | `RemediationService` — moves an incident to `AWAITING_APPROVAL` |
| `execute_remediation` | `RemediationService` — simulated execution; Phase 6 adds the human approval gate in front of it |

## Build Phases

| # | Phase | Status |
|---|-------|--------|
| 0 | Environment & Tooling Setup | ✅ |
| 1 | Spring Boot Skeleton & Core CRUD | ✅ |
| 2 | Data Model Completion & Synthetic Incident Generation | ✅ |
| 3 | MCP Tool Layer | ✅ |
| 4 | Retrieval-Augmented Generation (Runbook Search) | ⬜ |
| 5 | Agent Reasoning Core (ReAct Loop) | ⬜ |
| 6 | Approval Gating & Remediation Execution | ⬜ |
| 7 | Authentication & RBAC | ⬜ |
| 8 | Frontend Dashboard | ⬜ |
| 9 | CI/CD & GCP Deployment | ⬜ |
| 10 | Testing, Documentation & Interview Readiness | ⬜ |

## Local Development Setup

### Prerequisites

- JDK 21 ([Eclipse Temurin](https://adoptium.net/) recommended — see note below)
- Docker Desktop
- Git

### Infrastructure

Postgres and Redis run as Docker containers:

```bash
docker compose up -d
docker ps                 # both containers should show 'Up'
docker exec -it watchtower-postgres psql -U watchtower_user -d watchtower
```

### Running the app

```bash
./mvnw spring-boot:run
```

App runs on `http://localhost:8080`. Try it:

```bash
curl -X POST http://localhost:8080/incidents \
  -H "Content-Type: application/json" \
  -d '{"source":"github-actions","serviceName":"payments-service","severity":"HIGH","rawPayload":"{\"error\":\"OOMKilled\"}"}'

curl http://localhost:8080/incidents

# Dev/demo-only: generates a realistic synthetic incident
curl -X POST http://localhost:8080/incidents/simulate
```

On first startup, the app seeds `src/main/resources/runbooks/*.md` into the
`runbooks` table (12 documents covering common CI/CD incident types) - this
is the knowledge base RAG search will query against in Phase 4.

The MCP server is exposed at `POST /mcp` (Streamable HTTP transport) once the
app is running - Phase 5's ReAct loop is the first real client of it.

Run the test suite (also exercises the endpoints via MockMvc, and the MCP
tools via their real registered protocol handlers, against real Postgres):

```bash
./mvnw test
```

### Windows notes

- **Use JDK 21, not the newest available JDK.** Very new JDK builds have an
  unrelated NIO regression on some Windows setups that breaks the embedded
  server's socket handling. JDK 21 (LTS) avoids it.
- pgjdbc reads its `TimeZone` startup parameter from the JVM's default
  timezone. Some Windows locales resolve this to the deprecated `Asia/Calcutta`
  alias, which Postgres 17 rejects outright at connection time. `WatchtowerApplication.main()`
  forces `UTC` for the running app; the Surefire plugin config in `pom.xml`
  does the same for the test JVM (tests don't go through `main()`).
