"""McpToolClient: anything but a well-formed answer from Spring is a rejection or an error, never a success."""
import json

import anyio
import pytest
from mcp import types
from mcp.shared.exceptions import MCPError

from gajamitra.mcp_client import McpToolClient, McpUnavailable, _parse
from gajamitra.schemas import OfficerReport, VillageAlert

ALERT = VillageAlert(severity="warning", en="Elephants near the fence.", si="අලි වැටට ළඟයි.", ta="யானைகள் வேலிக்கு அருகில்.")


def client(answer):
    calls = []

    def call(name, arguments):
        calls.append((name, arguments))
        if isinstance(answer, Exception):
            raise answer
        return answer

    return McpToolClient(url="http://test/mcp", api_key="k", call=call), calls


def test_sends_the_ports_arguments_and_returns_springs_answer():
    tools, calls = client({"status": "accepted", "reason": None})
    assert tools.send_village_alert("a1", "a1:now:0", "NOW", ALERT) == {"status": "accepted"}
    assert calls == [("send_village_alert", {"analysis_id": "a1", "idempotency_key": "a1:now:0", "stage": "NOW",
                                             "alert": ALERT.model_dump()})]
    tools.notify_wildlife_officer("a1", "a1:now:report", OfficerReport(report="Three elephants"))
    assert calls[-1] == ("notify_wildlife_officer", {"analysis_id": "a1", "idempotency_key": "a1:now:report",
                                                     "report": "Three elephants"})


def test_a_rejection_keeps_its_reason():
    tools, _ = client({"status": "rejected", "reason": "no stored tracker risk for this analysis"})
    assert tools.record_assessment("a1", "high", {}) == {"status": "rejected",
                                                         "reason": "no stored tracker risk for this analysis"}


@pytest.mark.parametrize("failure", [McpUnavailable("down"), TimeoutError(), OSError("refused"),
                                     ExceptionGroup("tg", [OSError("refused")]), anyio.BrokenResourceError(),
                                     anyio.ClosedResourceError(), MCPError(code=-32603, message="internal")])
def test_an_unreachable_spring_is_a_rejection_not_a_success(failure):  # invariant 10
    tools, _ = client(failure)
    result = tools.send_village_alert("a1", "a1:now:0", "NOW", ALERT)
    assert result["status"] == "rejected"


@pytest.mark.parametrize("answer", [None, [], "accepted", {"ok": True}, {"status": 1}])
def test_a_malformed_answer_is_a_rejection(answer):
    tools, _ = client(answer)
    assert tools.send_village_alert("a1", "a1:now:0", "NOW", ALERT)["status"] == "rejected"


def test_failed_reads_raise_instead_of_returning_defaults():
    tools, _ = client({"name": "Galgamuwa"})  # no requires_approval
    with pytest.raises(McpUnavailable):
        tools.get_site_procedures("s1")
    tools, _ = client({"not": "a list"})
    with pytest.raises(McpUnavailable):
        tools.get_site_history("s1", 14)


def test_no_api_key_fails_at_once():
    with pytest.raises(McpUnavailable):
        McpToolClient(url="http://test/mcp", api_key="")


def test_parse_reads_structured_or_text_content_and_rejects_errors():
    text = types.TextContent(type="text", text=json.dumps({"status": "accepted"}))
    assert _parse(types.CallToolResult(content=[text])) == {"status": "accepted"}
    assert _parse(types.CallToolResult(content=[], structured_content={"result": [{"risk": "high"}]})) == [
        {"risk": "high"}]
    with pytest.raises(McpUnavailable):
        _parse(types.CallToolResult(content=[text], is_error=True))
    with pytest.raises(McpUnavailable):
        _parse(types.CallToolResult(content=[types.TextContent(type="text", text="oops")]))
