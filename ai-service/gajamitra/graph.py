"""The GajaMitra agent pipeline as a LangGraph graph (plan Phase 2).

perceive -> load_context -> responder <-> verifier -> policy_guard -> dispatch_now -> approval_gate
         -> dispatch_escalation -> finish

Agents propose; the policy guard decides; every side effect goes through the ToolClient port with an
idempotency key. One graph run per analysis; the thread id is the analysis id, so an interrupted run
(waiting for officer approval) resumes from its checkpoint instead of starting again (invariant 6).
"""
import json
import logging
import operator
from typing import Annotated, Any, TypedDict

import cv2
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt
from openai import OpenAIError
from pydantic import ValidationError

from gajamitra.agent import propose
from gajamitra.perception import keyframes, segment_video
from gajamitra.policy import guard
from gajamitra.prompts import VERIFIER
from gajamitra.schemas import GuardDecision, Proposal, ResponderOutput, Verdict
from gajamitra.track import assess

# How many times the verifier may send the proposal back to the responder before the guard decides.
MAX_REVISIONS = 2
APPROVAL_ANSWERS = ("approve", "reject", "timeout")

log = logging.getLogger("gajamitra.graph")


class State(TypedDict, total=False):
    analysis_id: str
    site_id: str
    video_path: str
    boundary: list[float]
    summary: dict
    keyframes: list[str]
    site: dict
    history: list[dict]
    responder: dict | None
    verdict: dict | None
    revisions: int
    decision: dict
    approval: str
    dispatched: Annotated[list[dict], operator.add]
    notes: Annotated[list[str], operator.add]


def perceive_clip(video_path, boundary):
    """Default perception: SAM 3.1 + tracker + keyframes. PerceptionError propagates (never 'no elephants')."""
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    cap.release()
    frames, size = segment_video(video_path)
    if not size:
        return {"herd_size": 0, "risk": "low", "elephants": []}, []
    w, h = size
    pixels = [v * (w if i % 2 == 0 else h) for i, v in enumerate(boundary)]
    return assess(frames, fps, pixels), keyframes(video_path, frames)


def parse_verdict(text):
    body = (text or "").strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    return Verdict.model_validate_json(body)


def build_graph(tools, chat, checkpointer, perceive=perceive_clip):
    """tools: ToolClient; chat: ChatModel (messages -> text); checkpointer: a LangGraph checkpointer."""

    def perceive_node(state):
        summary, frames = perceive(state["video_path"], state["boundary"])
        # Spring stores the risk before any model runs, so its re-check can fail closed (invariant 3).
        tools.record_assessment(state["analysis_id"], summary["risk"], summary)
        return {"summary": summary, "keyframes": frames, "revisions": 0}

    def load_context(state):
        site = tools.get_site_procedures(state["site_id"])
        return {"site": site, "history": tools.get_site_history(state["site_id"], 14)}

    def responder(state):
        context = [f"Site: {json.dumps(state['site'])}", f"Recent incidents (14 days): {json.dumps(state['history'])}"]
        if state.get("verdict") and state["verdict"]["decision"] == "revise":
            context.append("A verifier sent your previous proposal back: " + "; ".join(state["verdict"]["reasons"]))
        output, notes = propose(state["keyframes"], state["summary"], chat=chat, context="\n".join(context))
        return {"responder": output.model_dump() if output else None, "notes": notes}

    def verifier(state):
        if state["responder"] is None:
            return {"verdict": Verdict(decision="reject", reasons=["no valid proposal"]).model_dump()}
        messages = [{"role": "system", "content": VERIFIER},
                    {"role": "user", "content": [
                        {"type": "text", "text": f"Tracker summary:\n{json.dumps(state['summary'])}\n\n"
                                                 f"Responder output:\n{json.dumps(state['responder'])}"}]
                     + [{"type": "image_url", "image_url": {"url": u}} for u in state["keyframes"]]}]
        try:
            verdict = parse_verdict(chat(messages))
        except (ValidationError, OpenAIError) as e:
            # The verifier can only lower or hold; without it the guard still sets every level.
            log.warning("verifier unavailable: %s", e)
            return {"verdict": Verdict(decision="approve", reasons=[]).model_dump(),
                    "notes": [f"verifier unavailable ({type(e).__name__}); the guard still sets every level"]}
        revisions = state.get("revisions", 0) + (verdict.decision == "revise")
        notes = [f"verifier: {verdict.decision}" + (f" ({'; '.join(verdict.reasons)})" if verdict.reasons else "")]
        return {"verdict": verdict.model_dump(), "revisions": revisions, "notes": notes}

    def after_verifier(state):
        if state["verdict"]["decision"] == "revise" and state["revisions"] <= MAX_REVISIONS:
            return "responder"
        return "policy_guard"

    def policy_guard(state):
        output = ResponderOutput.model_validate(state["responder"]) if state["responder"] else None
        verdict = Verdict.model_validate(state["verdict"])
        proposal = output.proposal if output and verdict.decision != "reject" else Proposal()
        notes = []
        if verdict.decision == "revise":
            notes.append(f"verifier still asked for changes after {MAX_REVISIONS} revisions; the guard decides")
        aggression = verdict.aggression or bool(output and output.assessment.behaviour == "aggressive")
        site = state["site"]
        decision = guard(state["summary"], proposal, requires_approval=bool(site.get("requires_approval", True)),
                         aggression=aggression, place=site.get("name"))
        return {"decision": decision.model_dump(), "notes": notes + decision.notes}

    def dispatch_now(state):
        decision, aid = GuardDecision.model_validate(state["decision"]), state["analysis_id"]
        sent = [{"stage": "now", "key": f"{aid}:now:{i}", "severity": a.severity,
                 "result": tools.send_village_alert(aid, f"{aid}:now:{i}", a)} for i, a in enumerate(decision.send_now)]
        if decision.officer_report:
            sent.append({"stage": "now", "key": f"{aid}:now:report", "severity": "officer",
                         "result": tools.notify_wildlife_officer(aid, f"{aid}:now:report", decision.officer_report)})
        return {"dispatched": sent}

    def after_dispatch_now(state):
        return "approval_gate" if state["decision"]["escalation"] else "finish"

    def approval_gate(state):
        # LangGraph re-runs this node on resume, so nothing may happen before interrupt() (no side effects).
        if not state["decision"]["hold_for_approval"]:
            return {"approval": "not_required"}
        answer = interrupt({"type": "approval_required", "analysis_id": state["analysis_id"],
                            "escalation": state["decision"]["escalation"]})
        decision = answer.get("decision") if isinstance(answer, dict) else answer
        if decision not in APPROVAL_ANSWERS:
            raise ValueError(f"approval answer must be one of {APPROVAL_ANSWERS}, got {answer!r}")
        return {"approval": decision, "notes": [f"escalation {decision}" + (" (sent anyway: approval timed out)"
                                                                           if decision == "timeout" else "")]}

    def after_approval(state):
        return "finish" if state["approval"] == "reject" else "dispatch_escalation"

    def dispatch_escalation(state):
        decision, aid = GuardDecision.model_validate(state["decision"]), state["analysis_id"]
        return {"dispatched": [{"stage": "escalation", "key": f"{aid}:escalation:{i}", "severity": a.severity,
                                "result": tools.send_village_alert(aid, f"{aid}:escalation:{i}", a)}
                               for i, a in enumerate(decision.escalation)]}

    def finish(state):
        return {}

    g = StateGraph(State)
    for name, fn in [("perceive", perceive_node), ("load_context", load_context), ("responder", responder),
                     ("verifier", verifier), ("policy_guard", policy_guard), ("dispatch_now", dispatch_now),
                     ("approval_gate", approval_gate), ("dispatch_escalation", dispatch_escalation),
                     ("finish", finish)]:
        g.add_node(name, fn)
    g.add_edge(START, "perceive")
    g.add_conditional_edges("perceive", lambda s: "finish" if s["summary"]["herd_size"] == 0 else "load_context",
                            ["finish", "load_context"])
    g.add_edge("load_context", "responder")
    g.add_edge("responder", "verifier")
    g.add_conditional_edges("verifier", after_verifier, ["responder", "policy_guard"])
    g.add_edge("policy_guard", "dispatch_now")
    g.add_conditional_edges("dispatch_now", after_dispatch_now, ["approval_gate", "finish"])
    g.add_conditional_edges("approval_gate", after_approval, ["dispatch_escalation", "finish"])
    g.add_edge("dispatch_escalation", "finish")
    g.add_edge("finish", END)
    return g.compile(checkpointer=checkpointer)


def config(analysis_id):
    return {"configurable": {"thread_id": analysis_id}}


def start(graph, analysis_id, site_id, video_path, boundary) -> dict[str, Any]:
    """Run until finished or until the approval gate waits. An interrupted result has an '__interrupt__' key."""
    return graph.invoke({"analysis_id": analysis_id, "site_id": site_id, "video_path": video_path,
                         "boundary": list(boundary)}, config(analysis_id))


def resume(graph, analysis_id, decision) -> dict[str, Any]:
    """Continue a run waiting at the approval gate with 'approve', 'reject' or 'timeout'."""
    return graph.invoke(Command(resume={"decision": decision}), config(analysis_id))

