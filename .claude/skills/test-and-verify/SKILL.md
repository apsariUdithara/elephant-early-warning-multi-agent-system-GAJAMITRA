---
name: test-and-verify
description: 'Work out which GajaMitra test suites, linters and builds a change needs (ai-service pytest + ruff, backend mvnw verify, frontend lint + build, guard hook self-test), run them, and report exactly what ran, what passed, what failed and what could not run. Use before committing, before opening a PR, and whenever someone asks whether the change works.'
argument-hint: "[diff range or 'all'; default: branch diff vs main + uncommitted]"
---

# Test and verify

**Purpose:** honest verification. A check counts only if it ran in this session and its output was read.

## Inputs
- Changed files: `git diff --name-only main...HEAD` plus `git status --short`.

## Steps
1. **Select suites from the changed paths:**

   | Changed path | Run (from that directory) |
   |---|---|
   | `ai-service/**` | `..\.venv\Scripts\python -m pytest -q` and `..\.venv\Scripts\python -m ruff check .` |
   | `backend/**` | `.\mvnw verify` (needs Docker running once Testcontainers tests exist) |
   | `frontend/**` | `npm run lint` and `npm run build` (`npm ci` first if `node_modules` is missing) |
   | `.claude/hooks/**` | `python .claude/hooks/test_guard.py` (from the repo root) |
   | `policy/policy_table.json` | the Python **and** Java suites |
   | `.github/workflows/**`, release config | validate YAML/JSON; run `npx commitlint` / release dry run as described in `docs/development/CLAUDE_CODE_TOOLKIT.md` |
   | docs only | no suite; check links and paths exist |
   `all` runs every suite.
2. **Safety-relevant change?** (see `/safety-audit` step 1) Then name the invariant tests and make sure they are in the run.
3. **Run each command.** Don't stop at the first failure; run the rest so the report is complete.
4. **Read the output.** Record pass/fail counts, the first error for each failure, and warnings that matter.
5. **Failures:** report them with the error. Fix only if it's within the current task's scope; never skip, mark `xfail`, delete or loosen a test to get green.
6. **Couldn't run** (no Docker, missing key, Windows Smart App Control blocked a DLL: retry once), say exactly why.

## Constraints
- Never call the real Meta API or Twilio. Evals (`evals/`) are not tests and only run when the user starts them.
- Never claim a result you didn't see.

## Report
```
Verification: <scope>
| Suite | Command | Result | Notes |
| ai-service tests | pytest -q | ✅ 12 passed | |
| backend | mvnw verify | ❌ 1 failed | ApprovalServiceTest.timeout… : <first error line> |
| frontend | npm run build | ⏭ not run | no frontend changes |
Invariant tests included: <list or "n/a">
Not run and why: <list>
Overall: PASS | FAIL | PARTIAL
```
