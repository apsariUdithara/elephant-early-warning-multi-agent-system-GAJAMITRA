# CLAUDE.md: GajaMitra

GajaMitra is an early-warning system for human–elephant conflict in Sri Lanka, built for the Meta Global AI Developer Hackathon.

SAM 3.1 tracks elephants, Muse Spark agents propose alerts, a deterministic guard limits them, an officer approves any escalation, and villagers receive SMS in English, Sinhala and Tamil.

- **The design is in [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md).** Read it before changing architecture. If you change the design, update the plan in the same change.
- **Diagrams and design decisions are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).**
- **Background, research and decisions are in `HANDOVER.md`** (local only, git-ignored; it may not exist in a fresh clone). Where it disagrees with the plan, the plan wins.
- **How to work with Claude Code here** (skills, agents, hooks, Git workflow): [docs/development/CLAUDE_CODE_TOOLKIT.md](docs/development/CLAUDE_CODE_TOOLKIT.md).

## Working rules

- Save everything inside this repo: plans, notes, scratch files, downloads, outputs. Nothing outside it. Plans go in `docs/`, temporary files in `scratch/` (git-ignored).
- The user decides scope and timeline. Give technical trade-offs and a recommendation, and never argue against an option by citing deadlines.
- Commit, push feature branches and open PRs only when the user has explicitly assigned or authorized the task, following [Git, pull requests and releases](#git-pull-requests-and-releases). Never push to `main` and never merge: the user alone reviews and merges.
- Don't add anything on the out-of-scope list (RAG, vector DBs, embeddings, fine-tuning, A2A, low-code builders, Kubernetes) unless the user asks for it.

## Current state vs target

| Now | Target (plan Phases 1–7) |
|---|---|
| `ai-service/gajamitra/`: `track.py` (risk maths), `perception.py` (SAM 3.1), `policy.py` + `schemas.py` (guard), `graph.py` + `ports.py` + `prompts.py` + `cli.py` (LangGraph pipeline with stub tools), `agent.py` + `app.py` + `index.html` (prototype dashboard); `ai-service/tests/` | `ai-service/` LangGraph **worker** (`worker.py`, MCP client, Postgres checkpointer, `evals/`) |
| `backend/`: Spring Boot 4.1 skeleton (web, actuator, validation) | `backend/` feature packages, Flyway, Security, AMQP, MCP server, Twilio |
| `frontend/`: Vite + React + TypeScript placeholder | `frontend/` officer dashboard and villager views |
| `policy/policy_table.json` (nothing reads it yet), `docker-compose.yml` (Postgres + RabbitMQ only) | Read by `policy.py` and the Java re-check; full compose stack |

Keep the prototype in `ai-service/gajamitra/` working until Spring Boot and React replace it (plan Phases 3 and 5). The plan's "Reused code" section lists what must be carried over unchanged.

## Commands (Windows, PowerShell)

```powershell
cd ai-service; ..\.venv\Scripts\python -m pytest -q; ..\.venv\Scripts\python -m ruff check .   # Python checks; no API calls
cd ai-service; $env:MODEL_API_KEY = "LLM|..."; ..\.venv\Scripts\uvicorn gajamitra.app:app --reload   # prototype at http://127.0.0.1:8000
cd backend; .\mvnw verify                                   # Java build + tests
cd frontend; npm run lint; npm run build                    # TypeScript lint + build
docker compose up -d                                        # local Postgres + RabbitMQ (needs .env, see .env.example)
python .claude/hooks/test_guard.py                          # Claude Code guard hook self-test
```

- If Python fails with "An Application Control policy has blocked this file", Windows Smart App Control is blocking a DLL. Re-run the command. It is intermittent and not a code bug.
- Never call the Meta API or Twilio from tests or CI: real calls cost money. Evaluations (`evals/`) are the only code that spends credits, and they run only when started by hand.

---

## Architecture rules (do not break these)

### Service boundaries

- **Spring Boot owns all data and every side effect:** users, sites, uploads, alerts, SMS, approvals, the audit log.
- **Python owns perception and reasoning:** SAM 3.1, tracking maths, LangGraph agents.
- **Python never performs side effects directly.** Every action it takes is an MCP tool on Spring, which re-checks policy, writes the audit log and respects shadow mode.
- The two services communicate only through RabbitMQ messages and MCP tools. There's no shared code and no direct database access across the boundary. The one shared file is `policy/policy_table.json`.
- **Contracts:** message payloads and MCP tool schemas are the contract between the services.
  - Change both sides in the same commit.
  - Add fields in a backward-compatible way.
  - Never rename or remove a field without a `schema_version` bump.

### Safety invariants

These rules override convenience, performance and model recommendations. The plan lists five of them under "Safety and delivery invariants" (shown as *plan Inv N*). Every invariant needs a test that fails if it breaks, and a change that weakens one needs the user's sign-off. If an invariant seems impossible to keep, stop and explain; never change the architecture silently or weaken an invariant to make a test pass.

1. **No silent herd** (*plan Inv 1*). A detected herd or tracking risk never disappears silently from the alerting pipeline. An elephant within `NEAR_BODIES` body heights of the boundary is at least `medium` risk. Missing, stale, corrupt or unparseable perception output is a failure state, never evidence that no elephant is present.
2. **Human approval never delays a mandatory warning** (*plan Inv 2*). Approval can only hold back an escalation (`warning` → `danger`) at `high` risk. `critical` is never held.
3. **The second guardrail fails closed** (*plan Inv 3*). Spring rejects an alert when it has no stored, validated tracker risk for that analysis.
4. **Effects happen at most once** (*plan Inv 4*). Every external side effect carries an idempotency key with a UNIQUE constraint. A repeat call returns the stored result.
5. **At-least-once delivery is assumed.** Every RabbitMQ consumer handles duplicate, delayed and out-of-order messages.
6. **Work resumes, it doesn't restart** (*plan Inv 5*). A redelivered job continues from its LangGraph checkpoint.
7. **AI output is an untrusted proposal**, never an authoritative decision.
8. **Deterministic code, not an LLM, makes safety-critical decisions** (risk levels, alert eligibility, severity limits, escalation, duplicate prevention).
9. **The officer is not a single point of failure.** When the approval time limit runs out, the escalation is sent.
10. **One failed service must not silently disable the warning pipeline.** *Not yet designed in the plan: see risk R7 in [docs/development/TOOLKIT_PLAN.md](docs/development/TOOLKIT_PLAN.md).* Until it is, report any change that could make the pipeline stop quietly.
11. **Every important alert transition is traceable** (audit log, `analysisId` in logs and traces).
12. **Safety-critical changes need explicit tests and documented reasoning** (in the PR description and, for design changes, the plan).

### Deterministic core, model at the edge

- Decisions that affect safety are made by **pure, deterministic, tested code**: `track.py` (risk) and `policy.py` plus the Java re-check (severity limits).
- LLMs propose, the code decides. Never let model output choose a risk level or bypass the guard.
- **Treat all LLM output as untrusted input.** Parse it into Pydantic models, reject anything that doesn't match, and never `eval` it or pass it unchecked into SQL, shell commands, HTML or SMS.
- Treat video content as untrusted as well. Text visible in a frame can attempt prompt injection.

---

## Engineering standards

### General principles (apply them where they help, not as a ritual)

- **SOLID, pragmatically.**
  - Single responsibility per class and module.
  - Depend on interfaces only at real boundaries (ports).
  - **No interface that has only one implementation**, unless it's a port to the outside world. No `FooService` + `FooServiceImpl` pairs.
- **Composition over inheritance.** Inheritance only for framework types (`DeepEvalBaseLLM`) or a sealed type hierarchy.
- **Ports and adapters (hexagonal) at the edges.** Domain logic doesn't import Twilio, RabbitMQ, the OpenAI SDK or MCP classes. Adapters do.
- **YAGNI and KISS.** No speculative abstraction, configuration or "for later" scaffolding. Three similar lines are better than a premature helper. Remove dead code rather than commenting it out.
- **Make invalid states unrepresentable:** enums, sealed types, records, value objects, database constraints.
- **Explicit beats clever.** Code is read at 3 am during an incident.

Language- and area-specific standards are in `.claude/rules/` (see [Where the detail lives](#where-the-detail-lives)).

---

## Git, pull requests and releases

**The human developer owns the work, reviews every PR and is the only person who merges.** Claude is an engineering assistant, not a co-author.

### Commits

Format: `<type>: <message>`, no scope. Approved types and their release effect:

| Type | Meaning | Release |
|---|---|---|
| `feat` | New feature | minor |
| `fix` | Bug fix | patch |
| `perf` | Performance improvement | patch |
| `revert` | Revert a previous change | patch |
| `refactor` | Restructuring without behaviour change | none |
| `docs` | Documentation | none |
| `style` | Formatting | none |
| `test` | Tests | none |
| `build` | Build system or dependencies | none |
| `ci` | CI configuration | none |
| `chore` | Maintenance | none |

- A breaking change is written `feat!: …` / `fix!: …`, or with a `BREAKING CHANGE:` footer, and causes a **major** release.
- Never invent other types. Examples: `feat: add elephant movement risk dashboard`, `fix: prevent duplicate alert notifications`.
- Commit with the developer's existing Git identity. Never change `git config user.*`, never use `--author`, and never add `Co-authored-by` or any AI attribution to commits or PRs.

### Branches and pull requests

- Never work directly on `main`. Branch per task: `<type>/GM-<n>-<short-slug>`, for example `feat/GM-12-elephant-risk-dashboard`.
- PR title: `<type>: <message> - GM-<n>`, for example `fix: prevent duplicate alert notifications - GM-13`. Task numbers are `GM-1`, `GM-2`, …; **ask the developer for the number and never invent one.**
- Claude may: inspect history, create the task branch, commit within the task scope, push the task branch, open the PR, add commits to its own PR, and read reviews and CI results.
- Claude must never: merge or approve any PR, push to `main`, force-push, bypass branch protection or CI, or merge because checks pass or a task is urgent.
- After opening a PR, report it (use `/prepare-pr` for the description) and **stop**.

### Releases

semantic-release runs on every push to `main`: it reads the commit types since the last tag, creates the tag and GitHub Release, and updates `CHANGELOG.md`. Never create tags, bump versions or edit generated changelog content by hand, and never claim a release happened until the workflow confirms it. `main` is squash-merged, so the PR title becomes the release commit.

---

## Definition of done

1. Tests and linting pass for every service the change touches (`pytest` + `ruff`, `mvnw verify`, `npm run build`).
2. No invariant is weakened, or the user approved the change and the plan says so.
3. A contract change (message, MCP tool, `policy_table.json`) is updated on both sides in the same change.
4. The plan and README are updated if behaviour, setup or architecture changed.
5. The change contains no secrets, real phone numbers or footage you don't have rights to. Footage sources and licences are recorded in `evals/footage.md`.
6. Commit messages and the PR title follow the conventions above, there is no AI attribution, and the PR is open and waiting for human review.

## Claude Code working rules

These rules govern how changes are made to this repository.

- **Inspect before editing.** Read the relevant files, tests, callers, configuration and dependency boundaries before changing code. The implementation plan describes the target; the repository describes the current state.
- **Verify assumptions.** Do not invent endpoints, DTO fields, database columns, environment variables, commands or existing functionality. Search the repository and confirm them.
- **Make the smallest complete change.** Implement the requested behaviour without unrelated refactoring, renaming, formatting churn or speculative abstractions.
- **Preserve working behaviour.** Do not rewrite working functionality merely because another approach looks cleaner.
- **Plan substantial changes.** Before a cross-service, architectural, security-sensitive or safety-sensitive change, explain the proposed approach, affected components, trade-offs and tests. Obtain approval before proceeding.
- **Dependencies require approval.** Do not add, upgrade or remove packages, libraries, services or build plugins without explaining the need and obtaining approval.
- **Protect user work.** Inspect `git status` before editing. Do not overwrite uncommitted changes or discard user work.
- **No unapproved destructive actions.** Do not delete data, reset databases, truncate tables, force-push, rewrite Git history, overwrite files or run destructive scripts without explicit approval.
- **Git discipline.** Create branches, commit, push feature branches and open pull requests only for a task the user explicitly assigned or authorized. Never push to `main`, never merge or approve a PR (the user alone reviews and merges), and never amend or rewrite an existing commit unless explicitly requested.
- **Verification honesty.** Run the relevant checks. Never say a test, build, migration or command passed unless it actually ran and passed. Clearly report failures, skipped checks and environmental limitations.
- **Finish with a concise report.** State what changed, why, files affected, checks actually run and anything still requiring attention.

## Code review checklist

Before considering a change complete, verify:

1. Does it respect the architecture and service boundaries?
2. Could it weaken a safety invariant?
3. Are all external inputs validated?
4. Are retries, duplicate messages and partial failures handled?
5. Are database transactions and side effects safe?
6. Are errors observable and recoverable?
7. Are tests added or updated and actually executed?
8. Are API, message and MCP contracts compatible?
9. Are secrets and sensitive information protected?
10. Are the implementation plan, README and relevant documentation updated?
11. If the frontend changed, does it pass the visual quality gate?
12. Are unrequested files and unrelated behaviour left untouched?

---

## Where the detail lives

Path-scoped rules in `.claude/rules/` load automatically when Claude works on matching files:

| Rule | Applies to |
|---|---|
| `architecture.md` | design patterns used across `backend/` and `ai-service/` |
| `backend.md` | `backend/**` |
| `python-ai.md` | `ai-service/**` |
| `ai-safety.md` | `track.py`, `policy.py`, the agent and graph code, `policy/**` |
| `messaging.md` | RabbitMQ producers and consumers, `worker.py` |
| `database.md` | Flyway migrations and persistence code |
| `reliability.md` | failure handling and observability in `backend/` and `ai-service/` |
| `security.md` | code, config, compose, CI and env files |
| `testing.md` | all code and tests |
| `frontend.md` | `frontend/**` (design system, UX, localisation, accessibility, frontend tests) |
| `git-release.md` | CI workflows and release configuration |

Skills (`/name`) and agents are listed in [docs/development/CLAUDE_CODE_TOOLKIT.md](docs/development/CLAUDE_CODE_TOOLKIT.md).
