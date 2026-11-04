---
name: ui-ux-review
description: 'Review GajaMitra frontend screens (officer dashboard, approvals, live analysis, villager views) for task clarity, information hierarchy, risk communication, approved Clean Neutral & Signal Blue palette and tokens, light/dark themes, responsive field use, WCAG 2.2 AA accessibility and loading/empty/stale/error states. Use when a page or component under frontend/ changes.'
argument-hint: "[page, component or diff range; default: changed files under frontend/src/]"
---

# UI/UX review

**Purpose:** officers must understand a situation and act correctly in seconds. A confusing screen is a safety problem.

## Inputs
- Changed files under `frontend/src/`; the running app when available (`npm run dev` in `frontend/`).
- `.claude/rules/frontend.md`: product principles, palette table, theme rules, operational UX, responsive, localisation, accessibility, and the **visual quality gate** (10 questions).
- Plan Phase 5 (pages).

## Steps
1. **Task first:** for each screen, state the user (officer / admin / villager), their decision, and the one primary action. Is that action the most prominent element?
2. **Risk communication:** risk uses the `risk-*` tokens only, always with a text label (never colour alone); brand blue/terracotta never imply risk. The most urgent valid alert stands out; not everything looks urgent.
3. **Time and freshness:** observation, analysis, alert and delivery times are distinct and labelled; stale or missing data is conspicuous.
4. **Consequential actions:** approve/reject show context and consequence, prevent double submission, show the resulting state, and never delay an automatic warning (invariant 2).
5. **Tokens and themes:** no raw colour values in components; light and dark themes both checked for contrast; no decorative gradients, glows or card grids.
6. **States:** loading, empty, offline, error, stale and permission-denied are designed, not default.
7. **Responsive and field use:** works at phone width without horizontal scroll; touch targets ≥ 44 px; nothing essential behind hover.
8. **Accessibility:** semantic HTML, labels, keyboard path for approve/reject, visible focus, `prefers-reduced-motion`, live-region announcements for new alerts.
9. **Visual check:** if the app runs, inspect the screen at desktop and phone widths in both themes. If you can't run it, say so; never claim a visual review you didn't do.
10. Hand localisation details to `/localization-review`.

## Report
```
UI/UX review: <screen(s)>
Visual quality gate: 10 answers (yes/no + reason)
Inspected visually: yes (sizes, themes) | no (<reason>)
Findings: [blocker|major|minor] file:line or screen area: problem → rule in frontend.md → fix
```
