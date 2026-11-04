# GajaMitra — Implementation Plan (production-grade, multi-agent, Spring Boot + Python)

## Context
GajaMitra is our Meta Global AI Developer Hackathon project: an early-warning system for human–elephant
conflict in Sri Lanka. Today it is a working single-file prototype:
- `agent.py`: SAM 3.1 tracks elephants in an uploaded clip (`segment_video`), `keyframes` draws boxes,
  one Muse Spark agent (`run_agent`) reads behaviour and calls tools (alert, officer).
- `track.py`: pure maths: `parse_sam`, `tracks`, `velocity`, `assess` (distance/ETA/risk). Tested by `test_track.py`.
- `app.py`: FastAPI `/analyze` (sync). `index.html`: vanilla JS dashboard with canvas overlay.

Goal: turn it into a production-grade, interview-ready system (target: Sysco LABS-style Java/Spring roles
plus agentic-AI roles) while keeping the Meta-model core that the hackathon judges.

Decisions made with the user:
- **Backend: Spring Boot (Java) + Python AI service.** Spring Boot owns the product (users, sites, uploads,
  alerts, SMS, approvals, history, data). Python owns perception and reasoning (SAM 3.1, LangGraph, Muse Spark).
  Chosen because the user targets Sysco LABS interviews (Java/Spring), and the AI libraries live in Python.
- **Agents: multi-agent with LangGraph:** Responder → Verifier (Reflexion-style critique) → deterministic
  policy guard → dispatch (warnings now) → officer approval gate for escalations (interrupt/resume) → dispatch.
- **No hardware:** remove `trigger_deterrent`, `DETERRENT_URL` and all ESP32 mentions (done in the prototype, 2026-11-04).
- **Alerts: dashboard + real SMS gateway** (Twilio) behind a feature flag, with shadow mode.
- **Problem statement: Build for Your World** (recommended; user confirms at submission). Push the Frontier
  ("control a physical system") relied on the ESP32 loop, which is gone, and Stage 1 is pass/fail on fit.

Out of scope (deliberately): RAG, vector DBs, embeddings, fine-tuning, A2A, low-code builders, Kubernetes.
None has a real job in this system.

---

## Target architecture

```
React + TS dashboard ──REST/SSE──► Spring Boot backend ──► PostgreSQL (sites, analyses, alerts, approvals, audit)
                                       │   ▲     │
                    analysis.requests  │   │     └─► Twilio SMS (feature flag; shadow mode = log only)
                    analysis.resume    ▼   │ analysis.events
                                     RabbitMQ
                                       │   ▲
                                       ▼   │
                              Python AI worker (LangGraph)
                     SAM 3.1 → track.py → Responder → Verifier → Policy guard → Dispatch (now) → Approval gate → Dispatch (escalation)
                                       │                                                           │
                                       └──── MCP client ──► Spring Boot MCP server (tools) ◄────────┘
                              Langfuse (traces)     Postgres (LangGraph checkpoints)     shared /data/videos volume
```

Boundary rule: **Python never performs side effects directly.** Every side effect (SMS, officer report,
history lookup) is an MCP tool hosted by Spring Boot, which re-checks policy, writes the audit log and
honours shadow mode. That is defence in depth plus least privilege.

### Safety and delivery invariants
These hold across every phase; each has a test in Verification.
1. **No silent herd.** An elephant within `NEAR_BODIES` body heights of the boundary is at least `medium`
   risk even when standing still (`track.assess`; done in the prototype). The guard trusts the tracker, so the
   tracker must not report `low` for a herd at the fence.
2. **A human never delays a warning.** Approval can only hold back an *escalation* (`warning` → `danger`) at
   `high` risk. `critical` (an elephant already on the village side) is never held. An escalation that waits
   longer than the site's `approval_timeout_s` is sent automatically: for life safety, a timeout fails toward
   warning people.
3. **The second guardrail fails closed.** Spring rejects `send_village_alert` when it has no stored tracker
   risk for that analysis. The risk is written through a synchronous MCP call before any LLM runs, so a
   missing risk means a bug, not a race.
4. **Effects happen at most once.** Every side-effect tool call carries an idempotency key
   (`{analysis_id}:{stage}:{index}`). Spring stores it under a UNIQUE constraint and returns the original
   result for a repeat call; SMS deliveries are unique per (alert, subscriber). Crashes and redelivery then
   resend nothing.
5. **Work resumes, it doesn't restart.** A redelivered request whose LangGraph thread already exists resumes
   from its checkpoint instead of re-running SAM and the LLMs.

### Repository layout (monorepo)
```
policy/policy_table.json   risk → max severity + hold rule; read by policy.py and (as a Maven resource) by Spring
ai-service/           Python 3.11
  gajamitra/perception.py   ← segment_video, keyframes moved from agent.py (unchanged logic)
  gajamitra/track.py        ← moved unchanged
  gajamitra/schemas.py      Pydantic models (Assessment, ProposedAction, Verdict, GraphState)
  gajamitra/prompts.py      Responder + Verifier system prompts
  gajamitra/graph.py        LangGraph definition
  gajamitra/policy.py       deterministic guard
  gajamitra/worker.py       RabbitMQ consumer/publisher
  gajamitra/cli.py          run one clip locally without Spring (stub tools)
  evals/                    cases.yaml, runner, judge
  tests/                    test_track.py (moved), test_policy.py, test_graph.py
backend/              Java 21, Spring Boot 4.1, Maven wrapper
frontend/             React + TypeScript (Vite)
docker-compose.yml
.github/workflows/ci.yml
README.md
```
The prototype (`gajamitra/agent.py`, `app.py`, `index.html`) stays runnable in `ai-service/` until Spring Boot and React replace it (Phases 3 and 5), then it is deleted.

---

## Phase 0: Validate foundations (spikes, no product code)
Purpose: prove every external dependency works before building on it.

1. **Meta Model API access**: claim credits, add card, set a spend limit, set `MODEL_API_KEY`.
2. **Model IDs**: `client.models.list()`; confirm the exact SAM 3.1 and Muse Spark IDs and base URL used in `agent.py`.
3. **SAM output shape**: run `segment_video` on one clip, save the raw streamed text, confirm `parse_sam`
   handles it (frame markers, box fields, w/h). Check whether boxes come for every frame or sampled frames
   (the dashboard currently looks up `frames[now]` exactly).
4. **Model compatibility** (now `scripts/phase0_check.py`; LangChain dropped in GM-8): `ChatOpenAI(base_url=<Meta API>, model=<Muse Spark>)` with (a) image input,
   (b) tool calling, (c) `with_structured_output`. If structured output fails, fall back to `method="function_calling"`.
5. **MCP interop spike**: a Spring AI MCP server (`spring-ai-starter-mcp-server-webmvc`) exposing one dummy tool,
   called from Python via `langchain-mcp-adapters` (`MultiServerMCPClient`, Streamable HTTP/SSE transport).
   Pin the Spring Boot + Spring AI versions that pass here and the transport that works; record them in the README.
6. **Twilio**: trial account; enable Sri Lanka in *Messaging → Geo permissions*; send one Sinhala and one Tamil
   SMS to a verified number. Unicode SMS: 70 chars for a single message, 67 per part once concatenated. Trial
   messages are prefixed "Sent from your Twilio trial account", which costs segments; budget alert length for it.
7. **Demo footage**: 10–15 clips (Pexels, Pixabay, Wikimedia Commons, own tabletop toy-elephant footage);
   record source URL and licence for each in `evals/footage.md`.
8. **Repo**: `git init`, public GitHub repo, initial commit of the current prototype (marks the starting point).

Exit: every spike passes, or the plan is adjusted for the one that failed.

---

## Phase 1: Restructure and remove hardware
1. ~~Create the monorepo layout; move `track.py` and `test_track.py` into `ai-service/` unchanged.~~ Done 2026-11-04 (`gajamitra/track.py`, `tests/test_track.py` as a pytest test).
2. ~~Split `agent.py`: `segment_video`, `keyframes` and the lazy `meta()` client → `perception.py` (reuse as-is).
   The client is built on first use, so importing perception in tests/CI needs no API key. Old `run_agent`,
   `TOOLS`, `ACTIONS` are replaced in Phase 2.~~ Done 2026-11-04 (the prototype loop stays in `gajamitra/agent.py` until then).
3. ~~Delete `trigger_deterrent`, `DETERRENT_URL`, the ESP32 text, and the deterrent card in the UI.~~ Done 2026-11-04.
4. ~~Add a **SAM result cache** keyed by the video's SHA-256. Re-runs and evals then
   don't pay for SAM again.~~ Done in GM-5: raw completed SAM text in `data/sam-cache` (`GAJAMITRA_SAM_CACHE` for the shared volume).
5. `pyproject.toml` with deps: `openai`, `opencv-python-headless`, `langgraph`, `langgraph-checkpoint-postgres`,
   `langchain-mcp-adapters`, `pydantic`, `aio-pika`, `langfuse`; dev: `pytest`, `ruff`, `deepeval`. (`langchain-openai`
   dropped in GM-8: models sit behind the `ChatModel` port.)

Exit: `pytest` passes (track tests), `python -m gajamitra.cli clip.mp4 --boundary ...` prints tracker summary.

---

## Phase 2: Multi-agent pipeline (LangGraph, Python)

> **Status (GM-8):** the graph exists (`graph.py`, `ports.py`, `prompts.py`, `cli.py`) with every node below, run
> synchronously with an in-memory checkpointer and the `StubToolClient`; 19 graph tests. Decided while building it:
> - **No LangChain model layer.** Models sit behind a `ChatModel` port (`messages -> text`, Muse Spark through the
>   existing OpenAI-compatible client). Output is validated with the Pydantic models plus one retry, so
>   `langchain-openai` and `with_structured_output` aren't needed (Phase 0 step 4 is covered by `scripts/phase0_check.py`).
> - **Verdict** is `{decision, reasons, aggression}`. The verifier can't suggest a severity: only the guard sets levels.
>   `reject` discards the responder's text, and the guard sends its template alert; an unreadable verifier reply
>   counts as `approve` (the guard still applies).
> - Still to do: Langfuse tracing, timeouts and retries on Muse Spark calls, usage accounting, async + `AsyncPostgresSaver`
>   and the MCP client (Phase 4).
### State (`schemas.py`, Pydantic)
As built (GM-8, `graph.State`): `analysis_id, site_id, video_path, boundary, summary (from assess), keyframes, site,
history, models_started, responder (assessment + proposal), verdict, revisions, decision (guard output), approval,
dispatched, notes`. Values are plain dicts so the checkpointer can store them; every node re-validates what it
reads with the Pydantic models. Still to add: `usage` (tokens, latency).

### Nodes (`graph.py`)
1. **perceive**: `segment_video` (cached) → `parse_sam` → `assess` → `keyframes`, then MCP
   `record_assessment(analysis_id, risk, summary)` and **wait for it** (invariant 3: Spring's re-check needs the
   risk stored before any alert). Emits progress events. No elephants → route to **finish** (risk low, no actions).
   SAM and the OpenAI client are blocking: use `AsyncOpenAI`, or `asyncio.to_thread`, so a long SAM call doesn't
   stall the aio-pika event loop and drop the RabbitMQ heartbeat.
2. **load_context**: MCP `get_site_history(site_id, days=14)` (**episodic memory**: recent incidents, times,
   herd sizes) and `get_site_procedures(site_id)` (**procedural memory**: villages, landmarks, officer, approval rule).
3. **responder** (Muse Spark, vision): input = keyframes + tracker summary + site context. Structured output
   `Assessment {behaviour, calves, reasoning}` + `Proposal {alerts: [VillageAlert{severity, en, si, ta}], officer_report: OfficerReport{report} | null}` (`schemas.py`, done in GM-3).
   It **proposes** but does not execute.
4. **verifier** (Muse Spark, separate prompt, same evidence): `Verdict {decision: approve|revise|reject, reasons,
   aggression}`. It can't set a level. Checks: is it really an elephant, is it really approaching, does severity
   match evidence, do the alerts say where, how many and what to do, are all three languages present.
   `revise` → back to responder with the critique (max 2 rounds, then the guard decides). `reject` → the guard sends
   its template. **A verifier that fails or runs out of time counts as `reject`** (decided with the user, GM-8):
   unverified model text is never sent; the template goes out at the same level and time. Every model call has a
   timeout, and the whole model phase has a deadline (`MODEL_DEADLINE_S`) after which the guard decides.
5. **policy_guard** (deterministic, `policy.py`, done in GM-3; the main guardrail). Splits the proposal into
   `send_now` and `escalation` (invariant 2). "Approval" = the site has `requires_approval` on; otherwise nothing is
   held and the escalation is sent right after the warning.

   | Tracker risk | Max severity | `send_now` (exact level) | `escalation` (held for approval) |
   |---|---|---|---|
   | `low` | none | nothing (all alerts dropped) | – |
   | `medium` | `warning` | alert at `warning` | – |
   | `high` | `danger` | alert at `warning` (same text) + officer report | the `danger` re-broadcast, if the responder proposed `danger` |
   | `critical` | `danger` | alert at `danger` + officer report | – |

   - The guard **raises or lowers** the proposed alert to the `send_now` level: the tracker's risk sets the floor
     as well as the ceiling (decided with the user, GM-3).
   - **Silent responder:** at `medium` or above with no proposed alert, the guard sends a fixed template alert in
     English, Sinhala and Tamil (invariant 1). The Sinhala and Tamil templates need the native-speaker review in Phase 6.
   - **Officer report** at `high`/`critical` or on verifier-flagged aggression; if the responder wrote none, the guard
     sends a template report built from the tracker summary.
   - **Template escalation** (decided with the user, GM-8): at `high` risk on a site that requires approval, if no
     valid `danger` alert was proposed, the guard holds a template `danger` escalation for the officer instead of
     losing the escalation path. Like any held escalation, it is sent if the approval times out (invariant 9).
   - **Wording follows the level** (GM-25, found in the first real run): when the guard lowers or raises a proposed
     alert's severity, the model's text (written for another level, e.g. "DANGER: …" sent as a warning) is replaced by
     the template written for the level that is sent. The model's own text is kept where its level is allowed (the
     `danger` escalation at high risk, an alert at exactly the allowed level). The responder and verifier prompts also
     say to word each alert for its own severity; the verifier checks it.
   - Alert text is checked deterministically before anything is sent (`schemas.VillageAlert`): at most 320
     characters, no control characters, links or phone numbers, Sinhala text in `si`, Tamil text in `ta`.
   - Several proposed alerts: the most severe is kept, the rest dropped.
   - An unknown risk value raises an error; the guard never guesses a level.
   - Every raise, downgrade, template, drop or hold is recorded in `notes` (shown on the dashboard).
6. **dispatch** (`send_now`): MCP `send_village_alert` / `notify_wildlife_officer`, idempotency key
   `{analysis_id}:now:{i}`.
7. **approval_gate**: if `escalation` is non-empty → LangGraph `interrupt()`. The node does **nothing before
   `interrupt()`**, because LangGraph re-runs the whole node on resume; the *worker* publishes
   `approval_required` when `invoke` returns an interrupt. Resumed by `analysis.resume` carrying
   `{decision: approve|reject|timeout, officer, note}` (`Command(resume=...)`). `approve`/`timeout` → step 8;
   `reject` → drop the escalation, keep the audit (the warning already sent stands).
8. **dispatch** (`escalation`): same node, key `{analysis_id}:escalation:{i}`.
9. **finish**: publish `completed` with summary, assessment, verdict, guard notes, executed actions, usage.

### Cross-cutting
- **Tracing**: Langfuse callback on every LLM/tool call (Langfuse Cloud free tier, to avoid self-hosting
  ClickHouse). Langfuse v3 trace ids must be 32 lowercase hex chars: Spring generates `analysis_id` as a UUID and
  the trace id is that UUID without dashes, so logs, DB rows and traces share one id.
- **Retries/timeouts**: LLM calls with timeout + 2 retries; SAM call timeout; failures publish `failed` with reason.
- **Usage accounting**: tokens and wall time per node → stored for the cost/latency metric.

Exit: `cli.py` runs a clip end to end with stub tools, including a forced verifier `revise` loop, a `high`-risk
run where the warning is dispatched *before* the interrupt, and approve / reject / timeout resumes.

---

## Phase 3: Spring Boot backend (Java 21, Maven)
### Dependencies
web, validation, data-jpa, postgresql, flyway, security, amqp, actuator, `spring-ai-starter-mcp-server-webmvc`,
Twilio over `RestClient` (no SDK, GM-18), springdoc-openapi; tests: JUnit 5, Mockito, Testcontainers (Postgres, RabbitMQ).

### Packages (feature-based)
`site`, `analysis`, `alert`, `approval`, `audit`, `auth`, `messaging`, `mcp`, `sms`.

### Data model (Flyway migrations)
- `site` (name, villages, officer contact, mode `SHADOW|LIVE`, requires_approval, approval_timeout_s default 120)
- `subscriber` (site, phone, preferred language `EN|SI|TA`)
- `analysis` (id UUID, site, video path, boundary, status `QUEUED|RUNNING|AWAITING_APPROVAL|COMPLETED|FAILED`,
  risk (set by `record_assessment`), summary JSONB, result JSONB, timings, cost)
- `alert` (analysis, idempotency_key UNIQUE, stage `NOW|ESCALATION`, severity, en/si/ta text, shadow flag)
  + `alert_delivery` (alert, subscriber, status, provider id; UNIQUE (alert, subscriber))
- `officer_report` (analysis, idempotency_key UNIQUE, text)
- `approval_request` (analysis, proposed alert, status `PENDING|APPROVED|REJECTED|TIMED_OUT`, expires_at,
  decided_by, note, decided_at)
- `audit_log` (analysis, actor `AGENT|OFFICER|SYSTEM`, action, payload JSONB, timestamp)
- `app_user` (username, password hash, role `ADMIN|OFFICER`)
- `processed_event` (event id the events consumer has handled; at-least-once delivery)

**Data retention and deletion** (decided with the user, GM-10; enforced by a scheduled Spring job, not built yet):
- **Footage:** the video file is deleted 30 days after upload. The `analysis` row stays; `video_path` is cleared and
  `video_deleted_at` set (the database checks they change together).
- **Alerts, deliveries, officer reports, approvals, audit log, processed events and the analysis row:** kept 1 year,
  then deleted child-first (foreign keys to `analysis` are RESTRICT, so nothing disappears by accident).
- **Deleting a villager (subscriber):** their `alert_delivery` rows are kept for the audit trail with `subscriber_id`
  set to null, so no phone number remains. The service refuses the delete while any of their deliveries is `PENDING`,
  so a retry can't send twice to the same phone (invariant 4).
- Every `idempotency_key` must start with its own `analysis_id` (database check), and an `APPROVED` or `REJECTED`
  approval must name the officer (`decided_by`); `TIMED_OUT` has none.

### REST API
- `POST /api/analyses` (multipart: video, boundary, siteId): validate (6 numbers 0–1, MP4, size limit),
  store on volume, insert `QUEUED`, return `202 {id}`; publish `analysis.requests` **after commit**
  (`@TransactionalEventListener(AFTER_COMMIT)`), never inside the transaction. The scheduler republishes any
  `QUEUED` analysis older than 2 min, so a failed publish recovers (the worker's resume-from-checkpoint makes a
  duplicate harmless).
- `GET /api/analyses/{id}`, `GET /api/analyses?siteId=` (history)
- `GET /api/analyses/{id}/events`: **SSE** stream of progress/approval/completed events
- `GET /api/approvals?status=PENDING`, `POST /api/approvals/{id}` `{decision, note}` → publishes `analysis.resume`.
  The decision is a conditional update (`WHERE status = 'PENDING'`), so an officer click racing the timeout
  produces exactly one resume.
- Approval timeout: a `@Scheduled` job (every 10 s) moves expired `PENDING` requests to `TIMED_OUT` with the same
  conditional update and publishes `analysis.resume {decision: timeout}`. Spring owns time; the graph just waits.
  (One backend instance; add ShedLock if it is ever scaled out.)
- `/api/sites`, `/api/sites/{id}/subscribers`: CRUD (ADMIN)
- `GET /api/sites/{id}/incidents`: episodic history for the UI

### Messaging (Spring AMQP)
Queues: `analysis.requests`, `analysis.resume` (to Python), `analysis.events` (from Python). The events consumer
updates `analysis` status, writes `audit_log`, pushes to SSE emitters. Dead-letter queue for poison messages.
Events carry an `event_id`; the consumer ignores ones it has already stored, because RabbitMQ delivers at least once.

**Message contract, `schema_version` 1** (GM-20; JSON, persistent; fields may be added, renaming or removing one
needs a version bump on both sides):
- `analysis.requests` (Spring → worker): `{schema_version, analysis_id, site_id, video_path, boundary[6]}`.
- `analysis.resume` (Spring → worker): `{schema_version, analysis_id, decision: approve|reject|timeout, officer,
  note}`.
- `analysis.events` (worker → Spring): `{schema_version, event_id, analysis_id, type, …}` with `type`
  `started` | `approval_required` (+ `escalation`) | `completed` (+ `result`) | `failed` (+ `reason`).

**Built in GM-20** (`messaging`, `approval`, `analysis` packages), no HTTP yet:
- Queues are durable with a dead-letter queue `analysis.dlq`. A failing event is retried 5 times with backoff, then
  dead-lettered; never requeued forever, never dropped.
- The consumer stores `event_id` in `processed_event` in the same transaction as the effect. Status changes never
  move a finished analysis backwards (out-of-order events). `approval_required` opens one approval per analysis,
  expiring after the site's `approval_timeout_s`.
- Officer decision and timeout are conditional updates on `PENDING` (the timeout wins a tie); each publishes
  `analysis.resume` after commit. The analysis stays `AWAITING_APPROVAL` until the worker's next event.
- `MessagingRecovery` (every 60 s) republishes requests for analyses `QUEUED` over 2 minutes and resumes for approvals
  decided over 2 minutes ago whose analysis is still `AWAITING_APPROVAL`, so a publish lost after commit can't hold
  back an escalation (invariant 9). It also logs at ERROR any analysis `RUNNING` or `AWAITING_APPROVAL` with no
  progress for 45 minutes (a dead worker, or a message in `analysis.dlq`): loud, for a person to check.
- An unreadable event, or one missing its type's field (`escalation` for `approval_required`, `reason` for
  `failed`), goes straight to `analysis.dlq` without retries.
- **What the worker (GM-22) must do with duplicates** (decided in the GM-20 architecture review):
  - a request or resume for a thread **waiting at the approval gate** → resume it with the decision;
  - for a thread with **pending nodes** (crashed mid-run) → continue from the checkpoint (`ainvoke(None)`);
  - for a **finished** thread → publish `completed` (or `failed`) again from the stored state, never ignore it, so
    Spring's status always catches up;
  - after a resume it publishes no `started`; it publishes `completed` (also when the officer rejected) or `failed`;
  - `officer` and `note` in a resume may be null.

### MCP server (Spring AI)
Tools: `record_assessment`, `get_site_history`, `get_site_procedures`, `send_village_alert`, `notify_wildlife_officer`.
- Internal API key required (header, checked by a Spring Security filter on the MCP endpoint), not reachable from
  the public port in compose.
- **Built in GM-17** (`mcp.GajamitraTools`, Spring AI 2.0.1, streamable HTTP at `/mcp`): the five tools above, with
  the names and arguments of ai-service's `ToolClient`. The key is checked by `mcp.McpApiKeyFilter` (header
  `X-Api-Key`, constant-time compare, every call refused when `MCP_API_KEY` is unset) until Spring Security is added.
  Malformed arguments (bad UUID, unknown stage, invalid alert or report) are answered `rejected`, never thrown. An
  unknown site's procedures default to `requires_approval: true`; history is bounded (90 days, 50 rows) and carries
  no phones, video paths or alert text. The Python MCP client comes in Phase 4.
- `send_village_alert(analysis_id, idempotency_key, stage, alert)` with `stage` `NOW | ESCALATION` and the key
  `{analysis_id}:{now|escalation}:{index}` (the officer report uses `{analysis_id}:now:report`). Reject a key that
  doesn't belong to that analysis and stage. Key already stored → return the stored result, send nothing.
  Otherwise **re-check severity against the stored tracker risk** using the same table as `policy.py` (second
  guardrail): within `max_severity` and within the stage limit (`send_now_severity` for `NOW`,
  `escalation_severity` for `ESCALATION`); **no stored risk → reject** (invariant 3). An `ESCALATION` alert is
  accepted only if the analysis's `approval_request` is `APPROVED` or `TIMED_OUT`, or no approval is required
  (Spring owns approvals; this is the server-side half of invariants 2 and 9).
  Answers `{"status": "accepted"}` or `{"status": "rejected", "reason": …}`; the graph fails the run on a rejection. Then write `alert` + `audit_log` in one
  transaction, and deliver after commit: `SHADOW` site → log only; `LIVE` + flag on → Twilio to subscribers in
  their language, skipping deliveries already `SENT`; flag off → dashboard only.
- `notify_wildlife_officer` uses the same idempotency-key rule.
- **Built in GM-14** (`alert.AlertDispatchService`, `alert.AlertSender`): the logic behind `record_assessment`,
  `send_village_alert` and `notify_wildlife_officer`, without the MCP transport (next slice). Calls for one analysis
  are serialised by a row lock on `analysis`, so a repeated key finds the stored alert instead of racing to insert.
  `record_assessment` stores the risk once; the same risk again is a no-op, a different one is refused.
  SMS go out after commit through the `SmsGateway` port (`LoggingSmsGateway` until Twilio). Each delivery moves
  `PENDING → SENDING` (a conditional update, committed before the provider is called) and then `SENT` or `FAILED`
  (migration V2). Only the sender whose claim succeeded sends; a row left in `SENDING` means "maybe sent" and is never
  sent again (invariant 4). One failing delivery doesn't stop the others; failures are logged at ERROR with `analysisId`.
- **Delivery retry (GM-16, `alert.DeliveryRetryJob`)**: every 30 s it retries deliveries left `PENDING` (crash
  between commit and sending) or `FAILED` (provider refused) once they are 60 s old, with the same claim, up to 3
  provider calls per delivery in all (`attempts`, migration V3). Rows in `SENDING` for over 5 minutes are never resent
  ("maybe sent") and are logged at ERROR for a person to check.
- **Still to build** (GM-14 review):
  - sending off the MCP request thread, so even with the Twilio timeouts (GM-18) a large village list can't hold up
    the agent's tool call;
  - the escalation hold reads `hold_escalation` from the policy table instead of relying on `escalation_severity`
    being null for `critical`.

### SMS
`SmsGateway` with `TwilioSmsGateway` and `LoggingSmsGateway` (selected by `gajamitra.sms.enabled`).
Per-recipient language selection; Unicode segments; delivery status stored.
**Built in GM-18:** `TwilioSmsGateway` calls Twilio's Messages REST API with Spring's `RestClient` (connect 5 s, read
10 s) instead of the `com.twilio.sdk:twilio` library: one POST needs no SDK, and the timeouts are explicit. Any non-2xx
answer, timeout or missing message sid throws, so the delivery becomes `FAILED` and the retry job takes it. Enabling
SMS without all three `TWILIO_` values stops startup. `SENT` means Twilio accepted the message; delivery receipts
(status callbacks) are not built yet.

### Security
Spring Security session login (same-origin SPA), BCrypt, roles: OFFICER (view, approve), ADMIN (+ sites, subscribers).
CSRF stays on: `CookieCsrfTokenRepository.withHttpOnlyFalse()`, React sends the `X-XSRF-TOKEN` header on writes.
The MCP endpoint is excluded from CSRF (machine client, API-key filter instead).
Secrets only via environment variables. Upload size/type limits. CORS closed in prod.
**Built in GM-21** (`auth.SecurityConfiguration`): form login at `POST /api/login` (204 / 401, no redirects),
`POST /api/logout`; 401 for an unauthenticated API call; `/api/sites/**` ADMIN, the rest of `/api/**` OFFICER or
ADMIN, anything else denied. `/mcp` is outside the login and CSRF and keeps its API-key filter. The first ADMIN is
created from `GAJAMITRA_ADMIN_USERNAME` / `GAJAMITRA_ADMIN_PASSWORD` (12+ characters) only while there are no users.
Endpoints built: `POST /api/analyses` (multipart `video`, `siteId`, `boundary` "6 numbers"; MP4 checked by its
`ftyp` box, not the file name or MIME type; stored under a generated name in `GAJAMITRA_VIDEO_DIR`; 200 MB limit;
202 `{id}`), `GET /api/analyses/{id}` (no storage path), `GET /api/approvals` (pending),
`POST /api/approvals/{id}` `{decision: APPROVE|REJECT, note}` (204, or 409 if already decided or timed out).
Errors are `ProblemDetail`; only messages written for the client are returned. CSRF uses Spring Security's SPA mode;
session and XSRF cookies are Secure (unless `GAJAMITRA_SECURE_COOKIES=false` for plain-HTTP local runs) and
SameSite=Lax, with a 30-minute idle timeout. Five wrong passwords lock a username for 15 minutes (in memory, one
instance). A failed or rejected upload never leaves a file behind.
Officers see and decide approvals for every site (one pilot site; per-site scoping when there are several).
**Built in GM-23** (`site.SiteController`, ADMIN): `GET/POST /api/sites`, `PUT /api/sites/{id}` (name, villages,
officer contact, approval rule, `approvalTimeoutS` 30–1800 s so an escalation can't be held for long),
`PUT /api/sites/{id}/mode` (`SHADOW`/`LIVE`; new sites start in `SHADOW`), `GET/POST /api/sites/{id}/subscribers`
(E.164 phone, unique per site, language `EN|SI|TA`; a concurrent duplicate is 409),
`DELETE /api/sites/{id}/subscribers/{sid}` (refused while one of their SMS is `PENDING`, `SENDING` or `FAILED` with
retries left; their deliveries stay, anonymised, and are never claimed again). Every change is audited with the
admin's name, phones masked. Known limit: a villager removed in the same instant a sender claims their delivery can
still receive that one SMS. A site switched from `LIVE` to `SHADOW` still sends deliveries already created as
`PENDING` (the mode is fixed per alert when it is accepted).
**Built in GM-26** (read side for the dashboard, OFFICER or ADMIN): `GET /api/analyses?siteId=` (latest 50, newest
first, one site or all), `GET /api/analyses/{id}` now returns the outcome (`result`: risk, herd size, approval,
dispatched keys, guard and verifier notes), `failureReason`, the alerts that were sent (all three languages, shadow
flag, delivery counts per state, never phone numbers) and the officer report, and `GET /api/analyses/{id}/events`
(Server-Sent Events). The stream sends an event with no data (just a `kind`) on every change of status, risk, alert,
report or approval, only after the database commit; the page re-reads the analysis on each event, so it never acts on
a partial copy. Closed streams are removed on completion, timeout or error.
Still to build: keyframes for the approval screen; a per-user upload rate limit; clip duration is checked
by the AI service, not on upload; audit entries for logins.

### Observability
Actuator health/metrics; structured JSON logs with `analysisId` in MDC; the same id is the Langfuse trace id.

Exit: Testcontainers integration tests pass for upload → message published, events → status/SSE, approval → resume
message, MCP alert in SHADOW vs LIVE, guard rejecting an over-severe alert, alert rejected when no risk is stored,
same idempotency key twice → one alert row and one SMS, approval timeout → exactly one `timeout` resume.

---

## Phase 4: Wire Python to Spring
1. `worker.py`: aio-pika consumers for `analysis.requests` (start graph) and `analysis.resume` (resume thread
   `analysis_id`), publisher for `analysis.events`.
   - **Separate channels**, prefetch 1 each, so a long analysis never blocks an officer's resume.
   - **Ack when the graph yields**: at an interrupt or a terminal state, after the checkpoint is saved. An
     analysis waiting for approval holds no message. Nack → DLQ only on an unrecoverable error.
   - Request redelivered and the thread already has a checkpoint → resume it (`ainvoke(None, config)`), don't
     restart (invariant 5).
   - RabbitMQ `consumer_timeout` (default 30 min) must exceed the longest SAM call; set the SAM timeout below it.
2. Replace stub tools with an MCP client pointing at the Spring MCP endpoint (API key header).
   **Built in GM-19** (`gajamitra.mcp_client.McpToolClient`, `mcp` 2.3.0): implements the `ToolClient` port with the
   official MCP SDK rather than `langchain-mcp-adapters` (`MultiServerMCPClient` turns tools into LangChain tools for
   a model to call, but the graph calls `ToolClient` methods directly; the adapter also pins `mcp<2`). Settings
   `MCP_URL` (default `http://localhost:8080/mcp`) and `MCP_API_KEY`. Every call has a 30 s timeout; an error,
   timeout or malformed answer on a side-effect tool comes back as `rejected` (the graph then fails the run), and a
   failed read raises. One MCP session per call for now; keep one open once the worker is async.
3. LangGraph **`AsyncPostgresSaver`** (the worker is async; `PostgresSaver` is the sync variant), run `.setup()`
   once. Own schema via `?options=-csearch_path%3Dlanggraph` in the connection string, so its tables never mix
   with Flyway's.

**Built in GM-22** (`gajamitra/worker.py`, `python -m gajamitra.worker`; `aio-pika` 10.1.1,
`langgraph-checkpoint-postgres` 3.2.0, `psycopg` 3.3.6):
- The graph stays synchronous and runs in a thread (`asyncio.to_thread`), so the consumer loop is never blocked. It
  therefore uses the **sync `PostgresSaver`** rather than `AsyncPostgresSaver`, in its own `langgraph` schema
  (created on start).
- Two channels, prefetch 1 each. A message is acked after the graph yields and its event is published. A graph
  failure becomes a `failed` event (then acked); a failure to publish or to reach Postgres/RabbitMQ is retried after
  5 s (nack), so a finished run is never reported `failed` just because the broker blinked.
- Duplicates follow the GM-20 rules (resume at the gate, continue pending nodes, re-publish a finished outcome);
  one lock per analysis serialises a duplicate request and a resume.
- Messages off the contract (wrong version, missing fields, non-UUID id, bad boundary or decision) go to
  `analysis.dlq`.
- Known limit: a transient MCP or model error inside the graph ends the run as `failed` (loud, ERROR in Spring);
  Spring doesn't restart failed analyses yet.

Exit: upload via `curl` → events stream → approval via `curl` → SMS appears in shadow log / Twilio.

---

## Phase 5: React + TypeScript dashboard
Pages:
1. **Login**
2. **New analysis**: site picker, upload, boundary picking (port the `canvas.onclick` 3-click logic from `index.html`)
3. **Live analysis**: progress timeline from SSE (SAM → tracker → responder → verifier → guard → approval → sent);
   video with overlay (port `draw()`: boxes, trails, predicted path); risk badge, herd, behaviour, verifier verdict,
   guard downgrades, alerts in 3 languages, shadow/live badge
4. **Approvals queue** (officer): evidence keyframes + proposal + verifier reasons + countdown to auto-send
   (`expires_at`) → Approve / Reject with note
5. **Incident history** per site (episodic memory, visible)
6. **Admin**: sites (mode, approval rule), subscribers

Built by Vite and served from Spring Boot `static/` in the production image (same origin, no CORS).

**Build order and decisions** (agreed with the user): GM-26 backend read API (history, detail, SSE) → **GM-27
foundation** → GM-28 pages (new analysis with boundary picker, live analysis, approvals queue, history, admin) →
evidence keyframes for the approval screen (the worker writes small JPEGs to the shared volume, Spring serves them to
signed-in users, deleted with the footage after 30 days).
- **Built in GM-27:** `react-router-dom` (routing), `vitest` + `@testing-library/react` + `user-event` + `jest-dom` +
  `jsdom` (tests, run with `npm test`); no UI kit and no data-fetching library: `fetch` plus small hooks.
  - `src/api/client.ts`: the one place that calls the backend (same origin; Vite proxies `/api` to `127.0.0.1:8080` in
    development, so run that backend with `GAJAMITRA_SECURE_COOKIES=false`). It sends the session cookie and the
    CSRF token (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header) on writes, and reports an expired session. `src/api/parse.ts`
    checks every response at runtime (TypeScript types say nothing about what the network returned); an unexpected
    shape is an error state, never half-trusted data.
  - `GET /api/me` (backend) tells the page who is signed in. Pages shown or hidden by role are only convenience:
    every endpoint still checks the role.
  - Login page (401 wrong credentials, 429 locked, 0 server unreachable each get their own message; one attempt at
    a time), a signed-in shell (header, navigation, language switch, theme toggle, sign out) and the analyses list
    with deliberate loading, empty, error (with retry) and expired-session states.
  - **English, Sinhala and Tamil from the start**: a typed dictionary (`i18n/messages.ts`; the three languages must
    define the same keys or the build fails), saved choice then browser language, `<html lang>` kept in step, dates and
    times formatted for Sri Lanka, language names in their own script. The Sinhala and Tamil wording is a first
    draft: **a native speaker must review it before real use.**
  - Model-written and worker-written text (alert text, failure reasons, notes) is always rendered as plain text.

---

## Phase 6: Evals and success metrics
1. `evals/cases.yaml`: ~15 labelled cases: approaching herd, calm herd far away, herd standing at the boundary,
   flipped village side, no elephants (cattle/empty road), tabletop toy toward/away. Each: clip, boundary,
   expected risk, expected action set.
2. `evals/run.py`: runs the graph on cached SAM output, dispatch in shadow mode, approvals auto-approved.
   With ~15 cases one case is ~7 percentage points, so report **counts** ("13/15"), not percentages. Reports:
   - **Task success rate**: risk level matches label
   - **Tool-call accuracy**: executed action set matches expected (none / alert / alert + officer)
   - **Human escalation rate**: % of runs needing approval
   - **Cost and latency per task**: tokens × price, wall time per clip
   - **False alarms**: alerts on no-elephant cases (target 0)
   - **Verifier impact**: number of proposals it revised/rejected, and whether that was correct
3. **LLM-as-judge** (DeepEval GEval, custom `DeepEvalBaseLLM` wrapper on the Meta API): alert quality (location,
   count, instruction, brevity). Judge = **Muse Wonder**, not Muse Spark: Spark writes and verifies the alerts, and a
   model grading its own output is biased toward it. Spot-check 5 judgements by hand and report agreement.
4. Native-speaker check of Sinhala and Tamil alerts, including the guard's template alerts in `policy.py` (manual, recorded in `evals/translation_review.md`).
5. Results table generated into README.

---

## Phase 7: DevOps
1. Dockerfiles: `backend` (multi-stage: Node builds frontend → Maven builds jar → JRE 21 runtime),
   `ai-service` (python:3.11-slim + opencv-headless).
2. `docker-compose.yml`: postgres, rabbitmq (management UI), backend, ai-service; shared `videos` volume;
   healthchecks; `.env.example` with every variable.
3. GitHub Actions `ci.yml`: backend `mvn verify` (Testcontainers), ai-service `ruff` + `pytest`,
   frontend `tsc` + build. Evals as a manual `workflow_dispatch` job (it spends API credits).
4. Deployment: one cloud VM (e.g. AWS EC2/Lightsail) running compose behind HTTPS (Caddy). Documented in README.

**Built in GM-24** (steps 1–2 without the frontend and Caddy): `backend/Dockerfile` (Maven build → JRE 21, non-root,
built from the repo root so the shared policy table is included), `ai-service/Dockerfile` (python:3.11-slim,
editable install so `policy.py` finds `/app/policy`, non-root, runs `python -m gajamitra.worker`),
`.dockerignore` (keeps `.env`, data and build output out of images), and `docker-compose.yml` with `backend` and
`worker` services: healthcheck-ordered start, a shared `videos` volume (read-only for the worker), all ports on
127.0.0.1. The backend has a health check (`/actuator/health`) and the worker starts only once it is healthy, so
the queues exist. Both containers drop all capabilities and set `no-new-privileges`; the worker's crash loop stops
after 5 restarts. The worker takes the broker and database host, user and password as separate settings, never as a
URL (special characters in a password can't break it and no URL with a password is built or logged), and logs an
ERROR at start when `MODEL_API_KEY` is missing. Known follow-ups from the security review: pin the base images to
exact versions, add memory and CPU limits, and persist the SAM cache across restarts.
Smoke-tested with throwaway credentials: login → upload → `analysis.requests` → worker `started` → SAM fails without
an API key → `failed` → analysis `FAILED`, all audited (no credits spent). Still to add: the frontend stage and Caddy
(port 443 only, `/mcp` not routed).

---

## Phase 8: Hackathon submission
1. README: problem and impact, architecture diagram, Meta models table (SAM 3.1, Muse Spark, Muse Wonder as judge), agent design
   (responder/verifier/guard/approval), metrics table from Phase 6, **"Built during the hackathon"** section
   (prototype commit → final), footage credits, run instructions, **Path to production** (below).
2. 3-minute demo video: problem (20 s) → upload + boundary → live progress timeline → verifier catches an
   over-reaction → guard note → warning SMS arrives in Sinhala at once → officer approves the escalation → danger
   SMS → metrics table → code tour (15 s).
3. Public repo check (private window), video link public/unlisted, team listed, problem statement selected
   (Build for Your World), submit on Cerebral Valley.
4. Optional: X post of the demo for Most Viral; flag for Best Agentic Use-Case.

## Phase 9: Path to production (documented, not built)
Shadow-mode pilot with DWC rangers at one site (log what it would send, compare with real events) → RTSP live
camera streams → IR/thermal night cameras → metric distances via camera calibration → Sri Lankan SMS/voice
gateways (Dialog, Mobitel) → S3 video storage → edge inference on Muse Glimmer / Muse Spark Open for low-connectivity
sites → horizontal scaling of AI workers.

---

## Reused code (don't rewrite)
- `track.py`: `parse_sam`, `tracks`, `velocity`, `assess` (with the `NEAR_BODIES` proximity rule): used as-is by
  the **perceive** node and as the source of truth for the guard.
- `agent.py`: `segment_video`, `keyframes`, lazy `meta()` client → `perception.py` unchanged.
- `agent.py` `SYSTEM` prompt: behaviour definitions reused in the responder prompt (already hardware-free).
- `index.html`: `canvas.onclick` boundary picking and `draw()` overlay logic ported to React components.
- `test_track.py`: moved into `tests/`, still the tracking self-check.

## Verification
- **Unit**: `pytest` (track maths incl. a stationary herd at the boundary → `medium`; policy guard table: each
  risk × severity → `send_now` / `escalation` split; graph routing with a fake chat model: verifier revise loop,
  reject, warning dispatched before the interrupt, approve / reject / timeout resume, `critical` never interrupts);
  JUnit (services, guard re-check matching `policy.py`'s table, idempotent tools, SMS selection).
- **Shared guard table**: `policy.py` and the Java re-check read one `policy_table.json` (risk → max severity,
  hold rule), and both test suites assert against it, so the two guardrails can't drift apart.
- **Integration**: Testcontainers (Postgres + RabbitMQ) for the backend flows listed in Phase 3's exit, plus a
  worker test: kill the worker after `dispatch` → redelivery resumes from the checkpoint, no second SMS.
- **End to end**: `docker compose up` → log in → upload an approaching-herd clip → watch SSE timeline →
  approve in the queue → alert appears on dashboard and (LIVE site, flag on) on a verified phone in Sinhala/Tamil.
  Repeat with: flipped boundary (no/lower alert), calm herd (no action), herd standing at the boundary (warning),
  no-elephant clip (no alert), SHADOW site (logged, not sent), approval left untouched (danger auto-sent at
  `approval_timeout_s`).
- **Evals**: `python -m evals.run` produces the metrics table; false alarms = 0 on negative cases.
- **CI** green on push.
