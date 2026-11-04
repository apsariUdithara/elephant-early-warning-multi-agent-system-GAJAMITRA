---
paths:
  - "backend/src/test/**"
  - "ai-service/tests/**"
  - "frontend/src/**"
  - ".claude/hooks/**"
---

# Testing

- **Test pyramid.** Many fast unit tests, a few Testcontainers integration tests, and one end-to-end run per the plan's Verification section.
- **Each invariant in `CLAUDE.md` has at least one test that fails if the invariant breaks.** Add or update that test whenever you touch code connected to an invariant.
- Python: `pytest`. The graph is tested with a fake chat model, and the `StubToolClient` records the calls it receives. Java: JUnit 5 + Mockito for units, Testcontainers (Postgres, RabbitMQ) for integration. Don't use an in-memory database in place of Postgres.
- A bug fix comes with a test that reproduces the bug. A test that passes without the fix doesn't prove anything.
- Tests are deterministic: no network, no real clock (inject a `Clock` / pass `now`), no sleeping.
