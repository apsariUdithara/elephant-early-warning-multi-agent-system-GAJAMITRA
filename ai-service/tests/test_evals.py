import json

import pytest

from gajamitra import evals


def test_the_expected_severities_come_from_the_policy_table():
    assert evals.expected_severities("low") == []
    assert evals.expected_severities("medium") == ["warning"]
    assert evals.expected_severities("high") == ["danger", "warning"]
    assert evals.expected_severities("critical") == ["danger"]


def test_cases_without_a_clip_are_skipped_not_passed(monkeypatch):
    monkeypatch.setattr(evals, "run_case", lambda c, chat: {"id": c["id"], "ok": True, "risk": "low", "sent": []})
    s = evals.run_all([{"id": "a", "clip": "x.mp4"}, {"id": "b", "clip": None}], None)
    assert (s["passed"], s["ran"], s["total"], s["skipped"]) == (1, 1, 2, ["b"])
    assert evals.report(s).startswith("Passed 1/1 (2 cases, 1 without footage)")


def test_a_crashed_run_is_a_failed_case():
    class Boom:
        def invoke(self, *a, **k):
            raise RuntimeError("no perception")

    r = evals.run_case({"id": "x", "clip": "missing.mp4", "boundary": [0, 0.5, 1, 0.5, 0.5, 0.9], "expected_risk": "low"},
                       None, perceive_graph=Boom())
    assert r["ok"] is False and "error" in r


def test_the_judge_rejects_scores_it_cannot_trust():
    good = json.dumps({"location": 5, "count": 4, "instruction": 5, "brevity": 3})
    assert evals.judge_alert("DANGER", lambda m: good)["count"] == 4
    for bad in ('{"location": 9, "count": 1, "instruction": 1, "brevity": 1}', "not json", "{}"):
        with pytest.raises(ValueError):
            evals.judge_alert("DANGER", lambda m, b=bad: b)


def test_judge_scores_are_reported_and_a_bad_judge_never_changes_pass_or_fail(monkeypatch):
    monkeypatch.setattr(evals, "run_case", lambda c, chat: {"id": c["id"], "ok": True, "risk": "high", "sent": [], "texts": ["DANGER"]})
    good = json.dumps({"location": 5, "count": 5, "instruction": 5, "brevity": 5})
    s = evals.run_all([{"id": "a", "clip": "x"}], None, judge=lambda m: good)
    assert s["results"][0]["quality"][0]["location"] == 5 and "quality a" in evals.report(s)
    s = evals.run_all([{"id": "a", "clip": "x"}], None, judge=lambda m: "garbage")
    assert s["passed"] == 1 and s["results"][0]["quality"] == "judge output rejected"
