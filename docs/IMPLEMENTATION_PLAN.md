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
`com.twilio.sdk:twilio`, springdoc-openapi; tests: JUnit 5, Mockito, Testcontainers (Postgres, RabbitMQ).

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

### MCP server (Spring AI)
Tools: `record_assessment`, `get_site_history`, `get_site_procedures`, `send_village_alert`, `notify_wildlife_officer`.
- Internal API key required (header, checked by a Spring Security filter on the MCP endpoint), not reachable from
  the public port in compose.
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

### SMS
`SmsGateway` with `TwilioSmsGateway` and `LoggingSmsGateway` (selected by `gajamitra.sms.enabled`).
Per-recipient language selection; Unicode segments; delivery status stored.

### Security
Spring Security session login (same-origin SPA), BCrypt, roles: OFFICER (view, approve), ADMIN (+ sites, subscribers).
CSRF stays on: `CookieCsrfTokenRepository.withHttpOnlyFalse()`, React sends the `X-XSRF-TOKEN` header on writes.
The MCP endpoint is excluded from CSRF (machine client, API-key filter instead).
Secrets only via environment variables. Upload size/type limits. CORS closed in prod.

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
2. Replace stub tools with `MultiServerMCPClient` pointing at the Spring MCP endpoint (API key header).
3. LangGraph **`AsyncPostgresSaver`** (the worker is async; `PostgresSaver` is the sync variant), run `.setup()`
   once. Own schema via `?options=-csearch_path%3Dlanggraph` in the connection string, so its tables never mix
   with Flyway's.

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
