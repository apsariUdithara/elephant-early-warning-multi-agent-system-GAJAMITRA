---
paths:
  - "frontend/**"
---

# Frontend (React + TypeScript)

## Frontend (frontend/): React + TypeScript (Vite)

- `strict` TypeScript. API types are written once in `src/api/` to match the backend DTOs, and there is no `any` at the API boundary.
- Function components and hooks. Keep server state (fetch / SSE) separate from UI state, and keep components presentational where possible.
- Render model-written text (alerts, reasoning) as text, never with `dangerouslySetInnerHTML`.
- Accessibility basics: labelled controls, keyboard-usable approve/reject buttons, and risk shown by text as well as colour.

## Frontend product design and UX

The frontend is a safety-relevant operational product, not a generic admin dashboard or a portfolio template. Design for officers who need to understand a situation quickly, make correct decisions and use the system under real operational conditions.

### Product design principles

- **Clarity before decoration.** Every screen must make its purpose, current state and next useful action immediately understandable.
- **Design around user tasks.** Start with the user's role, decisions and workflow. Do not start by filling a dashboard with cards, charts and widgets.
- **Prioritise information.** Put the most time-sensitive and decision-relevant information first. Secondary details belong lower on the page or behind progressive disclosure.
- **Use progressive disclosure.** Show essential information by default. Reveal advanced filters, technical details and secondary metadata only when useful.
- **Reduce cognitive load.** Avoid competing colours, excessive borders, nested cards, redundant labels, decorative charts and unnecessary controls.
- **Make important actions unmistakable.** Approval, rejection, escalation and acknowledgement must have clear labels, consequences and appropriate confirmation.
- **Show system state honestly.** Clearly distinguish live, delayed, stale, unavailable, failed, pending and completed states. Never imply real-time status when data is delayed.
- **Design for recovery.** Users must understand what failed, what has been saved and what they can safely do next.
- **Consistency is intentional.** Reuse established components, spacing, typography, interaction patterns and tokens. Do not create a new visual treatment for every screen.
- **Balanced visual personality.** Combine a restrained, modern visual style with warmth and approachability. Avoid both sterile enterprise-admin styling and playful/decorative styling that undermines trust in a safety tool.
- **Do not imitate generic AI dashboards.** Avoid default green gradients, excessive glassmorphism, glowing borders, random floating shapes and template-like card grids.

### GajaMitra visual identity and colour system

The approved direction is **Clean Neutral & Signal Blue**: balanced, modern, calm and operationally trustworthy. It should feel like a carefully designed public-safety tool, not a generic AI dashboard. Avoid the predictable green-first nature/SaaS aesthetic. The primary users are **field and control-room officers**; the villager experience shares the same brand identity but must be much simpler and focused on understanding and acting on a warning.

The approved brand palette is:

| Token | Colour | Intended use |
|---|---|---|
| `brand-900` / `slate-800` | `#1E293B` | Primary brand, navigation, headings and strong text |
| `surface-50` | `#F8FAFC` | Light-theme page background |
| `slate-600` | `#475569` | Secondary text, icons and neutral controls |
| `brand-600` / `signal-blue` | `#2563EB` | Primary actions, links, selected controls and focus accents |
| `terracotta-600` | `#C06A45` | Restrained supporting accent and non-risk highlights |
| `dark-bg` | `#0F172A` | Dark-theme application background |
| `dark-surface` | `#1E293B` | Dark-theme cards, navigation and raised surfaces |
| `dark-text` | `#F8FAFC` | Primary text in dark theme |
| `risk-low` | `#64748B` | Low-risk state |
| `risk-medium` | `#D69E2E` | Medium-risk state |
| `risk-high` | `#E47735` | High-risk state |
| `risk-critical` | `#C83E45` | Critical-risk state |
| `status-safe` | `#0F766E` | Resolved/safe operational state only |

Theme rules:

- Support both light and dark themes throughout the application. Light is the default for officer dashboards; dark is a fully supported user-selectable theme, not an afterthought.
- Respect the operating-system theme when the user has not selected a preference. Persist an explicit user choice using the project's established preference mechanism.
- Use theme tokens for backgrounds, surfaces, text, borders, overlays, charts, maps and focus states. Do not implement dark mode by simply inverting colours.
- Re-check contrast and semantic status visibility independently in both themes. Never assume a colour that works on a light surface works on a dark one.
- Keep brand colours separate from semantic risk colours. Signal Blue and Terracotta must never imply an elephant risk level.
- Use risk colours only for actual risk and status communication. Do not use red or amber as random decoration.
- Never communicate risk using colour alone. Include a clear text label and, where helpful, an icon or pattern.
- Check text, icon, border and interactive-state contrast against WCAG 2.2 AA requirements.
- Keep maps legible in both themes: use an appropriately toned basemap and ensure risk overlays, routes, boundaries and markers remain distinguishable.
- Do not use gradients as a substitute for hierarchy. Avoid decorative gradients, glowing borders and excessive glass effects.
- Centralise tokens as CSS variables or the project's established theme mechanism. Do not scatter raw colour values across components.
- Treat this palette as the approved default. Change it only when usability testing or the user provides a reason, and update all tokens and theme previews consistently.

### Layout, typography and visual hierarchy

- Establish a consistent spacing scale, typography scale, border-radius scale, elevation system and content-width rules.
- Use a small, purposeful type system. Distinguish page titles, section headings, body text, labels, metadata and numerical values clearly.
- Prefer readable typography over tiny, dense dashboard text.
- Use tabular numerals for changing values, timestamps and metrics where comparison matters.
- Use whitespace to group related information. Do not put every item inside a card.
- Use borders and shadows sparingly. Prefer clear alignment, spacing and typography to excessive containers.
- Keep primary navigation, page structure and key actions consistent across the application.
- Avoid arbitrary changes in card radius, button height, icon style or spacing between screens.
- Use icons consistently and pair unfamiliar or critical icons with visible text labels.
- Use real, relevant map and field imagery only when it improves understanding. Never add stock imagery merely to fill empty space.

### Operational dashboard and alert UX

- **Officer-first information architecture.** Prioritise active incidents, threat severity, location, observation freshness, workflow state and the next safe action. Secondary analytics and system diagnostics must not compete with live operational work.
- Provide a concise at-a-glance overview, then let officers open an alert for evidence, tracking history, confidence/uncertainty, policy explanation and audit details. Do not expose all technical detail at once.
- Design the villager-facing warning experience as a separate, simpler workflow: plain language, clear location and time, direct safety instructions, accessible typography and language choice. Do not reuse the officer dashboard layout for villagers.
- Use the same brand tokens and semantic risk meanings across officer and villager experiences, while adapting density, navigation and interaction complexity to each audience.
- Design the officer's primary workflow around situational awareness, alert assessment and safe action.
- Make the most urgent valid alert visually prominent without making every alert look urgent.
- Show the alert's risk level, location, observation time, freshness, confidence or uncertainty where available, and current workflow state.
- Distinguish observation time, analysis time, alert creation time and delivery time. Never present one as another.
- Make stale or missing data conspicuous. Show the last known update and explain when information is unavailable.
- Make map markers, list entries and alert details refer to the same analysis and status.
- Provide filters that match real tasks, with clear active-filter states and an easy way to reset them.
- Use charts only when they answer a real operational question. Label units, time ranges, sources and missing data.
- Do not fabricate sample metrics, live activity, map markers or alert counts in production UI.
- Confirm consequential actions such as approval or rejection with clear context. Prevent accidental repeated submissions and show the resulting state.
- Do not make a human approval step delay an automatic warning where the safety invariants prohibit that delay.
- Make empty, loading, offline, error, stale and permission-denied states deliberate parts of the design.

### Responsive and field-use requirements

- Design mobile-first for essential field workflows, then scale up for officer workstations.
- Ensure critical information and actions remain usable on small screens and with touch input.
- Use comfortable touch targets; aim for at least 44 by 44 CSS pixels for primary touch controls.
- Do not rely on hover to reveal essential information or actions.
- Support narrow screens without horizontal scrolling for normal workflows.
- Consider bright outdoor light, variable connectivity, slower devices and interrupted sessions.
- Preserve user-entered work during recoverable network interruptions where safe.
- Show connection state and data freshness when live updates are important.
- Avoid heavy animations, large assets and unnecessary client-side work.

### Sinhala, Tamil and English localisation

- Design for English, Sinhala and Tamil from the beginning; do not treat translation as a final cosmetic step.
- Use fonts with suitable Sinhala and Tamil glyph coverage, such as Noto Sans Sinhala and Noto Sans Tamil, with appropriate fallbacks.
- Test actual Sinhala and Tamil text, not just English placeholders.
- Allow for different text lengths, line heights, wrapping and button widths.
- Avoid fixed-height text containers that clip translated content.
- Use locale-aware date, time and number formatting.
- Do not concatenate translated sentence fragments. Use complete translation keys and proper localisation.
- Ensure critical risk labels and instructions remain unambiguous in all supported languages.
- Keep language switching accessible and preserve the user's selected language.

### Accessibility

- Target WCAG 2.2 AA for the frontend.
- Use semantic HTML before adding ARIA. ARIA must not contradict native semantics.
- Ensure all workflows can be completed with a keyboard.
- Provide visible focus indicators and logical focus order.
- Label form controls and associate validation messages with their fields.
- Announce important asynchronous status changes accessibly without overwhelming screen-reader users.
- Provide sufficient contrast, meaningful alt text and accessible chart summaries.
- Respect reduced-motion preferences.
- Never rely only on colour, position, sound or animation to communicate critical information.

### Interaction and motion

- Use motion to explain state changes, navigation or spatial relationships, not as decoration.
- Keep transitions short, restrained and consistent.
- Respect `prefers-reduced-motion`.
- Disable or avoid animations that could obscure urgent information or make the interface harder to use.
- Provide immediate feedback for actions and clear pending, success and failure states.
- Prevent duplicate submissions for consequential actions.
- Use destructive-action confirmation only where it meaningfully prevents mistakes; do not add confirmation dialogs to every routine action.

### Frontend engineering practices

- Use the existing React + TypeScript + Vite architecture and approved dependencies.
- Keep TypeScript strict. Do not introduce `any` to bypass type errors, especially at API and event boundaries.
- Separate API/data-fetching logic, domain state and presentational components.
- Keep components focused and reusable where there is a real repeated pattern. Avoid both giant components and excessive one-use abstractions.
- Keep business rules and safety decisions out of presentation components.
- Treat backend responses as untrusted runtime data; validate important external boundaries rather than assuming TypeScript types validate network responses.
- Handle loading, error, empty, stale and retry states explicitly.
- Clean up event listeners, subscriptions, timers and SSE connections when components unmount or dependencies change.
- Make SSE reconnection and duplicate-event handling consistent with backend event identifiers and contracts.
- Avoid unnecessary global state. Use local state for local interactions and the project's established server-state approach for remote data.
- Avoid unnecessary dependencies, premature optimisation and custom implementations of well-supported browser features.
- Do not use `dangerouslySetInnerHTML` for model-generated, user-generated or external content.
- Never put secrets or privileged credentials in frontend code.
- Test important workflows with the framework approved by the implementation plan.

### Visual quality gate

Before calling a frontend screen complete, review it at realistic desktop and mobile sizes.

Ask:

1. Can a first-time user understand the purpose of this screen quickly?
2. Is the most important information visually dominant?
3. Is the primary action obvious and unambiguous?
4. Is anything present that does not help the user's task?
5. Is the hierarchy clear without relying on colour?
6. Are spacing, alignment, typography and component behaviour consistent?
7. Are loading, empty, stale, offline, error and permission states designed?
8. Does it work with keyboard, touch, screen readers and translated text?
9. Does it remain readable in field conditions and on narrow screens?
10. Does it look like GajaMitra rather than a generic generated dashboard?

If any answer is no, improve the screen before considering it done. Do not claim a visual review was performed unless the relevant screen was actually inspected.

## Frontend testing

- Test important user workflows, not only whether components render.
- Test approval, rejection, acknowledgement and other consequential actions.
- Test loading, empty, error, stale, offline and permission-denied states.
- Test SSE disconnections, reconnections, duplicate events and stale updates.
- Test role-dependent visibility while remembering that backend authorization remains authoritative.
- Test responsive behaviour and keyboard interaction for critical workflows.
- Test Sinhala, Tamil and English layouts with realistic translated strings.
- Use the frontend test framework approved by the implementation plan; do not add a new framework without approval.
