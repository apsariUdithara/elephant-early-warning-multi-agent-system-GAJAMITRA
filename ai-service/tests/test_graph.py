"""LangGraph pipeline routing and safety invariants, with a fake chat model and the stub tools. No API calls."""
import json

import pytest
from langgraph.checkpoint.memory import InMemorySaver

from gajamitra.graph import build_graph, resume, start
from gajamitra.ports import StubToolClient
from gajamitra.prompts import VERIFIER
from gajamitra.schemas import GuardDecision


def summary(risk, herd=2):
    if risk == "none":
        return {"herd_size": 0, "risk": "low", "elephants": []}
    return {"risk": risk, "herd_size": herd,
            "elephants": [{"id": i, "dist_px": 30, "eta_s": 20.0, "approaching": True} for i in range(herd)]}


def responder_reply(severity="danger", behaviour="warning"):
    return json.dumps({
        "assessment": {"behaviour": behaviour, "calves": False, "reasoning": "Ears spread; walking to the fence."},
        "proposal": {"alerts": [{"severity": severity, "en": "Two elephants at the east fence. Stay indoors.",
                                 "si": "නැගෙනහිර වැට අසල අලි දෙදෙනෙක්.", "ta": "கிழக்கு வேலியில் இரண்டு யானைகள்."}],
                     "officer_report": {"report": "Two elephants at the east fence, approaching."}}})


def verdict(decision="approve", aggression=False, reasons=()):
    return json.dumps({"decision": decision, "reasons": list(reasons), "aggression": aggression})


class FakeChat:
    """Replies by role (responder or verifier), in order. Records which role was asked, for ordering checks."""

    def __init__(self, responder=(), verifier=(), log=None):
        self.replies = {"responder": list(responder), "verifier": list(verifier)}
        self.asked, self.log = [], log

    def __call__(self, messages):
        role = "verifier" if messages[0]["content"] is VERIFIER else "responder"
        self.asked.append(role)
        if self.log is not None:
            self.log.append(("chat", role))
        return self.replies[role].pop(0)


def run(risk, chat, requires_approval=True, analysis_id="a1"):
    tools = StubToolClient({"s1": {"name": "Galgamuwa", "requires_approval": requires_approval}})
    graph = build_graph(tools, chat, InMemorySaver(), perceive=lambda path, boundary: (summary(risk), ["data:img"]))
    result = start(graph, analysis_id, "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])
    return graph, tools, result


def sent(tools):
    return [d for d in tools.deliveries]


def test_high_risk_warns_now_and_waits_for_approval_only_for_the_escalation():
    # Invariant 2: the warning and the officer report go out before the graph waits.
    _, tools, result = run("high", FakeChat([responder_reply("danger")], [verdict()]))
    assert "__interrupt__" in result
    assert sent(tools) == [("a1:now:0", "warning"), ("a1:now:report", "officer")]


@pytest.mark.parametrize("answer, escalated", [("approve", True), ("timeout", True), ("reject", False)])
def test_approval_answers(answer, escalated):
    # Invariant 9: a timeout sends the escalation, like an approval.
    graph, tools, _ = run("high", FakeChat([responder_reply("danger")], [verdict()]))
    result = resume(graph, "a1", answer)
    assert "__interrupt__" not in result and result["approval"] == answer
    assert (("a1:escalation:0", "danger") in sent(tools)) == escalated


def test_resume_does_not_resend_the_warning():
    # Invariants 4 and 6: resuming continues from the checkpoint; nothing sent before the interrupt is repeated.
    graph, tools, _ = run("high", FakeChat([responder_reply("danger")], [verdict()]))
    resume(graph, "a1", "approve")
    assert sent(tools) == [("a1:now:0", "warning"), ("a1:now:report", "officer"), ("a1:escalation:0", "danger")]


def test_invalid_approval_answer_is_rejected():
    graph, _, _ = run("high", FakeChat([responder_reply("danger")], [verdict()]))
    with pytest.raises(ValueError):
        resume(graph, "a1", "maybe")


@pytest.mark.parametrize("requires_approval", [True, False])
def test_critical_never_waits_for_approval(requires_approval):
    _, tools, result = run("critical", FakeChat([responder_reply("warning")], [verdict()]), requires_approval)
    assert "__interrupt__" not in result
    assert sent(tools) == [("a1:now:0", "danger"), ("a1:now:report", "officer")]


def test_site_without_approval_sends_the_escalation_straight_after_the_warning():
    _, tools, result = run("high", FakeChat([responder_reply("danger")], [verdict()]), requires_approval=False)
    assert "__interrupt__" not in result and result["approval"] == "not_required"
    assert ("a1:escalation:0", "danger") in sent(tools)


def test_no_elephants_runs_no_model_and_sends_nothing():
    chat = FakeChat()
    _, tools, _ = run("none", chat)
    assert chat.asked == [] and sent(tools) == []
    assert tools.calls == [("record_assessment", "a1", "low")]


def test_risk_is_recorded_before_any_model_runs():
    # Invariant 3: Spring must already hold the risk when the first alert arrives.
    log = []
    chat = FakeChat([responder_reply()], [verdict()], log=log)
    tools = StubToolClient({"s1": {"name": "X", "requires_approval": False}})
    real = tools.record_assessment
    tools.record_assessment = lambda *a: (log.append(("record_assessment",)), real(*a))[1]
    graph = build_graph(tools, chat, InMemorySaver(), perceive=lambda p, b: (summary("medium"), []))
    start(graph, "a2", "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])
    assert log[0] == ("record_assessment",) and log[1] == ("chat", "responder")


def test_revise_loop_is_bounded():
    chat = FakeChat([responder_reply("warning")] * 3, [verdict("revise", reasons=["say where"])] * 3)
    _, tools, result = run("medium", chat, requires_approval=False)
    assert chat.asked.count("responder") == 3  # first attempt + 2 revisions
    assert any("after 2 revisions" in n for n in result["notes"])
    assert sent(tools) == [("a1:now:0", "warning")]


def test_revise_then_approve_uses_the_revised_proposal():
    chat = FakeChat([responder_reply("warning"), responder_reply("warning")],
                    [verdict("revise", reasons=["name the field"]), verdict("approve")])
    _, tools, _ = run("medium", chat, requires_approval=False)
    assert chat.asked == ["responder", "verifier", "responder", "verifier"]
    assert sent(tools) == [("a1:now:0", "warning")]


@pytest.mark.parametrize("risk, level", [("medium", "warning"), ("critical", "danger")])
def test_verifier_reject_falls_back_to_the_template_alert(risk, level):
    # Invariant 1: rejecting the responder's text never silences the warning.
    _, tools, result = run(risk, FakeChat([responder_reply()], [verdict("reject", reasons=["not elephants"])]),
                           requires_approval=False)
    assert ("a1:now:0", level) in sent(tools)
    assert any("template" in n for n in result["notes"])


def test_invalid_responder_and_broken_verifier_still_warn():
    _, tools, result = run("high", FakeChat(["not json", "still not json"], ["garbage"]), requires_approval=True)
    assert sent(tools)[:2] == [("a1:now:0", "warning"), ("a1:now:report", "officer")]
    assert any("no valid proposal" in n for n in result["notes"])


def test_unreadable_verifier_reply_does_not_block_the_guard():
    _, tools, result = run("medium", FakeChat([responder_reply("warning")], ["nonsense"]), requires_approval=False)
    assert sent(tools) == [("a1:now:0", "warning")]
    assert any("verifier unavailable" in n for n in result["notes"])


def test_verifier_flagged_aggression_notifies_the_officer_at_medium():
    _, tools, _ = run("medium", FakeChat([responder_reply("warning")], [verdict(aggression=True)]),
                      requires_approval=False)
    assert ("a1:now:report", "officer") in sent(tools)


def test_stub_tools_enforce_the_contract():
    from gajamitra.schemas import VillageAlert
    tools = StubToolClient()
    alert = VillageAlert(severity="warning", en="x", si="අලි", ta="யானை")
    assert tools.send_village_alert("z", "z:now:0", "NOW", alert)["status"] == "rejected"   # no stored risk
    tools.record_assessment("z", "medium", {})
    danger = alert.model_copy(update={"severity": "danger"})
    assert tools.send_village_alert("z", "z:now:1", "NOW", danger)["status"] == "rejected"  # above medium's limit
    assert tools.send_village_alert("z", "y:now:9", "NOW", alert)["status"] == "rejected"   # key of another analysis
    assert tools.send_village_alert("z", "z:escalation:0", "NOW", alert)["status"] == "rejected"  # wrong stage
    tools.send_village_alert("z", "z:now:2", "NOW", alert)
    tools.send_village_alert("z", "z:now:2", "NOW", alert)                                 # same key twice
    assert tools.deliveries == [("z:now:2", "warning")]
    tools.record_assessment("h", "high", {})
    assert tools.send_village_alert("h", "h:now:0", "NOW", danger)["status"] == "rejected"  # danger now at high
    assert tools.send_village_alert("h", "h:escalation:0", "ESCALATION", danger)["status"] == "simulated"


def test_cli_runs_the_graph_and_resumes_with_the_given_answer(monkeypatch, capsys):
    from gajamitra import cli
    monkeypatch.setattr(cli, "build_graph", lambda tools, chat, cp: build_graph(
        tools, FakeChat([responder_reply("danger")], [verdict()]), cp, perceive=lambda p, b: (summary("high"), [])))
    assert cli.main(["clip.mp4", "--approval", "reject"]) == 0
    out = json.loads(capsys.readouterr().out)
    assert out["risk"] == "high" and out["approval"] == "reject"
    assert [d["stage"] for d in out["dispatched"]] == ["now", "now"]


def test_resume_does_not_call_dispatch_again():
    graph, tools, _ = run("high", FakeChat([responder_reply("danger")], [verdict()]))
    resume(graph, "a1", "approve")
    sends = [c for c in tools.calls if c[0] in ("send_village_alert", "notify_wildlife_officer")]
    assert [c[1] for c in sends] == ["a1:now:0", "a1:now:report", "a1:escalation:0"]


class RejectingTools(StubToolClient):
    def send_village_alert(self, analysis_id, idempotency_key, stage, alert):
        return {"status": "rejected", "reason": "no stored risk for this analysis"}


def test_rejected_warning_fails_the_run_loudly():
    # Invariants 3 and 10: a warning Spring refused must never look like a completed analysis.
    from gajamitra.graph import DispatchFailed
    tools = RejectingTools({"s1": {"name": "X", "requires_approval": False}})
    graph = build_graph(tools, FakeChat([responder_reply("warning")], [verdict()]), InMemorySaver(),
                        perceive=lambda p, b: (summary("medium"), []))
    with pytest.raises(DispatchFailed, match="no stored risk"):
        start(graph, "a1", "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])


def test_unrecorded_risk_fails_before_any_model_runs():
    from gajamitra.graph import DispatchFailed
    chat = FakeChat()
    tools = StubToolClient()
    tools.record_assessment = lambda *a: {"status": "rejected", "reason": "database unavailable"}
    graph = build_graph(tools, chat, InMemorySaver(), perceive=lambda p, b: (summary("high"), []))
    with pytest.raises(DispatchFailed):
        start(graph, "a1", "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])
    assert chat.asked == []


def test_redelivered_job_continues_instead_of_starting_again():
    # Invariant 6: a second start for the same analysis doesn't re-run perception, the models or the sends.
    perceived = []
    chat = FakeChat([responder_reply("danger")], [verdict()])
    tools = StubToolClient({"s1": {"name": "X", "requires_approval": True}})
    graph = build_graph(tools, chat, InMemorySaver(),
                        perceive=lambda p, b: (perceived.append(1), (summary("high"), []))[1])
    first = start(graph, "a1", "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])
    again = start(graph, "a1", "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])
    assert "__interrupt__" in first and "__interrupt__" in again
    assert perceived == [1] and chat.asked == ["responder", "verifier"]
    resume(graph, "a1", "approve")
    done = start(graph, "a1", "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])
    assert done["approval"] == "approve" and perceived == [1]
    assert len([c for c in tools.calls if c[0] == "send_village_alert"]) == 2


def test_verifier_timeout_does_not_block_the_warning():
    from gajamitra.ports import ModelUnavailable

    class TimeoutVerifier(FakeChat):
        def __call__(self, messages):
            if messages[0]["content"] is VERIFIER:
                raise ModelUnavailable("APITimeoutError")
            return super().__call__(messages)

    _, tools, result = run("medium", TimeoutVerifier([responder_reply("warning")]), requires_approval=False)
    assert sent(tools) == [("a1:now:0", "warning")]
    assert any("verifier unavailable" in n for n in result["notes"])


def test_over_severe_proposal_is_lowered_through_the_graph():
    _, tools, _ = run("medium", FakeChat([responder_reply("danger")], [verdict()]), requires_approval=False)
    assert sent(tools) == [("a1:now:0", "warning")]


def test_rejected_responder_cannot_trigger_an_officer_report():
    _, tools, _ = run("medium", FakeChat([responder_reply("warning", behaviour="aggressive")],
                                         [verdict("reject", reasons=["not elephants"])]), requires_approval=False)
    assert ("a1:now:report", "officer") not in sent(tools)


def test_slow_models_hit_the_deadline_and_the_guard_decides():
    from gajamitra.graph import MODEL_DEADLINE_S
    times = iter([0.0] + [MODEL_DEADLINE_S + 1] * 10)
    chat = FakeChat()  # would fail if asked: the deadline is already over
    tools = StubToolClient({"s1": {"name": "X", "requires_approval": False}})
    graph = build_graph(tools, chat, InMemorySaver(), perceive=lambda p, b: (summary("critical"), []),
                        clock=lambda: next(times))
    result = start(graph, "a1", "s1", "clip.mp4", [0, 0.5, 1, 0.5, 0.5, 0.9])
    assert chat.asked == [] and ("a1:now:0", "danger") in tools.deliveries
    assert any("longer than" in n for n in result["notes"])


def test_unverified_responder_text_is_never_sent():
    # Decided in GM-8: when the verifier is unavailable, the template goes out instead of unchecked model text.
    _, tools, result = run("medium", FakeChat([responder_reply("warning")], ["nonsense"]), requires_approval=False)
    assert sent(tools) == [("a1:now:0", "warning")]
    sent_text = GuardDecision.model_validate(result["decision"]).send_now[0].en
    assert "east fence" not in sent_text and "Elephant warning" in sent_text
