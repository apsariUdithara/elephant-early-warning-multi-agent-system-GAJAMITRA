---
name: review-pr
description: 'Review a GajaMitra pull request or the local branch diff for correctness, architecture and service boundaries, the 12 safety invariants, security, contracts, maintainability, conventions (commit types, PR title, no AI attribution) and test coverage, delegating to specialist reviewers only for areas that changed. Never approves or merges.'
argument-hint: "[PR number, or nothing for the local diff vs main]"
---

# Review PR (or local changes)

**Purpose:** give the human reviewer a reliable, prioritised list of what is wrong or risky, so they can decide whether to merge.

## Inputs
- PR: `gh pr view <n> --json title,body,commits,files,headRefName` and `gh pr diff <n>`. Local: `git diff main...HEAD` + uncommitted changes.

## Steps
1. **Conventions:** title matches `<type>: <message> - GM-<n>` (optional `!`); every commit matches `<type>: <message>`; no `Co-authored-by` or AI attribution in commits or body; branch isn't `main`; description has every required section and doesn't claim tests that didn't run.
2. **Correctness:** read each changed file in full, not just the hunk. Trace callers (`Grep`) of every changed function. Look for wrong logic, unhandled errors, off-by-one and edge cases. The built-in `/code-review` can be used for a general correctness pass; this skill adds the GajaMitra checks below.
3. **Architecture:** Spring owns data and side effects; Python only proposes through MCP; no shared DB access; no new services or abstractions without need (`.claude/rules/architecture.md`).
4. **Specialist checks, only for areas that changed** (run independent ones in parallel as agents):
   - tracking, policy, agents → `/safety-audit`, `tracking-reviewer`, `ai-safety-reviewer`
   - controllers, DTOs, MCP, messages → `/api-contract-review`, `/rabbitmq-review`
   - migrations, entities → `/database-review`
   - `frontend/**` → `frontend-reviewer` (`/ui-ux-review`, `/localization-review`)
   - auth, uploads, secrets, CI, compose → `security-reviewer`
5. **Tests:** each behaviour change and each touched invariant has a test; bug fixes have a test that fails without the fix. Run `/test-and-verify` for the PR's diff; for a PR also read `gh pr checks <n>`.
6. **Scope:** flag unrelated changes and generated files.

## Constraints
- Never `gh pr review --approve`, never merge, never push to the PR branch unless the user asks you to fix something.
- Post comments on GitHub only if the user asks; default is to report here.

## Report
```
Review of <PR #n | local diff>: <title>
Recommendation to the human: OK to merge | merge after fixes | do not merge
Blockers: [file:line] problem → why → fix
Major / Minor / Nits: same format
Conventions: title ✅/❌, commits ✅/❌, attribution ✅/❌, description ✅/❌
Tests: <run results>   CI: <status>
Specialist reviews run: <list with verdicts>
```
