"""Eval runner logic: labelled clips in, counts like "13/15" out. Pure and testable; only evals/run.py spends credits."""
import json
import uuid
from pathlib import Path

from langgraph.checkpoint.memory import InMemorySaver

from gajamitra.graph import build_graph, resume, start
from gajamitra.policy import load_table
from gajamitra.ports import StubToolClient


def load_cases(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def expected_severities(risk):
    """The severities that must go out for a risk level, from the shared policy table (escalation is auto-approved)."""
    rule = load_table().risk[risk]
    return sorted(s for s in (rule.send_now_severity, rule.escalation_severity) if s)


def run_case(case, chat, perceive_graph=None):
    """Run one case with stub tools (nothing is really sent). Returns a result dict; never raises on a failed run."""
    tools = StubToolClient({"site": {"name": case.get("site"), "requires_approval": True}})
    graph = perceive_graph or build_graph(tools, chat, InMemorySaver())
    aid = uuid.uuid4().hex
    try:
        state = start(graph, aid, "site", case["clip"], case["boundary"])
        if "__interrupt__" in state:
            state = resume(graph, aid, "approve")
    except Exception as e:  # noqa: BLE001  a failed run is a failed case, reported, never a pass
        return {"id": case["id"], "ok": False, "error": str(e), "risk": None, "sent": []}
    risk = state["summary"]["risk"]
    sent = sorted({sev for _, sev in tools.deliveries if sev != "officer"})  # officer reports are not village alerts
    want = case["expected_risk"]
    return {"id": case["id"], "ok": risk == want and sent == expected_severities(want),
            "risk": risk, "expected_risk": want, "sent": sent}


def run_all(cases, chat):
    """Cases without a clip are skipped and counted separately, never counted as passes."""
    ready = [c for c in cases if c.get("clip")]
    results = [run_case(c, chat) for c in ready]
    return {"results": results, "passed": sum(r["ok"] for r in results), "ran": len(ready), "total": len(cases),
            "skipped": [c["id"] for c in cases if not c.get("clip")]}


def report(summary):
    lines = [f"Passed {summary['passed']}/{summary['ran']} ({summary['total']} cases, {len(summary['skipped'])} without footage)"]
    lines += [f"  {'ok  ' if r['ok'] else 'FAIL'} {r['id']}: risk {r['risk']}, sent {r['sent']}{' ' + r['error'][:150] if 'error' in r else ''}" for r in summary["results"]]
    lines += [f"  skip {i}: no clip yet" for i in summary["skipped"]]
    return "\n".join(lines)


JUDGE_PROMPT = (
    "You grade a village elephant warning. Reply with JSON only: "
    '{"location": 1-5, "count": 1-5, "instruction": 1-5, "brevity": 1-5}. '
    "location: names where the elephants are; count: gives the herd size; "
    "instruction: says clearly what to do; brevity: short enough for one SMS."
)


def judge_alert(text, chat):
    """AI-judge for alert quality. The judge is untrusted too: anything that does not parse as four 1-5 scores is rejected."""
    raw = chat([{"role": "system", "content": JUDGE_PROMPT}, {"role": "user", "content": text}])
    scores = json.loads(raw)
    keys = ("location", "count", "instruction", "brevity")
    if not all(isinstance(scores.get(k), int) and 1 <= scores[k] <= 5 for k in keys):
        raise ValueError("judge returned invalid scores")
    return {k: scores[k] for k in keys}
