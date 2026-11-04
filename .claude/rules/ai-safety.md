---
paths:
  - "ai-service/gajamitra/track.py"
  - "ai-service/gajamitra/perception.py"
  - "ai-service/gajamitra/agent.py"
  - "ai-service/gajamitra/policy.py"
  - "ai-service/gajamitra/graph.py"
  - "ai-service/gajamitra/prompts.py"
  - "ai-service/gajamitra/schemas.py"
  - "policy/**"
---

# Safety-critical code

You are editing code that decides whether villagers are warned. The 12 safety invariants in the root `CLAUDE.md` apply in full.

## Which file owns which decision

| File | Decides | Status |
|---|---|---|
| `ai-service/gajamitra/track.py` | Risk level (`assess`), parsing of SAM output (`parse_sam`), calibration constants | exists |
| `ai-service/gajamitra/perception.py` | What the tracker sees (SAM call, keyframes) | exists |
| `ai-service/gajamitra/agent.py` | Prototype only: the LLM calls tools directly with no guard. Do not extend this pattern; the graph replaces it | exists, to be replaced |
| `policy/policy_table.json` | Risk → exact level sent now, escalation level, maximum, officer report, what is held | exists, read by `policy.py` |
| `ai-service/gajamitra/policy.py` | Applies the table to a proposal: level, template fallback, officer report, `send_now` / `escalation` split | exists (GM-3) |
| `ai-service/gajamitra/schemas.py` | Proposal, alert, report and guard-decision models (strict, reject unknown fields) | exists (GM-3) |
| `ai-service/gajamitra/graph.py`, `prompts.py` | Agent flow and prompts | planned (plan Phase 2) |

## Before changing any of these files

1. Name the invariants the change touches and the tests that protect them. If no test protects one, write the test first.
2. Never change a calibration constant (`HIGH_ETA_S`, `MIN_CLOSING_PX_S`, `VELOCITY_WINDOW_S`, `NEAR_BODIES`) or a row of `policy_table.json` without the user's approval and a test that shows the new behaviour.
3. `policy_table.json` is a contract between Python and Java. Change both readers and both test suites in the same change.
4. Run `/safety-audit` before committing, and `/elephant-tracking-review` when `track.py` or `perception.py` changed.

## Known open risks (from the toolkit assessment)

Do not make these worse; fix them only as an assigned task.

- ~~**R1:** a SAM failure reads as "No elephants detected".~~ Fixed in GM-2: `parse_sam` raises `SamOutputError`, `segment_video` raises `PerceptionError` unless the stream completes, and the prototype API answers 502. Keep it that way: empty SAM output is the only "no elephants" case.
- **R2:** an elephant that left the frame early is assessed from its last position; there is no "lost track" state.
- **R3/R4:** `agent.run_agent` passes unvalidated LLM arguments to tools and parses the final JSON without a schema.
