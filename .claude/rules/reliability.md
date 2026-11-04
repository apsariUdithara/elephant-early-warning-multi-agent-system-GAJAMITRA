---
paths:
  - "backend/**"
  - "ai-service/**"
---

# Failure handling and resilience

- Missing, stale, corrupt or unavailable video frames must never be interpreted as evidence that no elephant is present.
- Represent camera disconnection, tracker failure, incomplete analysis, low-confidence detection and unavailable dependencies as explicit states.
- Define the operational response for each safety-relevant failure in the implementation plan. Do not invent a new public-safety policy in code.
- Every external call must have a timeout and a documented failure path.
- Retry only operations that are safe to repeat or protected by idempotency. Use capped retries, backoff and jitter where appropriate.
- Consumers must tolerate duplicate, delayed and out-of-order messages.
- Failed SMS delivery must remain distinguishable from successful delivery. Never mark an alert as delivered merely because a send request was accepted.
- Preserve the original analysis and audit history when retrying or recovering failed work.
- Monitor stale analyses, unprocessed messages, queue backlog and incomplete dispatch.
- Never silently downgrade a safety-critical failure into a successful or safe result.
- Changes to failure behaviour that could affect public safety require user approval and tests for both normal and failure paths.

# Observability and operations

- Propagate correlation IDs across Spring Boot, Python, RabbitMQ and MCP calls.
- Include `analysisId` and `eventId` in relevant structured logs and traces.
- Use structured logging; do not leave ad hoc debug prints in production code.
- Track meaningful operational metrics, including analysis latency, processing failures, stale jobs, queue backlog, camera availability and SMS delivery outcomes.
- Provide appropriate liveness and readiness endpoints. Liveness must not fail solely because an optional external dependency is unavailable.
- Distinguish application health from dependency health.
- Ensure logs and metrics do not expose footage, credentials, full phone numbers or sensitive incident details.
- Make operational failures actionable: include a safe error category and correlation identifier, not stack traces or secrets in user-facing messages.
