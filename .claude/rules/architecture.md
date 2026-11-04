---
paths:
  - "backend/**"
  - "ai-service/**"
  - "policy/**"
---

# Design patterns used in this system

Use these where they appear. Introduce a new one only when it solves a problem the code has now.

| Pattern | Where | Rule |
|---|---|---|
| **Strategy** | `SmsGateway` → `TwilioSmsGateway` / `LoggingSmsGateway`, chosen by `gajamitra.sms.enabled` | Selected by configuration (`@ConditionalOnProperty`), never by `if` scattered through the code |
| **Adapter (port)** | Meta API client (`perception.py`), MCP tool client, Twilio | One adapter per external system. Tests use a fake that implements the same port |
| **State machine** | `Analysis.status` (`QUEUED → RUNNING → AWAITING_APPROVAL → COMPLETED / FAILED`), `ApprovalRequest.status` | Status changes only through entity methods (`markRunning()`, `approve(officer, note)`) that reject illegal transitions. No public `setStatus` |
| **Pipeline / graph** | LangGraph nodes: perceive → load_context → responder → verifier → policy_guard → dispatch → approval_gate → dispatch → finish | A node receives state and returns only the fields it changes. Nodes stay small and are tested one at a time |
| **Guard / policy object** | `policy.py`, the Java severity re-check, `policy_table.json` | Table-driven. Python and Java test against the same table |
| **Idempotent receiver** | MCP side-effect tools, event consumers | Key with a UNIQUE constraint; a repeat call returns the stored result |
| **Human in the loop (interrupt / resume)** | `approval_gate` | No side effects before `interrupt()`, because LangGraph re-runs the node on resume |
| **Observer (SSE)** | Spring pushes analysis events to dashboard clients | Emitters live in one registry component and are removed on completion, timeout or error |
