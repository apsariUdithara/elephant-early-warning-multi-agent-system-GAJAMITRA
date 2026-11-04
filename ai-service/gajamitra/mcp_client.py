"""ToolClient over MCP: the agent graph's side effects go to Spring's /mcp endpoint (plan Phase 4, step 2).

Spring decides; this client only carries calls and reads answers. Anything other than a well-formed answer (an error,
a timeout, a malformed result) comes back as {"status": "rejected", "reason": ...}, so the graph fails the run loudly
instead of treating it as sent (invariant 10). Reads that fail (site procedures, history) raise, for the same reason.
"""
import asyncio
import json
import os

import httpx2
from mcp import ClientSession
from mcp.client.streamable_http import create_mcp_http_client, streamable_http_client

# Longest one tool call may take, connection included. Sending SMS happens after Spring commits, inside the call.
CALL_TIMEOUT_S = 30.0


class McpUnavailable(RuntimeError):
    """Spring's MCP endpoint couldn't be reached or answered with something unusable."""


def _parse(result):
    """A CallToolResult -> the JSON value Spring returned."""
    if result.is_error:
        text = " ".join(getattr(c, "text", "") for c in result.content)[:200]
        raise McpUnavailable(f"tool error: {text}")
    if isinstance(result.structured_content, (dict, list)):
        value = result.structured_content
        # Spring wraps non-object results (a list) as {"result": [...]}
        return value["result"] if isinstance(value, dict) and set(value) == {"result"} else value
    texts = [c.text for c in result.content if getattr(c, "type", None) == "text"]
    if not texts:
        raise McpUnavailable("tool answered without content")
    try:
        return json.loads(texts[0])
    except json.JSONDecodeError as e:
        raise McpUnavailable(f"tool answered with non-JSON text: {texts[0][:80]!r}") from e


async def _call_once(url, api_key, name, arguments):
    # ponytail: one MCP session per call (about five per analysis); keep one session open once the worker is async.
    http = create_mcp_http_client(headers={"X-Api-Key": api_key}, timeout=httpx2.Timeout(CALL_TIMEOUT_S))
    async with http, streamable_http_client(url, http_client=http) as (read, write, *_), \
            ClientSession(read, write) as session:
        await session.initialize()
        return _parse(await session.call_tool(name, arguments, read_timeout_seconds=CALL_TIMEOUT_S))


class McpToolClient:
    """The ToolClient port (gajamitra.ports) backed by Spring's MCP server."""

    def __init__(self, url=None, api_key=None, call=None):
        self.url = url or os.environ.get("MCP_URL", "http://localhost:8080/mcp")
        api_key = api_key if api_key is not None else os.environ.get("MCP_API_KEY", "")
        if not api_key:
            raise McpUnavailable("MCP_API_KEY is not set")
        # call(name, arguments) -> parsed JSON. Tests pass a fake; production opens an MCP session.
        self._call = call or (lambda name, arguments: asyncio.run(
            asyncio.wait_for(_call_once(self.url, api_key, name, arguments), CALL_TIMEOUT_S + 5)))

    def _side_effect(self, name, arguments):
        try:
            answer = self._call(name, arguments)
        except (McpUnavailable, OSError, TimeoutError, httpx2.HTTPError, ExceptionGroup) as e:
            return {"status": "rejected", "reason": f"MCP call failed ({type(e).__name__})"}
        if not isinstance(answer, dict) or not isinstance(answer.get("status"), str):
            return {"status": "rejected", "reason": "malformed tool answer"}
        return {k: v for k, v in answer.items() if v is not None}

    def record_assessment(self, analysis_id, risk, summary):
        return self._side_effect("record_assessment", {"analysis_id": analysis_id, "risk": risk, "summary": summary})

    def send_village_alert(self, analysis_id, idempotency_key, stage, alert):
        return self._side_effect("send_village_alert", {"analysis_id": analysis_id, "idempotency_key": idempotency_key,
                                                        "stage": stage, "alert": alert.model_dump()})

    def notify_wildlife_officer(self, analysis_id, idempotency_key, report):
        return self._side_effect("notify_wildlife_officer", {"analysis_id": analysis_id,
                                                             "idempotency_key": idempotency_key,
                                                             "report": report.report})

    def get_site_procedures(self, site_id):
        answer = self._call("get_site_procedures", {"site_id": site_id})
        if not isinstance(answer, dict) or not isinstance(answer.get("requires_approval"), bool):
            raise McpUnavailable("malformed site procedures")
        return answer

    def get_site_history(self, site_id, days):
        answer = self._call("get_site_history", {"site_id": site_id, "days": days})
        if not isinstance(answer, list) or not all(isinstance(i, dict) for i in answer):
            raise McpUnavailable("malformed site history")
        return answer
