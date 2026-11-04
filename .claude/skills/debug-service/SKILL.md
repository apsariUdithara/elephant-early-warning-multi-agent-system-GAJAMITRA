---
name: debug-service
description: 'Debug a GajaMitra failure systematically - reproduce it, isolate the failing component (ai-service, backend, frontend, RabbitMQ, Postgres, Meta API, Twilio), find the root cause in the shared code path rather than patching a symptom, write a failing test, then fix within scope. Use for errors, wrong risk levels, missing or duplicate alerts, stuck analyses, failing CI.'
argument-hint: "<symptom, error message or failing test>"
---

# Debug service

## Steps
1. **Capture the symptom exactly:** error text, stack trace, failing test name, `analysisId`, time, input clip/boundary. Ask for missing facts only if you can't reproduce without them.
2. **Reproduce** with the smallest case:
   - tracking or risk → a synthetic SAM string in a pytest (like `SAMPLE` in `ai-service/tests/test_track.py`); no API calls
   - prototype end to end → `uvicorn gajamitra.app:app` from `ai-service/` (needs `MODEL_API_KEY`; spends credits, so ask first)
   - backend → `.\mvnw test -Dtest=<Class>`; infrastructure → `docker compose ps` / `docker compose logs <service>`
   - CI → `gh run view <id> --log-failed`
3. **Isolate:** follow the data across the boundary (browser → Spring → RabbitMQ → worker → Meta → MCP → Spring → Twilio). Find the first component whose output is wrong. Correlate logs by `analysisId` / `eventId`.
4. **Root cause:** before editing, `Grep` every caller of the function you'll change. Fix it once in the shared path, not in the caller that happened to fail.
5. **Safety triage:** if the bug could hide a herd, delay or duplicate a warning, or bypass the guard, say so first and treat it as a blocker (run `/safety-audit` on the fix).
6. **Failing test first:** add a test that fails for the bug, confirm it fails, then fix and confirm it passes.
7. **Verify:** `/test-and-verify` for the affected services.
8. **Commit and PR** only if this is an assigned task (`/prepare-commit`, `/prepare-pr`); otherwise report and stop.

## Constraints
- No destructive commands (`docker compose down -v`, dropping tables, `git reset --hard`) without explicit permission.
- Never send real SMS or call paid APIs to reproduce without asking.

## Report
```
Bug: <symptom>
Root cause: <file:line + explanation>
Component: <where it first goes wrong>
Safety impact: <none | invariant # at risk>
Fix: <what changed>   Test: <name> (failed before ✅, passes after ✅)
Verification: <suites and results>
```
