---
name: localization-review
description: 'Review English, Sinhala and Tamil support in GajaMitra (SMS alert text, UI strings, translation keys, Unicode handling, SMS segment length, fonts, text expansion, locale formatting, fallback behaviour). Use when alerts, prompts that write alerts, SMS code or translated UI strings change.'
argument-hint: "[files or diff range; default: changes touching alerts, SMS or i18n]"
---

# Localization review

**Purpose:** a warning that is truncated, garbled or ambiguous in Sinhala or Tamil is a warning that didn't arrive.

## Inputs
- Alert-writing prompts and schemas (`ai-service/gajamitra/agent.py` today; planned `prompts.py`, `schemas.py`), SMS code (planned backend `sms` package), frontend translation files (planned under `frontend/src/`).
- `.claude/rules/frontend.md` section "Sinhala, Tamil and English localisation"; plan Phase 0 step 6 (Twilio Unicode limits).

## Steps
1. **Completeness:** every alert has `en`, `si` and `ta`; missing language → defined fallback (send English plus the others available, and log the gap), never a silent drop.
2. **SMS length:** Sinhala/Tamil force UCS-2: 70 characters for one SMS, 67 per part when concatenated. Count real alert text; the Twilio trial prefix also uses characters. Long alerts split into many parts cost money and arrive out of order on some phones; prefer short, complete sentences.
3. **Unicode correctness:** text stays NFC-normalised UTF-8 end to end (model → JSON → DB → Twilio); no `?` or mojibake; no byte-length truncation that cuts a character in half.
4. **Content quality:** where, how many, what to do; no English fragments inside Sinhala/Tamil sentences; no concatenated fragments; numbers and times unambiguous. Model-written translations need a native-speaker check (plan Phase 6, `evals/translation_review.md`).
5. **UI:** complete translation keys, no string concatenation, fonts with Sinhala/Tamil coverage (Noto Sans Sinhala / Tamil), no fixed-height containers that clip, locale-aware dates and numbers, language choice persisted and keyboard-accessible.
6. **Tests:** realistic SI/TA strings in tests (not English placeholders), including a segment-count test for the longest expected alert.

## Report
```
Localization review: <scope>
Verdict: OK | CHANGES NEEDED
| Message | EN chars | SI chars/segments | TA chars/segments | Fallback | Native check done? |
Findings: [blocker|major|minor] file:line: problem → fix
```
