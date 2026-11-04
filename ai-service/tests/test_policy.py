"""Deterministic policy guard (safety invariants 1, 2, 8). Table-driven over every risk x proposal x approval case."""
import json

import pytest
from pydantic import ValidationError

from gajamitra.policy import TABLE_PATH, guard, load_table
from gajamitra.schemas import OfficerReport, Proposal, VillageAlert


def summary(risk, herd=2):
    elephants = [{"id": i, "dist_px": 40 - 10 * i, "eta_s": 30.0 + i, "approaching": True} for i in range(herd)]
    return {"risk": risk, "herd_size": herd, "elephants": elephants}


def alert(severity, text="Elephants near the east field"):
    return VillageAlert(severity=severity, en=text, si="සිංහල " + text, ta="தமிழ் " + text)


# risk -> (severity sent now, severity of the escalation when the responder proposed danger, officer report sent)
EXPECTED = {
    "low": (None, None, False),
    "medium": ("warning", None, False),
    "high": ("warning", "danger", True),
    "critical": ("danger", None, True),
}


@pytest.mark.parametrize("risk", list(EXPECTED))
@pytest.mark.parametrize("proposed", [None, "advisory", "warning", "danger"])
@pytest.mark.parametrize("requires_approval", [True, False])
def test_guard_table(risk, proposed, requires_approval):
    now, escalation, report = EXPECTED[risk]
    proposal = Proposal(alerts=[alert(proposed)] if proposed else [])
    d = guard(summary(risk), proposal, requires_approval=requires_approval)

    assert [a.severity for a in d.send_now] == ([now] if now else [])
    # A held template escalation is offered at high risk even without a danger proposal (decided in GM-8).
    expected_escalation = [escalation] if escalation and (proposed == escalation or requires_approval) else []
    assert [a.severity for a in d.escalation] == expected_escalation
    assert d.hold_for_approval == bool(expected_escalation and requires_approval)
    assert (d.officer_report is not None) == report
    if proposed and now and proposed != now:
        assert any(("raised" in n or "lowered" in n) for n in d.notes)


@pytest.mark.parametrize("requires_approval", [True, False])
def test_critical_is_never_held_for_approval(requires_approval):
    # Invariant 2: an elephant on the village side means danger now, never waiting for an officer.
    d = guard(summary("critical"), Proposal(alerts=[alert("danger")]), requires_approval=requires_approval)
    assert [a.severity for a in d.send_now] == ["danger"] and d.escalation == [] and not d.hold_for_approval


def test_high_risk_warning_goes_out_before_any_approval():
    # Invariant 2: approval may hold only the escalation; the warning is in send_now.
    d = guard(summary("high"), Proposal(alerts=[alert("danger")]), requires_approval=True)
    assert [a.severity for a in d.send_now] == ["warning"] and [a.severity for a in d.escalation] == ["danger"]
    assert d.hold_for_approval
    assert d.send_now[0].en == d.escalation[0].en  # same text, two levels


@pytest.mark.parametrize("risk", ["medium", "high", "critical"])
def test_silent_responder_still_warns_villagers(risk):
    # Invariant 1: the model proposing nothing must not silence the warning.
    d = guard(summary(risk, herd=3), Proposal(), requires_approval=True)
    (a,) = d.send_now
    assert a.severity == EXPECTED[risk][0]
    assert all(t and "3" in t for t in (a.en, a.si, a.ta))
    assert any("template" in n for n in d.notes)


def test_template_alert_uses_the_place_name():
    d = guard(summary("medium"), Proposal(), requires_approval=False, place="Galgamuwa")
    assert all("Galgamuwa" in t for t in (d.send_now[0].en, d.send_now[0].si, d.send_now[0].ta))


def test_low_risk_drops_alerts_and_reports():
    proposal = Proposal(alerts=[alert("danger")], officer_report=OfficerReport(report="herd seen"))
    d = guard(summary("low"), proposal, requires_approval=False)
    assert d.send_now == [] and d.escalation == [] and d.officer_report is None
    assert len(d.notes) == 2


@pytest.mark.parametrize("risk", ["high", "critical"])
def test_missing_officer_report_uses_template_from_tracker(risk):
    d = guard(summary(risk, herd=2), Proposal(alerts=[alert("warning")]), requires_approval=False)
    assert d.officer_report and "2 elephant(s)" in d.officer_report.report and risk in d.officer_report.report


def test_responder_report_is_kept_when_required():
    d = guard(summary("high"), Proposal(officer_report=OfficerReport(report="mock charge seen")), requires_approval=True)
    assert d.officer_report.report == "mock charge seen"


@pytest.mark.parametrize("risk", ["low", "medium"])
def test_verifier_flagged_aggression_notifies_officer(risk):
    d = guard(summary(risk), Proposal(), requires_approval=False, aggression=True)
    assert d.officer_report is not None


def test_most_severe_of_several_proposed_alerts_is_kept():
    proposal = Proposal(alerts=[alert("advisory", "a"), alert("danger", "d"), alert("warning", "w")])
    d = guard(summary("high"), proposal, requires_approval=True)
    assert d.send_now[0].en == "d" and d.escalation[0].en == "d"
    assert any("extra" in n for n in d.notes)


def test_unknown_risk_fails_loudly():
    with pytest.raises(ValueError):
        guard({"risk": "unknown", "herd_size": 1, "elephants": []}, Proposal(), requires_approval=True)


@pytest.mark.parametrize("bad", [
    {"alerts": [{"severity": "emergency", "en": "x", "si": "x", "ta": "x"}]},   # invented severity
    {"alerts": [{"severity": "warning", "en": "x", "si": "", "ta": "x"}]},      # missing language
    {"alerts": [], "send_sms_to": "+94770000000"},                              # unexpected field
])
def test_invalid_model_output_is_rejected(bad):
    # Invariant 7: model output that doesn't fit the schema is rejected, not coerced.
    with pytest.raises(ValidationError):
        Proposal.model_validate(bad)


def test_policy_table_contract():
    # policy_table.json is a contract with the Java re-check: changing it must change this test too.
    raw = json.loads(TABLE_PATH.read_text(encoding="utf-8"))
    assert raw["severity_order"] == ["advisory", "warning", "danger"]
    rows = {r: (v["max_severity"], v["send_now_severity"], v["escalation_severity"], v["officer_report"], v["hold_escalation"])
            for r, v in raw["risk"].items()}
    assert rows == {
        "low": (None, None, None, False, False),
        "medium": ("warning", "warning", None, False, False),
        "high": ("danger", "warning", "danger", True, True),
        "critical": ("danger", "danger", None, True, False),
    }
    load_table.cache_clear()
    assert set(load_table().risk) == set(EXPECTED)


def test_high_risk_offers_a_template_escalation_when_none_was_proposed():
    d = guard(summary("high", herd=2), Proposal(alerts=[alert("warning")]), requires_approval=True)
    (esc,) = d.escalation
    assert esc.severity == "danger" and "2" in esc.en and d.hold_for_approval
    assert any("template danger escalation held" in n for n in d.notes)


def test_no_template_escalation_where_nobody_would_approve_it():
    d = guard(summary("high"), Proposal(alerts=[alert("warning")]), requires_approval=False)
    assert d.escalation == []
