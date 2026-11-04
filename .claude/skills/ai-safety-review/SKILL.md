---
name: ai-safety-review
description: 'Review LLM and agent code in GajaMitra (Muse Spark responder/verifier, prompts, structured outputs, tool calls, LangGraph nodes) for hallucination risk, unvalidated model output, guard bypasses, prompt injection from video frames, unsafe retries and escalation logic. Use when ai-service agent, prompt, schema or graph code changes.'
argument-hint: "[files or diff range; default: changed files under ai-service/]"
---

# AI safety review

**Purpose:** make sure the models only ever *propose*, and that deterministic code and people decide.

## Inputs
- Changed files under `ai-service/gajamitra/` (today: `agent.py`, `perception.py`; planned: `graph.py`, `prompts.py`, `schemas.py`, `policy.py`).
- Plan Phase 2 (nodes, state, guard table) and `.claude/rules/ai-safety.md`, `.claude/rules/python-ai.md`.

## Steps
1. **Map the flow:** for every LLM call, write down input → model → output → where the output goes. Mark each destination: decision, side effect, prompt of another call, UI, log.
2. **Output validation:** every model output is parsed into a Pydantic model. Anything that doesn't match is rejected, not coerced. No `json.loads` + dict access on raw model text. Tool arguments are validated before the tool runs (prototype risk R3/R4).
3. **Authority:** risk level comes only from `track.assess`. Severity limits come only from `policy_table.json` through the guard. Check that no prompt, model field or verifier verdict can raise a severity, skip the guard or skip approval.
4. **Prompt injection:** frames can contain text (signs, captions). Model output that reaches SMS or HTML must be treated as untrusted. Check alert text length, character set and that no instructions from frames are followed (for example "ignore previous instructions").
5. **Hallucination handling:** what happens if the model invents an elephant, a calf, a location, or claims movement the tracker didn't measure? The verifier must compare claims to tracker numbers; the guard must cap regardless.
6. **Failure paths:** timeouts, retries (capped, with backoff), refusals, empty output, wrong language. Each ends in an explicit state; none becomes "no alert" silently (invariants 1, 10).
7. **LangGraph specifics (when `graph.py` exists):** no side effect before `interrupt()`; nodes return only changed fields; checkpoint and resume tested; `revise` loop bounded (max 2).
8. **Tests:** fake chat model tests exist for: invalid output, over-severe proposal, verifier reject/revise, injection text in the alert. Run them via `/test-and-verify`.

## Constraints
- Read-only review. Don't call the Meta API to "check"; tests use fakes (CLAUDE.md: tests never spend credits).
- Don't recommend adding agents where a deterministic function works.

## Report
```
AI safety review: <scope>
Verdict: OK | CHANGES NEEDED
Flow map: <call → output → destination, one line each>
Findings: [blocker|major|minor] file:line: risk → invariant (7/8/1/10) → fix
Missing tests: <list>
Tests run: <command> → <result> (or "not run: <reason>")
```
