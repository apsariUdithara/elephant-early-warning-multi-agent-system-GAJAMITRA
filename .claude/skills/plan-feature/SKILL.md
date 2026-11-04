---
name: plan-feature
description: 'Plan a GajaMitra feature or change before writing code - understand the request and affected code, map it to the implementation plan phases, identify services, contracts, database, messaging, safety invariants, failure cases and tests, and present a plan for the user''s approval. Use at the start of any substantial, cross-service, safety- or security-sensitive task.'
argument-hint: "<feature description> [GM-<n>]"
---

# Plan feature

**Output:** a plan the user can approve or change. No code is written in this skill.

## Steps
1. **Understand the ask.** Restate it in one or two sentences. Ask for the task number `GM-<n>` if it isn't given (never invent one). Ask only questions whose answer changes the plan.
2. **Locate it in the design.** Find the matching phase and section in `docs/IMPLEMENTATION_PLAN.md`. If the request contradicts the plan, say so and propose updating the plan in the same change.
3. **Read the real code** that will change and its callers and tests. Note what exists vs what the plan says will exist; don't assume planned files exist.
4. **Map the impact:**
   - services and files (Spring, Python, frontend, policy table)
   - contracts changed (REST, SSE, MCP tool, queue payload, `policy_table.json`) and both sides that must change together
   - database: new migration, constraints, indexes
   - messaging: new messages, idempotency keys, ack points
   - safety: which of the 12 invariants are touched and how they stay true
   - security and privacy: auth, inputs, secrets, personal data
   - localisation and UI: languages, SMS length, screens
5. **Failure cases:** timeouts, duplicates, crash mid-way, bad model output, missing data. Each ends in an explicit state.
6. **Tests:** list the tests to add, including one per touched invariant, and which existing suites must pass.
7. **Smallest complete slice:** cut anything speculative (YAGNI). Note dependencies that need the user's approval.

## Report (then wait for approval)
```
Plan: <title> (GM-<n>)    Branch: <type>/GM-<n>-<slug>
Goal: <1-2 sentences>
Plan reference: IMPLEMENTATION_PLAN Phase <n> §<section>
Steps: 1. … (each with files)
Contracts / DB / messaging changes: …
Invariants touched: <# → how preserved → test>
Failure handling: …
Tests: new … / existing suites …
Needs your decision: <list>   New dependencies: <list or none>
```
