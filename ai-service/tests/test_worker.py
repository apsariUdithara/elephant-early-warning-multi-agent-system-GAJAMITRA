"""The worker's message handling with the real graph, stub tools and a fake model (no RabbitMQ, no API calls)."""
import pytest
from langgraph.checkpoint.memory import InMemorySaver
from test_graph import FakeChat, responder_reply, summary, verdict

from gajamitra.graph import build_graph
from gajamitra.ports import StubToolClient
from gajamitra.worker import BadMessage, Worker

BOUNDARY = [0, 0.5, 1, 0.5, 0.5, 0.9]
AID = "7d5e8f8a-1d2b-4c3e-9f00-000000000001"


def worker(risk="high", chat=None, perceive=None):
    tools = StubToolClient({"s1": {"name": "Galgamuwa", "requires_approval": True}})
    chat = chat or FakeChat([responder_reply("danger")], [verdict()])
    graph = build_graph(tools, chat, InMemorySaver(),
                        perceive=perceive or (lambda path, boundary: (summary(risk), ["data:img"])))
    events = []
    return Worker(graph, events.append), events, tools


def request(aid=AID):
    return {"schema_version": 1, "analysis_id": aid, "site_id": "s1", "video_path": "/data/v.mp4", "boundary": BOUNDARY}


def resume_message(decision, aid=AID):
    return {"schema_version": 1, "analysis_id": aid, "decision": decision, "officer": None, "note": None}


def types(events):
    return [e["type"] for e in events]


def test_a_request_reports_started_then_waits_for_the_officer():
    w, events, tools = worker()
    w.handle_request(request())
    assert types(events) == ["started", "approval_required"]
    assert events[1]["escalation"][0]["severity"] == "danger"
    assert [d[1] for d in tools.deliveries] == ["warning", "officer"]  # the warning went out before anyone was asked


@pytest.mark.parametrize("decision, escalated", [("approve", True), ("timeout", True), ("reject", False)])
def test_a_resume_finishes_the_run_and_always_reports_completed(decision, escalated):
    w, events, tools = worker()
    w.handle_request(request())
    w.handle_resume(resume_message(decision))
    assert types(events)[-1] == "completed"
    assert events[-1]["result"]["approval"] == decision
    assert (f"{AID}:escalation:0", "danger") in tools.deliveries if escalated else len(tools.deliveries) == 2


def test_a_duplicate_resume_for_a_finished_run_reports_completed_again_and_sends_nothing():  # invariant 9
    w, events, tools = worker()
    w.handle_request(request())
    w.handle_resume(resume_message("approve"))
    sent = list(tools.deliveries)
    w.handle_resume(resume_message("approve"))
    assert types(events)[-2:] == ["completed", "completed"]
    assert tools.deliveries == sent


def test_a_duplicate_request_continues_from_the_checkpoint_without_started():  # invariant 6
    w, events, tools = worker()
    w.handle_request(request())
    w.handle_request(request())
    assert types(events) == ["started", "approval_required", "approval_required"]
    assert [d[1] for d in tools.deliveries] == ["warning", "officer"]  # nothing sent twice


def test_any_failure_is_reported_as_failed_with_a_reason():  # invariant 10
    def broken(path, boundary):
        raise RuntimeError("SAM timed out")

    w, events, _ = worker(perceive=broken)
    w.handle_request(request())
    assert types(events) == ["started", "failed"]
    assert "SAM timed out" in events[-1]["reason"]


def test_a_resume_without_a_checkpoint_is_reported_failed():
    w, events, _ = worker()
    w.handle_resume(resume_message("approve", aid="7d5e8f8a-1d2b-4c3e-9f00-0000000000ff"))
    assert types(events) == ["failed"]


@pytest.mark.parametrize("message", [
    {}, {"schema_version": 2, "analysis_id": "a1"},
    {**request(), "boundary": [0, 1]}, {**request(), "video_path": ""}, {**request(), "analysis_id": "a1"}])
def test_messages_off_contract_are_refused(message):
    w, events, _ = worker()
    with pytest.raises(BadMessage):
        w.handle_request(message)
    with pytest.raises(BadMessage):
        w.handle_resume({**resume_message("approve"), "decision": "maybe"})
    assert events == []


def test_a_failed_publish_after_the_run_is_redelivered_never_reported_failed():  # GM-22 review
    w, events, tools = worker()

    def publish(event):
        if event["type"] == "approval_required":
            raise ConnectionError("broker down")
        events.append(event)

    w.publish = publish
    with pytest.raises(ConnectionError):  # the message is nacked and redelivered
        w.handle_request(request())
    assert "failed" not in types(events)
    w.publish = events.append
    w.handle_request(request())  # redelivery: the run is at the gate, so it reports that again
    assert types(events)[-1] == "approval_required"
    assert [d[1] for d in tools.deliveries] == ["warning", "officer"]


def jpeg_worker(frames_dir):
    import base64

    url = "data:image/jpeg;base64," + base64.b64encode(b"\xff\xd8\xff\xe0fake-jpeg").decode()
    tools = StubToolClient({"s1": {"name": "Galgamuwa", "requires_approval": True}})
    graph = build_graph(tools, FakeChat([responder_reply("danger")], [verdict()]), InMemorySaver(),
                        perceive=lambda path, boundary: (summary("high"), [url, "data:text/plain;base64,xx"]))
    events = []
    return Worker(graph, events.append, frames_dir=frames_dir), events


def test_the_frames_the_agents_looked_at_are_kept_for_the_approval_screen(tmp_path):
    w, events = jpeg_worker(tmp_path)
    w.handle_request(request())
    assert types(events)[-1] == "approval_required"
    assert (tmp_path / AID / "0.jpg").read_bytes().startswith(b"\xff\xd8")
    assert not (tmp_path / AID / "1.jpg").exists()  # only real JPEG data URLs are written


def test_a_disk_problem_never_delays_the_approval_event(tmp_path):
    blocker = tmp_path / "not-a-folder"
    blocker.write_text("x")
    w, events = jpeg_worker(blocker)  # frames_dir is a file: every write fails
    w.handle_request(request())
    assert types(events)[-1] == "approval_required"  # evidence is best effort; the escalation still waits for the officer
