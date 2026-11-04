---
name: database-review
description: 'Review GajaMitra''s PostgreSQL schema and data access (Flyway migrations, JPA entities, repositories, constraints, indexes, transactions, idempotency keys, LangGraph checkpoint schema separation, query performance). Use when a migration, entity, repository or transactional service method changes.'
argument-hint: "[files or diff range; default: persistence-related changes]"
---

# Database review

**Purpose:** the database enforces the rules code can get wrong: uniqueness, references and valid states.

## Inputs
- Flyway migrations (planned: `backend/src/main/resources/db/migration/`), entities and repositories under `backend/src/main/java/`.
- Data model in plan Phase 3 and `docs/architecture/05-data-model.svg`; `.claude/rules/database.md`.

## Steps
1. **Migrations:** Flyway only, never `ddl-auto=update`. Run `git log --diff-filter=A --name-only -- backend/src/main/resources/db/migration` and confirm no already-committed migration was edited. New migration numbering is sequential.
2. **Constraints in the database:** every UNIQUE rule in the plan exists as a constraint (`alert.idempotency_key`, `officer_report.idempotency_key`, `alert_delivery (alert_id, subscriber_id)`, `app_user.username`). Foreign keys exist. Enum-like columns have CHECK constraints or are mapped enums.
3. **State machines:** `analysis.status` and `approval_request.status` change only through entity methods; approval decisions use conditional updates (`WHERE status = 'PENDING'`) so the officer and the timeout job can't both win.
4. **Transactions:** `@Transactional` on service methods; no transaction held open across Meta, Twilio, MCP or RabbitMQ calls; side effects (publish, SMS) after commit.
5. **Queries:** pagination on list endpoints; no N+1 (check fetch joins / entity graphs); indexes on foreign keys and on columns used to find stale `QUEUED` analyses and expiring approvals.
6. **Schema ownership:** Flyway owns schema `public`; LangGraph owns schema `langgraph`. Neither touches the other's tables.
7. **Privacy:** phone numbers and footage paths are not exposed in DTOs or logs; retention rules follow the plan.
8. **Tests:** Testcontainers PostgreSQL (never an in-memory database) covers constraints and the conditional updates. Run via `/test-and-verify`.

## Report
```
Database review: <scope>
Verdict: OK | CHANGES NEEDED
Migrations: <new files; any edited committed migration = blocker>
Findings: [blocker|major|minor] file:line: problem → fix
Tests run: <command> → <result>
```
