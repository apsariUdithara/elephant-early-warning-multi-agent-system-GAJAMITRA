---
paths:
  - "ai-service/**"
---

# Python (ai-service/): Python 3.11

- **Functions first.** Stateless logic (`track.py`, `policy.py`, prompts) is plain functions and constants. Use a class only when it holds state or implements a port.
- **Ports are `typing.Protocol`s** (for example `ToolClient` with `StubToolClient` for the CLI and tests and `McpToolClient` for production). Pass them in as arguments; no module-level singletons except a lazily created, cached client (`@functools.cache def meta()`).
- **Every boundary is typed:** Pydantic models for graph state, LLM structured output, messages and MCP payloads. Type hints on every public function.
- **Async correctness:** the worker is asyncio. Never make a blocking call on the event loop; use async clients or `asyncio.to_thread`.
- **Calibration constants** (`HIGH_ETA_S`, `MIN_CLOSING_PX_S`, `NEAR_BODIES`, …) are named UPPER_CASE values at the top of their module, each with a comment saying what it controls. Don't bury thresholds inside logic.
- Lint and format with `ruff`. No bare `except:`. Catch narrowly, and either handle the error or publish a `failed` event with a reason. Never silently swallow it.
