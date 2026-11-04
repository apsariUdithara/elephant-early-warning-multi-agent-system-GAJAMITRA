---
name: elephant-tracking-review
description: 'Review GajaMitra''s elephant tracking and risk maths (SAM 3.1 output parsing, track continuity, herd identity, missing detections, velocity, boundary distance, ETA, NEAR_BODIES floor, calibration constants, risk propagation). Use when track.py, perception.py or anything consuming the tracker summary changes.'
argument-hint: "[files or diff range; default: changes to track.py / perception.py]"
---

# Elephant tracking review

**Purpose:** the tracker is the source of truth for risk. If it is wrong or silent, every later safety layer is working from bad evidence.

## Inputs
- `ai-service/gajamitra/track.py` (`parse_sam`, `tracks`, `velocity`, `assess`, constants `HIGH_ETA_S`, `MIN_CLOSING_PX_S`, `VELOCITY_WINDOW_S`, `NEAR_BODIES`).
- `ai-service/gajamitra/perception.py` (`segment_video`, `keyframes`).
- `ai-service/tests/test_track.py`.
- SAM 3.1 output format notes in `HANDOVER.md` section 3 (local only) and the plan Phase 0 step 3.

## Steps
1. **Parsing (`parse_sam`):** what does it return for an empty stream, an error message, a format change, a mask payload containing `|`, IDs that skip numbers? Empty or unparseable output must not look like "no elephants" (invariant 1, known risk R1).
2. **Identity and continuity:** IDs are taken from SAM and assumed stable. What if an ID disappears and returns, two IDs swap, or one elephant gets two IDs? Check herd size and per-elephant ETA in each case.
3. **Missing detections:** an elephant last seen early in the clip is assessed from its last box (`tr[-1]`, risk R2). Is staleness considered? Is a herd that walked out of view toward the village still reported?
4. **Maths:** ground point = box bottom-centre; velocity = least squares over `VELOCITY_WINDOW_S`; signed distance and village side from the 3-point boundary; ETA = distance / closing speed. Check division by zero, a single-point track, a boundary with zero length, frames with fps 0.
5. **Risk propagation:** `critical` if any elephant is on the village side; `high` if any ETA < `HIGH_ETA_S`; `medium` if approaching or within `NEAR_BODIES` body heights; else `low`. A change must never lower the risk for an existing scenario without user sign-off.
6. **Calibration constants:** changed? Then the user must approve, the PR must explain why, and a test must show the new boundary case.
7. **Tests:** each step above has a case in `test_track.py`, or the report lists it as missing. Run `pytest` via `/test-and-verify`.

## Constraints
- Read-only. No API calls; use synthetic SAM text like the `SAMPLE` in `test_track.py`.
- Distances are pixels; don't introduce metres without the calibration design (plan Phase 9).

## Report
```
Tracking review: <scope>
Verdict: OK | CHANGES NEEDED
Scenario table: | scenario | expected risk | actual (code path) | test |
Findings: [blocker|major|minor] file:line: problem → invariant 1/8 → fix
Constants changed: <none | old → new, approved?>
Tests run: <command> → <result>
```
