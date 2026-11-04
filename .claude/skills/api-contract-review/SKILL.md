---
name: api-contract-review
description: 'Review GajaMitra''s contracts between components (Spring REST endpoints and DTOs, SSE events, MCP tool schemas, RabbitMQ message payloads, policy_table.json) for validation, status codes, ProblemDetail errors, backward compatibility, schema_version use and service-ownership boundaries. Also checks that both sides of a contract change together. Use when a controller, DTO, MCP tool, message payload or the policy table changes.'
argument-hint: "[files or diff range; default: branch diff vs main]"
---

# API and integration contract review

**Purpose:** the services only talk through contracts. A contract that changes on one side breaks the warning pipeline at runtime, not at compile time.

## Inputs
- Changed controllers, DTO records, MCP tools (`backend/src/main/java/**`), message payload models (Python `schemas.py`, Java messaging package), the frontend API types (`frontend/src/api/`), `policy/policy_table.json`.
- Plan Phase 3 (REST API, MCP tools, messaging) and the "Contracts" rules in `CLAUDE.md` and `.claude/rules/backend.md`.
- Most of these are planned, not built yet. Review what exists; for planned items compare against the plan.

## Steps
1. **Inventory:** list every contract touched: endpoint (method + path), SSE event type, MCP tool, queue + payload, policy table. Note the producer and every consumer.
2. **Both sides:** for each contract, confirm producer and all consumers changed in the same diff (Java ↔ Python ↔ TypeScript). One-sided change → blocker.
3. **Compatibility:** fields only added, never renamed or removed without a `schema_version` bump. New required fields have a default or a version bump. Enum values only added, and consumers handle unknown values.
4. **REST:** correct method and status codes (`202` for queued analysis, `400/422` validation, `404`, `409` for conditional approval updates); Bean Validation on every request DTO; errors as `ProblemDetail` from one `@RestControllerAdvice`; no JPA entity exposed; authorization on every protected endpoint (role checks, not hidden buttons).
5. **MCP tools:** input validated; internal API key required; side-effect tools take an idempotency key; `send_village_alert` re-checks severity against stored risk and rejects when no risk is stored (invariant 3).
6. **Ownership:** Python never writes Spring's tables or sends SMS; the frontend never decides risk or severity.
7. **Tests:** contract tests or integration tests cover the change on both sides. Run via `/test-and-verify`.

## Constraints
- Read-only. Don't invent endpoints or fields: if the plan and the code disagree, report it.

## Report
```
Contract review: <scope>
Verdict: COMPATIBLE | BREAKING (needs version bump) | INCONSISTENT
| Contract | Producer | Consumers | Change | Compatible? | Both sides updated? |
Findings: [blocker|major|minor] file:line: problem → fix
Tests run: <command> → <result>
```
