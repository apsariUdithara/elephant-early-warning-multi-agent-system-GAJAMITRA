---
paths:
  - "backend/src/main/java/**/messaging/**"
  - "backend/src/main/java/**/mcp/**"
  - "ai-service/gajamitra/worker.py"
  - "docker-compose.yml"
---

# Messaging and transactions

- RabbitMQ delivers at least once. Every consumer must handle duplicates (dedupe on `event_id` or an idempotency key).
- Acknowledge a message only after its effect is persisted: a checkpoint saved, or a database transaction committed.
- **Database plus broker writes:** never publish to RabbitMQ inside a database transaction.
  - Publish after commit with `@TransactionalEventListener(phase = AFTER_COMMIT)`.
  - Make the scheduler republish any `QUEUED` analysis that's older than the stale threshold, so a failed publish recovers.
  - SMS delivery also happens after commit.
- Set an explicit timeout on every external call (Meta API, Twilio, MCP, HTTP). Retry only operations that are safe to repeat, with a capped number of retries and backoff.

## Contracts and failed messages

- Treat HTTP APIs, RabbitMQ messages and MCP schemas as versioned contracts.
- Check all consumers before changing a request, response, event or tool schema.
- Prefer additive, backward-compatible changes. Coordinate unavoidable breaking changes across all affected services and update contract tests.
- Where reliable database-to-broker publication is required, evaluate a transactional outbox rather than assuming an after-commit callback cannot lose an event. Follow the approved implementation plan.
- Never silently discard failed messages. Define retry, dead-letter, inspection and replay behaviour.
