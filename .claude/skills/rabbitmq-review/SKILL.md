---
name: rabbitmq-review
description: 'Review GajaMitra''s RabbitMQ messaging (queues analysis.requests / analysis.resume / analysis.events, dead-letter queue, Spring AMQP and aio-pika consumers) for at-least-once handling, acknowledgement after persistence, idempotency, retries, ordering assumptions, publish-after-commit and consumer recovery. Use when a producer, consumer, queue declaration or message schema changes.'
argument-hint: "[files or diff range; default: messaging-related changes]"
---

# RabbitMQ review

**Purpose:** RabbitMQ delivers at least once and can redeliver after crashes. Every message path must be safe to repeat and must never lose a warning.

## Inputs
- Backend messaging package (planned: `backend/src/main/java/**/messaging/**`), the Python worker (planned: `ai-service/gajamitra/worker.py`), `docker-compose.yml`.
- `.claude/rules/messaging.md`, plan Phase 3 "Messaging" and Phase 4.
- Not built yet at the time this skill was written; check what exists first and say so.

## Steps
1. **Topology:** list queues, exchanges, routing keys, DLQ bindings and who declares them. Declarations must be idempotent and match on both sides.
2. **Publishing:** never inside a database transaction; publish after commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`), and a scheduler republishes `QUEUED` analyses older than the stale threshold. Consider a transactional outbox where loss matters.
3. **Consuming:** ack only after the effect is persisted (checkpoint saved or transaction committed). Python: separate channels, prefetch 1 each, ack when the graph yields. Nack to DLQ only for unrecoverable errors; transient errors retry with capped backoff.
4. **Duplicates:** every consumer dedupes (`event_id` stored, or idempotency key with UNIQUE constraint). Walk through the same message arriving twice and arriving after a later one.
5. **Ordering:** no logic depends on order unless the design guarantees it. Status updates use conditional updates, not "last write wins".
6. **Long work:** blocking SAM/LLM calls don't stall the event loop (`asyncio.to_thread` or async clients) and the SAM timeout is below RabbitMQ `consumer_timeout`.
7. **Failure visibility:** a stuck queue, a DLQ message or a dead consumer is observable (metrics, logs with `analysisId`/`eventId`). Note invariant 10 / risk R7.
8. **Tests:** Testcontainers RabbitMQ tests for duplicate delivery, redelivery after crash, DLQ routing, invalid payload. Run via `/test-and-verify`.

## Report
```
RabbitMQ review: <scope>
Verdict: OK | CHANGES NEEDED
Message paths: | queue | producer | consumer | ack point | dedupe key | DLQ | test |
Findings: [blocker|major|minor] file:line: problem → invariant (4/5/6/10) → fix
Tests run: <command> → <result>
```
