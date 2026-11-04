"""Prototype responder + guard: model output is only a proposal (invariants 1, 7, 8). No API calls."""
import json
from types import SimpleNamespace

import pytest
from openai import APIConnectionError

from gajamitra import agent


def summary(risk, herd=2):
    return {"risk": risk, "herd_size": herd,
            "elephants": [{"id": i, "dist_px": 30, "eta_s": 20.0, "approaching": True} for i in range(herd)]}


def reply(severity="danger", behaviour="warning", report="Two elephants at the east fence", **extra):
    out = {"assessment": {"behaviour": behaviour, "calves": False, "reasoning": "Ears spread. Moving to the fence."},
           "proposal": {"alerts": [{"severity": severity, "en": "Elephants at the east fence. Stay indoors.",
                                    "si": "නැගෙනහිර වැට අසල අලි.", "ta": "கிழக்கு வேலிக்கு அருகே யானைகள்."}],
                        "officer_report": {"report": report} if report else None}}
    out.update(extra)
    return json.dumps(out)


def fake_model(monkeypatch, *replies):
    """Each item is the model's text, or an exception to raise. Records the messages sent."""
    calls = []

    def create(**kw):
        calls.append(kw["messages"])
        item = replies[len(calls) - 1]
        if isinstance(item, Exception):
            raise item
        return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=item))])

    client = SimpleNamespace(chat=SimpleNamespace(completions=SimpleNamespace(create=create)))
    monkeypatch.setattr(agent, "meta", lambda: client)
    return calls


def test_valid_proposal_is_set_to_the_tracker_level(monkeypatch):
    # The model asks for danger at medium risk; the guard sends a warning (invariant 8).
    fake_model(monkeypatch, reply("danger"))
    assessment, decision, notes = agent.respond([], summary("medium"))
    assert assessment["behaviour"] == "warning"
    assert [a.severity for a in decision.send_now] == ["warning"] and decision.escalation == []
    assert any("lowered the alert from danger to warning" in n for n in notes)


def test_high_risk_escalation_is_sent_after_the_warning_in_the_prototype(monkeypatch):
    fake_model(monkeypatch, reply("danger"))
    _, decision, _ = agent.respond([], summary("high"))
    actions = agent.dispatch(decision)
    assert [(a["stage"], a["args"]["severity"]) for a in actions if a["tool"] == "send_village_alert"] == [
        ("now", "warning"), ("escalation", "danger")]
    assert all(a["result"]["status"] == "simulated" for a in actions)  # prototype holds nothing


def test_code_fenced_json_is_accepted(monkeypatch):
    fake_model(monkeypatch, "```json\n" + reply("warning") + "\n```")
    output, notes = agent.propose([], summary("medium"))
    assert output is not None and notes == []


def test_invalid_output_is_retried_with_the_error(monkeypatch):
    calls = fake_model(monkeypatch, "I think there are elephants.", reply("warning"))
    output, notes = agent.propose([], summary("medium"))
    assert output is not None and len(calls) == 2
    assert "rejected" in calls[1][-1]["content"]
    assert any("attempt 1" in n for n in notes)


@pytest.mark.parametrize("bad", [
    reply(send_sms_to="+94770000000"),                    # unexpected field: the model tries to add an action
    reply(severity="emergency"),                          # invented severity
    reply(behaviour="hungry"),                            # behaviour outside the five states
    json.dumps({"assessment": {"behaviour": "calm", "calves": False, "reasoning": "x"},
                "proposal": {"alerts": [{"severity": "warning", "en": "x", "si": "", "ta": "x"}]}}),  # missing Sinhala
])
@pytest.mark.parametrize("risk", ["medium", "critical"])
def test_model_that_never_gives_valid_output_still_warns_villagers(monkeypatch, bad, risk):
    # Invariants 1 and 7: rejected output is never used, and the guard's template alert still goes out.
    fake_model(monkeypatch, bad, bad)
    assessment, decision, notes = agent.respond([], summary(risk))
    assert assessment["behaviour"] == "unknown"
    assert [a.severity for a in decision.send_now] == [{"medium": "warning", "critical": "danger"}[risk]]
    assert any("template" in n for n in notes) and any("no valid proposal" in n for n in notes)


def test_model_call_failure_still_warns_villagers(monkeypatch):
    fake_model(monkeypatch, APIConnectionError(request=None))
    _, decision, notes = agent.respond([], summary("high"))
    assert [a.severity for a in decision.send_now] == ["warning"] and decision.officer_report is not None
    assert any("responder call failed" in n for n in notes)


def test_aggressive_behaviour_notifies_the_officer_even_at_medium(monkeypatch):
    fake_model(monkeypatch, reply("warning", behaviour="aggressive", report=None))
    _, decision, _ = agent.respond([], summary("medium"))
    assert decision.officer_report is not None


def test_low_risk_sends_nothing_whatever_the_model_proposes(monkeypatch):
    fake_model(monkeypatch, reply("danger"))
    _, decision, _ = agent.respond([], summary("low"))
    assert agent.dispatch(decision) == []
