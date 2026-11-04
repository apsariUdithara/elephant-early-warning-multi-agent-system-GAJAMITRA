---
name: pre-release-audit
description: 'Comprehensive release-readiness audit of GajaMitra before changes accumulate into a release - safety invariants, security, contracts, migrations, tests, CI, deployment config and documentation across everything since the last release tag. Runs the relevant specialist reviews in parallel. Use before merging a set of feat/fix PRs that will produce a release, or when asked "are we ready to release?".'
argument-hint: "[range; default: last release tag..main, or all of main if no tag exists]"
disable-model-invocation: true
---

# Pre-release audit

**Purpose:** catch anything that should not reach a release, across all changes since the last one. Read-only; it never tags or releases.

## Inputs
- Range: `git describe --tags --abbrev=0` → `<tag>..main`; no tag yet → all of `main`.
- `/release-readiness` output (what version semantic-release would produce).

## Steps
1. Run `/release-readiness` first: predicted version, commit types, CI status.
2. List changed areas in the range and pick reviewers. Run the independent ones **in parallel** as agents, only for areas that changed:
   - safety-relevant code → `ai-safety-reviewer`, `tracking-reviewer`, plus `/safety-audit`
   - `backend/**` → `backend-reviewer` (contracts, DB, messaging)
   - `frontend/**` → `frontend-reviewer`
   - auth, uploads, secrets, CI, compose, env → `security-reviewer`
   - cross-service or plan changes → `architect-reviewer`
3. Run `/test-and-verify all`.
4. **Migrations and deployment:** new Flyway migrations are additive and ordered; `docker-compose.yml`, `.env.example` and the README list every new variable; no secrets committed (`git log -p <range>` scan for keys).
5. **Docs:** plan, README and `docs/ARCHITECTURE.md` match the behaviour shipped.
6. **Merge decision input:** list blockers. The human decides; this skill never merges, tags or edits `CHANGELOG.md`.

## Report
```
Pre-release audit: <range> → predicted <version> (<reason>)
Verdict: READY | READY WITH NOTES | NOT READY
Reviews run: <agent/skill → verdict>
Tests: <table from /test-and-verify>
Blockers: <list>
Notes for the release reviewer: <list>
```
