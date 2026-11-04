# GajaMitra · Claude Code engineering toolkit: Phase 1 assessment and implementation plan


## Context
You want Claude Code to behave as a disciplined engineering assistant on GajaMitra: it should follow the architecture,
protect the safety invariants, follow the team's Git workflow (`<type>: <message>` commits, `<type>: <message> - <Task>`
PR titles), open PRs but never merge them, and leave out AI co-author attribution. Releases should be automated with
semantic-release and a CHANGELOG.md. This document is the Phase 1 assessment, done with read-only inspection only.
Nothing in the repo has been changed.

---

## 1. Repository architecture summary (what actually exists today)

| Area | Reality on disk | Target (docs/IMPLEMENTATION_PLAN.md) |
|---|---|---|
| `ai-service/` (Python 3.11) | The prototype as a package: `gajamitra/track.py` (deterministic risk maths), `perception.py` (SAM 3.1 client `meta()`, `segment_video`, `keyframes`), `agent.py` (single Muse Spark tool loop; tools are simulated), `app.py` (FastAPI `/analyze`) and `index.html`. `tests/test_track.py` holds one pytest test. Ruff is configured (line length 120). | LangGraph graph, `policy.py`, `worker.py`, `schemas.py`, `prompts.py`, `cli.py`, `evals/` |
| `backend/` | Initializr skeleton, Spring Boot **4.1.2**, Java 21: web, actuator, validation. One `contextLoads` test. | JPA/Flyway/Security/AMQP/MCP server/Twilio, feature packages |
| `frontend/` | Vite + React 19 + TypeScript 7, oxlint, a placeholder `App.tsx`. **No test framework.** | 6 pages, design tokens, i18n |
| Messaging / DB / MCP | **Not yet implemented.** `docker-compose.yml` runs Postgres 17 and RabbitMQ 4 for local development only. | Queues `analysis.requests/resume/events`, DLQ, Flyway schema, 5 MCP tools |
| `policy/policy_table.json` | Exists with the risk → severity table. **Nothing reads it yet.** | Read by `policy.py` and the Java re-check |
| Auth, logging, monitoring | None yet. The prototype `app.py` has no authentication (local use only). | Spring Security sessions + CSRF, JSON logs with MDC, Actuator, Langfuse |
| Docs | `README.md`, `docs/ARCHITECTURE.md` + 7 SVGs, `docs/IMPLEMENTATION_PLAN.md` (10 phases), `HANDOVER.md` (git-ignored, local only) | — |

Conclusion: the toolkit has to cover both the code that exists now and the planned design. Rules and Skills will
point at real paths (`ai-service/**`, `backend/**`, `frontend/**`, `policy/**`). Planned paths (Flyway migrations, the
messaging packages) are marked "planned (IMPLEMENTATION_PLAN Phase N)" rather than presented as if they existed.

## 2. Existing Claude Code configuration

| Item | State |
|---|---|
| Root `CLAUDE.md` | 427 lines, **untracked** (it would be committed on the next `git add -A`). It is thorough, but it is too long to load in full every session: the ~170-line frontend/design section is a good candidate to move into a path-scoped rule. |
| `.claude/` in the repo | **Doesn't exist.** No settings, rules, skills, agents or hooks. |
| `.mcp.json` | None |
| User level (`~/.claude`) | `settings.json`: model opus, ponytail plugin enabled. That plugin injects "minimal code" rules via a SessionStart hook, which fits YAGNI. User skills: graphify, tech-resume. No user hooks of your own. |
| Built-in skills already available | `/code-review`, `/security-review`, `/simplify`, `/init`, `/run`. New skills must not duplicate these; ours add the GajaMitra-specific checklists. |

**Inconsistencies in CLAUDE.md (to fix in Phase 2):**
1. The "Current state vs target" section and the commands still describe the prototype as being in the repo root. It is now in `ai-service/gajamitra/`.
2. It says "Spring Boot 3.x"; the project uses 4.1.2.
3. It says "Conventional Commits (`feat(ai-service): …`)" with scopes. Your convention is `<type>: <message>` with **no scope**.
4. It says "Commit only when the user asks". Your Part 17.4 lets Claude commit, push and open PRs within an assigned task. The new wording will be: allowed as part of an assigned task; never merge; never push to `main`.
5. It links `HANDOVER.md`, which is git-ignored, so the link is broken in the public repo.
6. It lists 5 invariants; your prompt lists 12. They overlap (see §4). They will be merged into one numbered list.

## 3. Git and release automation

| Capability | State | Evidence |
|---|---|---|
| CI checks | ✅ `.github/workflows/ci.yml`: ai-service (ruff + pytest), backend (`mvnw verify`), frontend (lint + build). The latest run on `main` succeeded. | GitHub API |
| CI hardening | ⚠️ Actions are pinned to major tags (`@v5`), not SHAs. There is no `permissions:` block (it relies on the repo's default token scope) and no `concurrency`. | ci.yml |
| Commit message validation | ❌ None | — |
| PR title validation | ❌ None | — |
| Branch protection on `main` | ❌ `"protected": false` | GitHub API |
| semantic-release / tags / CHANGELOG | ❌ None. No `.releaserc`, no root `package.json`, **no tags**, no CHANGELOG.md | repo + `git ls-remote --tags` |
| Local git hooks | None (`.git/hooks` has samples only) | — |
| Git identity | `Apsari Udithara <udithara169@gmail.com>`; the existing commit uses it, with no AI trailer | `git log` |
| History | 1 commit, `Initial project structure: …`. It isn't in `<type>:` format; semantic-release will ignore it (no release). | — |
| Working tree | `.gitignore` modified (adds `HANDOVER.md`), `CLAUDE.md` untracked | `git status` |
| GitHub CLI | ❌ `gh` isn't installed, so Claude can't open PRs yet | — |

**Two facts that shape the design:**
- **GitHub can't tell Claude apart from you.** Claude runs `git push` with your credentials. Branch protection
  protects `main` from accidents, but "Claude never merges or pushes to main" has to be enforced **locally**:
  deny rules plus a PreToolUse hook. Both are in the plan.
- **semantic-release writes to `main`** (the CHANGELOG commit plus a tag). Once `main` is protected, the release job needs an
  identity that is allowed to bypass the protection. That is blocking question Q3.

## 4. Safety-critical components and risks

**Code that is already safety-relevant:**
- `ai-service/gajamitra/track.py`: `assess` (risk levels, the `NEAR_BODIES` floor), `parse_sam`.
- `policy/policy_table.json`: the shared guard table.
- `ai-service/gajamitra/agent.py`: lets the LLM call tools directly with no guard. It is a prototype and the tools are
  simulated, but it is the pattern the plan replaces.

**Risks found in the current code (reported, not fixed: they're outside the toolkit scope):**

| # | Risk | Where | Invariant |
|---|---|---|---|
| R1 | **A SAM output-format mismatch reads as "no elephants".** If `parse_sam`'s regex matches nothing (format change, empty stream, error text), `analyze` returns risk `low` with "No elephants detected". A perception failure becomes a silent herd. | `track.parse_sam`, `agent.analyze` | 1, 10 (and the CLAUDE.md rule "missing frames ≠ no elephant") |
| R2 | An elephant that left the frame early is assessed from its last stale position, with no "lost track" state | `track.assess` uses `tr[-1]` | 1 |
| R3 | LLM tool arguments aren't validated: `ACTIONS[name](**args)` raises on unexpected keys, so the whole analysis fails with a 500 | `agent.run_agent` | 7 |
| R4 | The model's final JSON is parsed by stripping fences, with no schema | `agent.run_agent` | 7 |
| R5 | `policy_table.json` has no reader or test yet, so drift can't be detected | `policy/` | 8 |
| R6 | The prototype `/analyze` has no auth or upload size limit (local only, but the repo is public) | `app.py` | security |
| R7 | The plan has **no explicit "pipeline down" alarm**. If ai-service or RabbitMQ is down, analyses sit in `QUEUED` and the stale-republish only retries. Nobody is told that warnings have stopped. | IMPLEMENTATION_PLAN | **10 (gap in the plan)** |

**How your 12 invariants map onto the plan's 5:** 1→Inv1 · 2,9→Inv2 · 3→Inv3 · 4→Inv4 · 5→Inv4 + the messaging
rules · 6→Inv5 · 7,8→"deterministic core" · 10→**not covered (R7)** · 11→audit_log/traces (planned) · 12→testing rule.
I recommend one merged list of 12 in CLAUDE.md. Closing R7 is a design change to IMPLEMENTATION_PLAN that needs your
sign-off, so it's listed as a follow-up, not part of this toolkit work.

## 5. Missing toolkit components
All of it: `.claude/settings.json` (permissions, attribution off, hooks), `.claude/rules/`, `.claude/skills/`,
`.claude/agents/`, hook scripts, commitlint + PR-title validation, semantic-release + CHANGELOG, branch-protection
guidance, toolkit docs, and `gh` for PRs.

---

## 6. Implementation plan (all work on one branch, one PR, opened by Claude and merged by you)

**Branch:** `chore/GM-1-claude-code-toolkit`, cut from `main` *after* you've dealt with the two uncommitted changes
(`.gitignore`, `CLAUDE.md`). I won't touch them without asking; Phase 2 will propose committing CLAUDE.md on this branch.
Commits use your identity (`Apsari Udithara`), with no trailers.
**Step 0 (before writing any config):** check the current official Claude Code formats for Skills
(`.claude/skills/<name>/SKILL.md` frontmatter), subagents (`.claude/agents/*.md`: `name`, `description`, `tools`),
path-scoped rules (`.claude/rules/*.md` with `paths:` globs), hook events and input/output JSON
(`PreToolUse`/`PostToolUse`, `permissionDecision`, exit code 2), and the commit-attribution setting.
I'll check these against the docs with the claude-code-guide agent and won't guess. Anything that doesn't exist gets
dropped and reported.

### Phase 2: Core instructions (`docs:` commit)
| Change | Why | Files | Verify |
|---|---|---|---|
| 2.0 Copy this plan into the repo | "Everything in the repo" rule | `docs/development/TOOLKIT_PLAN.md` | file exists |
| 2.1 Slim the root CLAUDE.md to about 150 lines: purpose, ownership, the **12 merged invariants**, paths, commands, Git/PR/release rules, DoD, and an index of Skills and agents. Fix inconsistencies 1–6. | It loads every session, so it must be short and correct | `CLAUDE.md` (and commit it; it's untracked today) | every path and command in it exists or is marked "planned"; `pytest`/`mvnw`/`npm` commands run |
| 2.2 Path-scoped rules, moving content out of CLAUDE.md **without changing what it says**: `backend.md` (`backend/**`), `python-ai.md` (`ai-service/**`), `frontend.md` (`frontend/**`, holds the design system, palette, UX, a11y and i18n), `ai-safety.md` (`ai-service/gajamitra/{track,policy,agent,graph}*.py`, `policy/**`), `messaging.md` (backend messaging + `worker.py`, planned), `database.md` (`backend/src/main/resources/db/**`, planned), `security.md` (`**/*Security*`, `.env*`, `docker-compose.yml`, `.github/**`), `testing.md` (`**/test*/**`), `git-release.md` (`.github/**`, `.releaserc.json`, `commitlint.config.*`) | Context loads only when relevant; no duplication | `.claude/rules/*.md` | each `paths` glob matches at least one existing file, or is marked planned |

### Phase 3: Skills (`chore:` commit). I recommend 17, not 20, to avoid duplicates
Each skill gets: purpose · when to use · inputs · steps · files to inspect · constraints · verification · report format.

| Skill | Notes |
|---|---|
| `/safety-audit` | Checks the diff against the 12 invariants; must name the test that protects each one it touches |
| `/ai-safety-review` | Prompts, structured outputs, guard bypasses, prompt injection through video text. Kept separate because it goes deeper on LLM-specific risks than the safety audit |
| `/elephant-tracking-review` | `track.py`: continuity, ID stability, missing detections (R1, R2), calibration constants |
| `/api-contract-review` | REST + MCP tool schemas + DTO compatibility. **Absorbs `/check-integration`** |
| `/rabbitmq-review` | ack-after-persist, DLQ, idempotency, ordering, at-least-once |
| `/database-review` | Flyway-only, constraints, transactions, N+1, never edit a committed migration |
| `/ui-ux-review` | Applies the visual quality gate + palette from `rules/frontend.md` |
| `/localization-review` | EN/SI/TA, Unicode SMS segment counts (70/67), fonts, no string concatenation |
| `/test-and-verify` | Picks suites from the changed paths, runs them, and reports what ran and what didn't |
| `/pre-release-audit` | Runs the relevant reviews + test-and-verify + migration/deploy check before a release |
| `/release-readiness` | Read-only: commits since the last tag → predicted bump, CI status, `.releaserc` sanity. Never tags |
| `/prepare-commit` | Diff review, type selection, `<type>: <message>`, secret/unrelated-file check, no trailers |
| `/prepare-pr` | Title `<type>: <message> - <TASK>` (asks if the task is unknown) + the full description template |
| `/review-pr` | Reviews a PR **or the local diff**. **Absorbs `/review-changes`**. Builds on the built-in `/code-review` and adds GajaMitra checks. Never approves or merges |
| `/plan-feature` | Stages 1–2 of the workflow; output is a plan for approval |
| `/implement-feature` | Stages 3–7: branch → implement → verify → review → commit → push → PR → stop |
| `/debug-service` | Reproduce → isolate (ai-service / backend / frontend / broker) → root cause → failing test → fix |
| ~~`/understand-project`~~ | Dropped: CLAUDE.md + `docs/ARCHITECTURE.md` + built-in `/init` cover it |

Verify: frontmatter parses; every file and path a skill names exists (or is marked planned); each skill shows up in `/` listing.

### Phase 4: Subagents (`chore:` commit)
`architect-reviewer`, `backend-reviewer`, `ai-safety-reviewer`, `tracking-reviewer`, `frontend-reviewer`,
`security-reviewer`, `test-engineer`. Reviewers get **read-only tools** (Read, Grep, Glob, plus Bash limited to
read commands where the format allows it). `test-engineer` may write test files only, and that is stated in its
instructions. Each has: scope, repo context, criteria, and the same findings format (severity · file:line · invariant ·
fix). Skills call agents selectively, for example `/pre-release-audit` runs the relevant reviewers in parallel.
`security-reviewer` adds the GajaMitra threat list (SMS, uploads, MCP key, LLM/video injection) on top of the built-in `/security-review`.

### Phase 5: Hooks and permissions (`chore:` commit)
One stdlib-only Python script, `.claude/hooks/guard.py`, deterministic and under 100 ms with no network:

| Event | Matcher | Behaviour |
|---|---|---|
| PreToolUse | Bash | **Block** (exit 2, with a reason): `git push` to `main` or with `--force`, `gh pr merge` / `gh pr review --approve`, `git commit --author`, `git config user.*`, `Co-authored-by` in a commit message, `git reset --hard` / `git clean -f` / `git branch -D` / `git checkout --` (ask instead), commit messages not matching `^(feat\|fix\|perf\|refactor\|docs\|style\|test\|build\|ci\|chore\|revert): .+`, `git commit` while `git diff --cached` contains secret patterns (Meta `LLM\|`, Twilio `AC…`/auth token, private keys) |
| PreToolUse | Edit\|Write | **Deny** `.env` / `.env.*` (except `.env.example`). **Ask**: `policy/policy_table.json`, already-committed Flyway migrations, `.github/workflows/**`, `.releaserc.json`, `track.py` thresholds |
| PostToolUse | Edit\|Write | On safety-critical paths, add context: "safety-critical file changed: run /safety-audit and the invariant tests" |

`.claude/settings.json` (committed): `permissions.deny` for reading `.env*` and for `gh pr merge`, push to main and force
push (defence in depth alongside the hook); **commit/PR attribution switched off**; the hook registrations.
No formatter hook: the repo uses `ruff check`, not `ruff format`, so formatting on every edit would cause churn.
No Stop hook, because it risks loops; the Definition of Done covers running tests.
Verify: unit-test `guard.py` with sample hook payloads (`ai-service`-style pytest under `.claude/hooks/test_guard.py`,
run in CI); then do a live check that `git push origin main` and a bad commit message are blocked.

### Phase 6: Git and release workflow (`ci:` / `build:` commits)
| Change | Files | Notes |
|---|---|---|
| Commit message check on PRs | `commitlint.config.mjs`, `.github/workflows/pr-checks.yml` | `type-enum` = your 11 types; `scope-empty: always` (no scopes); checks the PR's commits only, so the old initial commit is exempt |
| PR title check | same workflow | bash regex `^(types): .+ - <TASK-REGEX>$` (needs Q1). No third-party action |
| semantic-release | root `package.json` (private, release tooling only) + lockfile, `.releaserc.json`, `.github/workflows/release.yml` | plugins: commit-analyzer (`conventionalcommits` preset + `releaseRules` making `revert`→patch and refactor/docs/style/test/build/ci/chore→none), release-notes-generator, changelog (`CHANGELOG.md`), git (commits CHANGELOG as `chore(release): x.y.z [skip ci]`), github (Release). Runs on push to `main` with `concurrency` and least-privilege `permissions`, pinned versions |
| CI hardening | `ci.yml` | add `permissions: contents: read`, `concurrency`. SHA-pinning the actions is recommended; it's in your list of things that need approval as a dependency change |
| Branch protection / ruleset (**you do this**; I'll write the exact settings) | GitHub settings | PR required, required checks (CI + pr-checks), no force push or deletion, squash-merge only (so the PR title becomes the release commit), release identity as the only bypass actor, secret scanning + push protection on |

Notes: semantic-release starts at **1.0.0** on the first `feat`/`fix` (you said not to create tags by hand, so no
`v0.0.0` seed tag). Merging with squash means the commit analysed on `main` is the PR title (`feat: x - GM-12 (#3)`);
the type prefix still parses.
Verify: `npx semantic-release --dry-run --no-ci` locally (no tag, no push) shows the predicted version and notes;
commitlint run against good and bad sample messages; the PR-title regex tested against the examples in your prompt.

### Phase 7: Documentation (`docs:` commit)
`docs/development/CLAUDE_CODE_TOOLKIT.md` (architecture, skills, agents, hooks, permissions, MCP, Git/release,
troubleshooting, how to add a skill or agent) and `docs/development/CLAUDE_CODE_QUICKSTART.md` (the 9 workflows). They
document only what has been built and verified.

### Phase 8: Validation
Every rule `paths` glob, skill and agent file reference resolves; settings JSON is valid; guard tests pass;
`pytest`/`ruff`, `mvnw verify` and `npm run lint && build` pass; semantic-release dry run; commitlint samples; I confirm no
`Co-authored-by` appears in any commit (`git log --format=%B`); the PR is opened and left open.

### Phase 9: Final report, then stop. You review and merge.

**Integrations:** GitHub access via `gh` CLI (Q4). Figma, a database MCP and a docs MCP are **not added**: there are no
designs, no database yet, and nothing else they would help with today.
**Attribution:** your rule (no AI co-author lines) overrides Claude Code's default; it's set in settings and enforced by the hook.

## 7. Decisions (answered 2026-11-04)
| Q | Decision | Effect on the plan |
|---|---|---|
| Q1 Task numbers | **"Start with 1"**, read as `GM-<n>` numbered from 1 (the format in your examples). **This toolkit is GM-1** (correct me at approval if that's wrong) | Branch `chore/GM-1-claude-code-toolkit`; PR title `chore: add Claude Code engineering toolkit - GM-1`; title regex `- GM-[1-9][0-9]*$`. Claude asks you for the number on every new task and never invents one |
| Q2 Breaking changes | **Allowed** → major | `feat!: …` / `fix!: …` and a `BREAKING CHANGE:` footer → major. Commitlint and the PR-title regex accept an optional `!` after the type |
| Q3 Release identity | **GitHub App** | `release.yml` gets a token through `actions/create-github-app-token` from secrets `RELEASE_APP_ID` / `RELEASE_APP_PRIVATE_KEY`. You create the private app (contents: write, issues/PRs: write for release comments), install it on this repo only, and add it as the **only** bypass actor on the `main` ruleset. I'll write the step-by-step guide; I never handle the key |
| Q4 GitHub access | **gh CLI** | You run `winget install GitHub.cli` and `gh auth login`. Claude uses `gh pr create/view/checks` only; the guard hook blocks `gh pr merge`, `gh pr review --approve`, `gh api … -X PUT/PATCH/DELETE` on protection, and `gh repo edit` |

**Manual steps only you can do:** install and log in to gh; create and install the GitHub App and add its secrets; create the
`main` ruleset (exact settings in the toolkit doc); enable secret scanning + push protection. Until the ruleset exists,
the local hook and deny rules are the only things stopping a push to `main`.

## Verification (end to end)
On the toolkit branch: (1) start a fresh Claude Code session and check that the skills and agents are listed and rules load
when a matching file is opened; (2) try `git push origin main`, `gh pr merge 1`, a commit with a `Co-authored-by` line, and a
commit message without a type. All four must be blocked with a clear reason; (3) edit `policy/policy_table.json` and expect
an "ask" prompt; (4) run all project checks; (5) semantic-release dry run; (6) open the PR with a compliant title and confirm
pr-checks pass on it; (7) you merge, then the release workflow runs on `main`: it creates no release for `chore`/`docs`/`ci` commits, and the first `feat`/`fix` produces v1.0.0, a tag and CHANGELOG.md.
