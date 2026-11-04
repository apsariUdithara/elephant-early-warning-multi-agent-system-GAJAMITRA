---
name: safety-audit
description: 'Audit a change against GajaMitra''s 12 safety invariants (silent herd, warnings never delayed by approval, fail-closed guardrail, at-most-once side effects, at-least-once messaging, checkpoint resume, untrusted AI output, deterministic decisions, officer not a single point of failure, pipeline failure visibility, traceability, tested reasoning). Use before committing any change to tracking, policy, agents, alert dispatch, approval, messaging or SMS code.'
argument-hint: "[branch, commit range or PR number; default: uncommitted + branch diff vs main]"
---

# Safety audit

**Purpose:** decide whether a change can stop, delay, duplicate or wrongly escalate a warning to villagers.

## Inputs
- The change: `$ARGUMENTS`, or by default `git diff main...HEAD` plus uncommitted changes.
- The invariants in the root `CLAUDE.md` ("Safety invariants") and `.claude/rules/ai-safety.md`.
- `docs/IMPLEMENTATION_PLAN.md` sections "Safety and delivery invariants", Phase 2 (graph and guard table) and Phase 3 (MCP re-check, approvals).

## Steps
1. List the changed files. Classify each: tracking (`track.py`, `perception.py`), policy (`policy/policy_table.json`, `policy.py`, Java re-check), agents (`agent.py`, `graph.py`, `prompts.py`, `schemas.py`), dispatch/approval/SMS (backend `alert`, `approval`, `sms`, `mcp` packages), messaging (`worker.py`, backend `messaging`), other. If nothing is safety-relevant, say so and stop.
2. For **each** of the 12 invariants, answer: touched? how? still holds? Trace the real code path; don't rely on names. Check especially:
   - Can any error, empty result, timeout or parse failure end up as "no elephant" or risk `low`? (inv. 1, 10)
   - Can an approval, a slow officer, or a missing officer delay a `warning` or a `critical` alert? Does the timeout still send? (inv. 2, 9)
   - Can an alert reach Spring without a stored risk, or above the policy-table maximum? (inv. 3, 8)
   - Does every side effect carry an idempotency key with a UNIQUE constraint? What happens on redelivery? (inv. 4, 5, 6)
   - Does any model output reach a decision, SQL, shell, HTML or SMS without schema validation and the deterministic guard? (inv. 7, 8)
   - Is every state transition logged with `analysisId` and written to the audit log? (inv. 11)
3. For each touched invariant, find the test that would fail if it broke (`ai-service/tests/`, `backend/src/test/`). Run it with `/test-and-verify`. No test → that's a finding.
4. If `policy_table.json` changed: confirm both readers and both test suites changed in the same diff.
5. For deep LLM or tracking questions, hand off to `/ai-safety-review` or `/elephant-tracking-review` (or the `ai-safety-reviewer` / `tracking-reviewer` agents) instead of guessing.

## Constraints
- Read-only. Don't fix during the audit; report.
- Never mark an invariant "holds" without naming the code path and the test.
- Invariant 10 has no designed mechanism yet (risk R7). Flag any change that makes a quiet pipeline stop more likely.

## Report
```
Safety audit: <scope>
Verdict: SAFE | SAFE WITH NOTES | UNSAFE (blocks merge)

| # | Invariant | Touched | Holds | Evidence (file:line) | Protecting test |
...12 rows...

Findings (most severe first):
[blocker|major|minor] file:line: what can go wrong → which invariant → suggested fix

Tests run: <command> → <result>   (or "not run: <reason>")
Needs user sign-off: <list, or "none">
```
