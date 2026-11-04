# Claude Code engineering toolkit

How Claude Code is set up to work on GajaMitra: what each piece does, what it enforces, and how to change it.
For day-to-day use, start with the [quick start](CLAUDE_CODE_QUICKSTART.md).

**Principle:** Claude is an engineering assistant. The developer owns the work, keeps their Git identity, reviews every pull request and is the only person who merges.

## 1. How the pieces fit

| Layer | Where | Loaded | Job |
|---|---|---|---|
| Handbook | `CLAUDE.md` | every session | Purpose, service ownership, the 12 safety invariants, Git/PR/release rules, definition of done |
| Path rules | `.claude/rules/*.md` | when Claude works on a matching file | Area standards (backend, Python, frontend, safety, messaging, database, reliability, security, testing, CI/release) |
| Skills | `.claude/skills/<name>/SKILL.md` | on `/name`, or automatically when relevant | Repeatable workflows with a fixed report format |
| Agents | `.claude/agents/*.md` | when a skill or the session delegates | Specialist reviewers (read-only) and a test engineer |
| Guard hook | `.claude/hooks/guard.py` + `.claude/settings.json` | before and after every tool call | Blocks unsafe Git/GitHub actions and protected-file edits; asks before destructive ones |
| Deny rules | `.claude/settings.json` → `permissions.deny` | always | Backstop if the hook fails |
| CI | `.github/workflows/ci.yml`, `pr-checks.yml` | on GitHub | Tests, lint, build, guard tests, commit messages, PR title |
| Releases | `.releaserc.json`, `release.yml`, root `package.json` | on push to `main` | semantic-release: version, tag, GitHub Release, `CHANGELOG.md` |

The design and the Phase 1 assessment behind it are in [TOOLKIT_PLAN.md](TOOLKIT_PLAN.md).

## 2. Path rules

| Rule | Loads for |
|---|---|
| `architecture.md` | `backend/**`, `ai-service/**`, `policy/**` |
| `backend.md` | `backend/**` |
| `python-ai.md` | `ai-service/**` |
| `ai-safety.md` | `track.py`, `perception.py`, `agent.py`, `policy.py`, `graph.py`, `prompts.py`, `schemas.py`, `policy/**` |
| `messaging.md` | backend `messaging/` and `mcp/` packages, `worker.py`, `docker-compose.yml` |
| `database.md` | `backend/src/main/resources/db/**`, `*Repository.java`, `*Entity.java` |
| `reliability.md` | `backend/**`, `ai-service/**` |
| `security.md` | code, `docker-compose.yml`, `.github/**`, `.env*`, `.claude/**` |
| `testing.md` | test folders, `frontend/src/**`, `.claude/hooks/**` |
| `frontend.md` | `frontend/**` |
| `git-release.md` | `.github/**`, release and commitlint config, root `package.json`, `CHANGELOG.md` |

Some paths (Flyway migrations, `worker.py`, the backend feature packages) are planned in `docs/IMPLEMENTATION_PLAN.md` and don't exist yet; their rules start applying when the files are created.

## 3. Skills

Type `/name` in Claude Code. **Manual** skills run only when you type them (they commit, push or open PRs, or are expensive).

| Skill | Use it to | Manual |
|---|---|---|
| `/plan-feature` | Plan a change before coding; output is a plan for your approval | |
| `/implement-feature GM-<n>` | Branch → implement → test → review → commit → push → open PR → stop | ✅ |
| `/debug-service` | Reproduce, isolate, find the root cause, failing test first, fix | |
| `/test-and-verify` | Pick and run the right suites; report what ran and what didn't | |
| `/safety-audit` | Check a change against the 12 invariants, with evidence and tests | |
| `/ai-safety-review` | Prompts, model output validation, guard bypass, prompt injection | |
| `/elephant-tracking-review` | Parsing, continuity, missing detections, maths, risk levels | |
| `/api-contract-review` | REST, SSE, MCP tools, messages, policy table: compatibility and both sides | |
| `/rabbitmq-review` | Ack points, duplicates, DLQ, ordering, publish-after-commit | |
| `/database-review` | Flyway, constraints, transactions, queries, schema ownership | |
| `/ui-ux-review` | Officer and villager screens against the design system and quality gate | |
| `/localization-review` | English, Sinhala, Tamil: completeness, Unicode, SMS segments, fonts | |
| `/prepare-commit` | Check the diff, choose the type, write `<type>: <message>`, commit | ✅ |
| `/prepare-pr GM-<n>` | Title `<type>: <message> - GM-<n>`, full description, push, open PR, stop | ✅ |
| `/review-pr [n]` | Review a PR or the local diff; never approves or merges | |
| `/release-readiness` | Predict the next version from commits since the last tag | |
| `/pre-release-audit` | Full audit before a release, with parallel specialist reviews | ✅ |

Built-in commands complement these: `/code-review` (general correctness), `/security-review` (general security), `/simplify`, `/init`.

## 4. Agents

| Agent | Tools | Reviews |
|---|---|---|
| `architect-reviewer` | Read, Grep, Glob | Service ownership, contracts, plan consistency, unnecessary complexity |
| `backend-reviewer` | Read, Grep, Glob | Spring layers, validation, auth, transactions, migrations, idempotency |
| `ai-safety-reviewer` | Read, Grep, Glob | LLM output validation, guard authority, injection, LangGraph interrupt/resume |
| `tracking-reviewer` | Read, Grep, Glob | `track.py` / `perception.py` correctness and risk levels |
| `frontend-reviewer` | Read, Grep, Glob | UX, tokens, themes, accessibility, localisation, SSE, typing |
| `security-reviewer` | Read, Grep, Glob | Secrets, auth, uploads, SMS abuse, CI permissions, privacy |
| `test-engineer` | Read, Grep, Glob, Edit, Write, Bash | Writes and runs tests; **a hook limits its edits to test files** |

Reviewers can't edit, run commands, commit or push. Skills call them only for areas that changed, in parallel when they're independent (for example `/pre-release-audit` and `/review-pr`). An agent's `tools` field can't restrict Bash to particular commands, so read-only reviewers get no Bash at all.

## 5. Guard hook and permissions

`.claude/hooks/guard.py` (Python standard library only) runs before every Bash, Read, Edit and Write call, and after every edit.

**Blocked** (exit 2, with the reason shown to Claude):
- `gh pr merge`, `gh pr review --approve`, `gh release create/edit/delete`, `gh repo edit`, `gh api` writes to protection, rulesets, merges or collaborators
- `git push` to `main` (explicitly, via `HEAD:main`, or a bare push while on `main`), force pushes, pushing tags, `--mirror`, `--all`, remote deletes
- `git tag <name>` (tags come only from semantic-release)
- `git config user.name/email <value>`, `git commit --author`, `--no-verify` / `-n`
- commits on `main`; commit messages that aren't `<type>: <message>` with an approved type (optional `!`)
- `Co-authored-by` or "Generated with Claude Code" in a commit message or PR body (including `--body-file`)
- `gh pr create/edit` titles that aren't `<type>: <message> - GM-<n>`
- commits whose staged changes contain a Meta API key, Twilio SID or token, private key, GitHub token or `sk-` key
- reading or editing `.env` / `.env.*` (`.env.example` is allowed); editing `CHANGELOG.md`; editing a committed Flyway migration
- for `test-engineer`: editing anything that isn't a test file

**Asks you first** (permission prompt):
- `git reset --hard`, `git clean -f`, `git branch -D`, `git checkout -- …`, `git restore`, `git stash drop/clear`, `git rebase`, `git commit --amend`
- `docker compose down -v`, `docker volume rm/prune`, `DROP`/`TRUNCATE` through `psql` and similar, `rm -r` outside `scratch/`
- editing `policy/policy_table.json`, `.github/workflows/*`, `.releaserc.json`, `commitlint.config.mjs`, `.claude/settings.json`, `guard.py`, or `track.py` calibration constants (or rewriting `track.py`)

**After an edit** to a safety-critical file, Claude is reminded to run the invariant tests and `/safety-audit`.

**Deny rules** in `.claude/settings.json` block `Read(./.env)`, `gh pr merge`, force pushes and pushes to `main` even if the hook can't run.

**Attribution** is switched off for commits and PRs (`"attribution": {"commit": "", "pr": ""}`).

**Failure behaviour:** if the hook gets input it can't parse, it exits 1. Claude Code reports the error and continues (it doesn't block all work), and the deny rules still apply.

**Limits, stated honestly:**
- The hook reads command text. It can't see through scripts, aliases or commands built at runtime (for example `sh deploy.sh` that pushes inside). Branch protection on GitHub is the second line.
- **GitHub can't tell Claude from you:** Claude uses your Git and `gh` credentials. Without the `main` ruleset (section 8), only this hook stops a push to `main`.
- Commit messages written to the editor (no `-m`, `-F` or heredoc) aren't checked locally; `pr-checks` catches them on the PR.

Self-test: `python .claude/hooks/test_guard.py` (runs in CI as the `claude-guard` job).

## 6. Integrations

| Integration | Status | Why |
|---|---|---|
| GitHub CLI (`gh`) | **You install it** (`winget install GitHub.cli`, then `gh auth login`) | Opening PRs and reading CI. Merge/approve/settings commands are blocked by the hook |
| MCP servers | None | No Figma designs, no database yet, and no other integration with a current job. Add one only when it solves a real need, read-only first, credentials never in the repo |

## 7. Git workflow

The rules are in `CLAUDE.md` ("Git, pull requests and releases"). In short:

1. Branch per task: `<type>/GM-<n>-<slug>`. Never commit on `main`.
2. Commits: `<type>: <message>` with an approved type, no scope, optional `!` for breaking changes, developer identity, no attribution.
3. PR title: `<type>: <message> - GM-<n>`. Claude asks for the task number and never invents one.
4. Claude pushes the task branch and opens the PR, then stops. **You review and merge** (squash).

The approved type list appears in five places that must stay identical: `CLAUDE.md`, `commitlint.config.mjs`, `.releaserc.json`, `.github/workflows/pr-checks.yml` and `.claude/hooks/guard.py`.

## 8. Release automation

On every push to `main`, `release.yml` runs semantic-release (pinned in the root `package.json`):

| Commit type | Release |
|---|---|
| `type!:` or a `BREAKING CHANGE:` footer | major |
| `feat` | minor |
| `fix`, `perf`, `revert` | patch |
| `refactor`, `docs`, `style`, `test`, `build`, `ci`, `chore` | none |

It creates the `vX.Y.Z` tag and GitHub Release, and commits `CHANGELOG.md` as `chore: release X.Y.Z [skip ci]`. The first `feat` or `fix` produces **v1.0.0**. Nobody creates tags, bumps versions or edits `CHANGELOG.md` by hand.

**One-time setup (you do this; the job is skipped until it's done):**

1. **Create a GitHub App:** GitHub → Settings → Developer settings → GitHub Apps → *New GitHub App*.
   - Name: for example `gajamitra-release`. Homepage URL: the repository URL. Webhook: untick *Active*.
   - Repository permissions: **Contents: Read and write**, **Issues: Read and write**, **Pull requests: Read and write** (semantic-release comments on released issues and PRs).
   - *Only on this account*. Create it, note the **App ID**, then *Generate a private key* (a `.pem` file downloads).
2. **Install the app** on this repository only (*Install App* → *Only select repositories*).
3. **Add to the repository** (Settings → Secrets and variables → Actions):
   - Variable `RELEASE_APP_ID` = the App ID
   - Secret `RELEASE_APP_PRIVATE_KEY` = the full contents of the `.pem` file. Then delete the local `.pem` or store it in a password manager. Never put it in the repo.
4. **Protect `main`** (Settings → Rules → Rulesets → *New branch ruleset*):
   - Target: the default branch. Enforcement: Active.
   - Bypass list: **only the `gajamitra-release` app**. Don't add the admin role: Claude uses your account, so an admin bypass would let it push to `main`.
   - Rules: *Restrict deletions*, *Block force pushes*, *Require a pull request before merging* (required approvals **0**: GitHub doesn't let you approve your own PR, and your merge is the human review), *Require status checks to pass*: `claude-guard`, `ai-service`, `backend`, `frontend`, `pr-title`, `commits`.
5. **Squash merging only** (Settings → General → Pull Requests): allow squash merging, untick merge commits and rebase merging, and set the squash commit message to *Default to pull request title*. The PR title then becomes the commit semantic-release reads.
6. **Secret scanning and push protection** (Settings → Code security): turn both on.

Check the setup with `/release-readiness`.

## 9. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| "Blocked by GajaMitra guard: …" | Working as intended; the message says why. If it's a false positive, show the developer the command and fix `guard.py` with a test in `test_guard.py` (editing `guard.py` asks for approval) |
| Hook error "python: command not found" | The hook runs `python` through Git Bash. Install Python 3.9+ and make sure `python` is on `PATH` |
| "An Application Control policy has blocked this file" | Windows Smart App Control blocking a DLL; re-run. Not a code bug |
| `pr-checks / commits` fails | A commit in the PR has the wrong format. Add a new commit or ask the developer whether to reword (rewriting history needs approval) |
| `pr-checks / pr-title` fails | Edit the PR title to `<type>: <message> - GM-<n>`; the check re-runs on edit |
| Release job skipped | `RELEASE_APP_ID` isn't set (section 8) |
| Release job: push rejected | The app isn't in the ruleset bypass list, or lacks *Contents: write* |
| Skill doesn't appear in `/` | Check its frontmatter: `name` must equal the folder name; quote any value containing `: ` |

## 10. Extending the toolkit

- **Add a skill only for a repeated workflow** that isn't covered by an existing skill or a built-in command. Create `.claude/skills/<name>/SKILL.md` with `name` (same as the folder) and a single-quoted `description` saying what it does and when to use it. Add `disable-model-invocation: true` if it has side effects (commits, pushes, PRs, spending money). Structure: purpose, inputs, steps, constraints, report format. Add it to the table in section 3.
- **Add an agent** in `.claude/agents/<name>.md` with `name`, `description` and the smallest `tools` list that works (read-only reviewers: `Read, Grep, Glob`). Add it to section 4.
- **Add a rule** in `.claude/rules/<name>.md` with a `paths:` list of globs that match real files.
- **Change the guard:** add the case to `test_guard.py` first, then change `guard.py`, then run `python .claude/hooks/test_guard.py`.
- Keep `CLAUDE.md` short: put area detail in a rule, and workflows in a skill.
