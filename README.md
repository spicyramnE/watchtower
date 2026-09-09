# Watchtower

**Agentic AI CI/CD Incident Response Platform**

Watchtower is an agentic AI incident-response backend for CI/CD pipelines. It ingests
pipeline failures and production alerts, reasons over logs, pipeline history, and
runbooks using a ReAct-style agent loop and the Model Context Protocol (MCP), and
proposes remediation actions that require human approval before execution. It is built
on Spring Boot, deployed to GCP Cloud Run via a GitHub Actions CI/CD pipeline, and
backed by PostgreSQL and Redis.

> **Note on the reasoning model:** the original project spec called for the Anthropic
> Claude API here. This build uses [Groq](https://groq.com) (an open-weight model host,
> currently `openai/gpt-oss-120b`) instead - a deliberate deviation, not an oversight.
> See "Why Groq instead of Claude" below for the reasoning and its trade-offs.

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
| `search_runbook` | `RunbookSearchService` — Voyage AI embeddings + cosine similarity (see below) |
| `propose_remediation` | `RemediationService` — moves an incident to `AWAITING_APPROVAL` |
| `execute_remediation` | `RemediationService` — simulated execution; Phase 6 adds the human approval gate in front of it. **Never offered to the agent itself** — see Phase 5 notes below |

### RAG: why Voyage AI, and in-memory over pgvector

`search_runbook` started (Phase 3) as keyword-overlap scoring - it could only
match queries that shared literal words with a runbook. Phase 4 replaced it
with real semantic search: [Voyage AI](https://www.voyageai.com) embeddings +
cosine similarity, computed in-memory rather than via pgvector.

- **Why Voyage AI for embeddings, not Claude:** Anthropic doesn't offer an
  embeddings API - Claude is a generation model, not a retrieval one - and
  Voyage is Anthropic's recommended embedding partner. Training a custom
  embedding model was never in scope: that's a multi-month ML research
  problem (billions of training pairs, GPU clusters) orthogonal to what RAG
  actually tests, which is the retrieval pipeline around a pretrained model -
  the same reason this project uses Postgres instead of writing a database.
- **Why in-memory cosine similarity over pgvector:** with 12 runbooks, a
  dedicated vector index (pgvector) buys nothing - a linear scan over a
  dozen vectors is microseconds. `VectorMath.cosineSimilarity` is a ~15-line
  method, easy to explain and test in isolation. pgvector becomes the right
  call once the corpus is large enough that a linear scan matters; see
  `docker-compose.yml`/tech stack notes - this is a documented upgrade path,
  not a limitation nobody considered.
- **Pipeline:** `RunbookSeeder` embeds each runbook's content once at
  startup (`input_type: document`) and caches the vector as a JSON string on
  `Runbook.embedding`, so restarts don't re-call the API. At query time,
  `RunbookSearchService` embeds the query (`input_type: query` - Voyage
  embeds these two asymmetrically for better retrieval), scores every
  candidate by cosine similarity, and filters anything below `MIN_SIMILARITY`
  (0.25, empirically tuned) so an unrelated query returns nothing rather
  than the "least bad" runbook.
- **Verified with a real paraphrase query** (no shared words with any
  runbook): `"my pod won't come up and health checks keep failing"` correctly
  ranked *"What to do when a deployment times out"* first (score 0.54) -
  something Phase 3's keyword matching could never have found.
- **Graceful degradation:** if `VOYAGE_API_KEY` isn't set, indexing and
  search both log a warning and return no results rather than failing
  startup - see Local Development Setup below.

### The ReAct loop, and why Groq instead of Claude

`AgentReasoningService` is the heart of the project: given an incident, it lets
a model reason step by step, call tools to gather real evidence, and conclude
with a grounded diagnosis - the classic ReAct (Reason + Act) pattern.

- **Why Groq instead of the Anthropic Claude API the doc specifies:** this was
  an explicit, informed choice, not a default - Anthropic's API is not free,
  and Groq's free tier removed that barrier entirely for building and testing
  this phase. The trade-off is real and worth being upfront about in an
  interview: Groq hosts *open-weight* models (currently
  `openai/gpt-oss-120b`), not Claude, so the resume story is "an agent built
  against an OpenAI-compatible tool-calling API, currently served by Groq"
  rather than "the Claude API" specifically. Swapping providers is a small,
  contained change (`GroqClient` is the only thing that would need to change
  to point at Anthropic's Messages API instead), since the agent loop itself
  is written against a standard tool-calling contract, not anything
  Groq-specific.
- **The loop:** each incident gets a system prompt plus an evidence summary,
  and the model is offered exactly four tools: `get_recent_logs`,
  `get_pipeline_history`, `search_runbook`, and `propose_remediation`. It's
  instructed to gather evidence before concluding, and to conclude *by
  calling* `propose_remediation` - so the "final diagnosis" the doc describes
  (a recommended action, confidence score, and rationale) is literally that
  tool call's own arguments, reusing Phase 3's `RemediationService` rather
  than inventing a second, parallel "diagnosis" concept.
- **`execute_remediation` is deliberately never offered to the model.** The
  four tools the agent sees are a hard-coded allowlist
  (`AgentReasoningService.AGENT_TOOL_NAMES`), not "all registered MCP tools
  minus one" - so this can't silently regress if a future tool is added. If
  the model somehow still names `execute_remediation` in a tool call, the
  loop blocks it, logs the attempt, and tells the model it isn't permitted,
  without ever invoking it. This is the human-in-the-loop design principle
  enforced in code, not just left to Phase 6's API-level gating.
- **Iteration cap:** hard-capped at 5 steps. If the model never calls
  `propose_remediation` within that budget, the loop stops, logs a clear
  "iteration cap reached" decision-log entry, and returns `concluded: false`
  - visibly inconclusive, never a silent failure or an infinite loop.
- **Every step is logged** to `AgentDecisionLog` (step number, tool name,
  input, output) - this is what `GET /incidents/{id}/decision-log` returns,
  and it's the explainability artifact the eventual dashboard (Phase 8)
  visualizes.
- **Verified against the real Groq API** across 4 different synthetic
  incident types (rollback failure, OOM crash, flaky test, dependency
  failure) - each correctly gathered 2-3 pieces of evidence before
  concluding, and every rationale cited specifics from the actual tool
  output (e.g. "pipeline history shows v1.14.1 was previously successful"),
  not generic boilerplate.
- **Graceful degradation:** if `GROQ_API_KEY` isn't set, `/diagnose` returns
  immediately without touching the incident's status, rather than throwing.

## Build Phases

| # | Phase | Status |
|---|-------|--------|
| 0 | Environment & Tooling Setup | ✅ |
| 1 | Spring Boot Skeleton & Core CRUD | ✅ |
| 2 | Data Model Completion & Synthetic Incident Generation | ✅ |
| 3 | MCP Tool Layer | ✅ |
| 4 | Retrieval-Augmented Generation (Runbook Search) | ✅ |
| 5 | Agent Reasoning Core (ReAct Loop) | ✅ |
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
- A [Voyage AI](https://www.voyageai.com) API key (free tier) - only needed
  for `search_runbook` to return real results; the app runs fine without it,
  just with that one tool returning nothing. Set it as an environment
  variable named `VOYAGE_API_KEY` - never commit it or put it in
  `application.yml`.
- A [Groq](https://console.groq.com) API key (free tier) - only needed for
  `POST /incidents/{id}/diagnose` to actually run; without it, that endpoint
  returns immediately saying so. Set it as `GROQ_API_KEY`, same rule as
  above.

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

# Hand a diagnosed incident to the agent, then see its full reasoning trace
curl -X POST http://localhost:8080/incidents/{id}/diagnose
curl http://localhost:8080/incidents/{id}/decision-log
```

On first startup, the app seeds `src/main/resources/runbooks/*.md` into the
`runbooks` table (12 documents covering common CI/CD incident types) and
embeds each one via Voyage AI (if `VOYAGE_API_KEY` is set) - this is the
knowledge base `search_runbook` semantically searches.

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
- Spring's default `RestClient` picks an HTTP client backed by Java NIO,
  which opens an internal loopback selector pipe - the same class of bug as
  the JDK regression above, and it breaks in the same sandboxed
  environments. Both `VoyageEmbeddingClient` and `GroqClient` explicitly use
  `SimpleClientHttpRequestFactory` (classic `HttpURLConnection`, no NIO) to
  avoid it. Apply the same fix to any future outbound HTTP client that hits
  the identical "Unable to establish loopback connection" error.
- Jackson serializes `null` record fields as explicit JSON `null` by
  default. Groq's chat completions API rejects that for optional
  OpenAI-style message fields (e.g. `name` on a system message must be
  *absent*, not `null`) with a 400. `GroqClient.ChatMessage` is annotated
  `@JsonInclude(NON_NULL)` to omit unset fields entirely - worth checking
  for any future request DTO sent to a strict third-party API.
