"""The GajaMitra agent pipeline as a LangGraph graph (plan Phase 2).

perceive -> load_context -> responder <-> verifier -> policy_guard -> dispatch_now -> approval_gate
         -> dispatch_escalation -> finish

Agents propose; the policy guard decides; every side effect goes through the ToolClient port with an
idempotency key, and a side effect that isn't accepted fails the run loudly (invariants 3, 10). One graph run per
analysis; the thread id is the analysis id, so a redelivered or interrupted run continues from its checkpoint
instead of starting again (invariant 6).
"""
import json
import logging
import operator
import time
from typing import Annotated, Any, TypedDict

import cv2
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt
from pydantic import ValidationError

from gajamitra.agent import propose
from gajamitra.perception import keyframes, segment_video
from gajamitra.policy import guard
from gajamitra.ports import ACCEPTED, ModelUnavailable, alert_key
from gajamitra.prompts import VERIFIER
from gajamitra.schemas import GuardDecision, Proposal, ResponderOutput, Verdict
from gajamitra.track import assess

# How many times the verifier may send the proposal back to the responder before the guard decides.
MAX_REVISIONS = 2
# Longest the models may take, from the first responder call. After it, the guard decides with what it has
# (a template if nothing valid arrived), so a slow model can't hold back the warning (invariant 2).
MODEL_DEADLINE_S = 180.0
APPROVAL_ANSWERS = ("approve", "reject", "timeout")

log = logging.getLogger("gajamitra.graph")


class DispatchFailed(RuntimeError):
    """Spring didn't accept a side effect (for example no stored risk). The run fails visibly; it never ends as if
    the warning went out."""


class State(TypedDict, total=False):
    # Plain dicts so the checkpointer can store them; every node re-validates what it reads with the
    # Pydantic models (ResponderOutput, Verdict, GuardDecision).
    analysis_id: str
    site_id: str
    video_path: str
    boundary: list[float]
    summary: dict
    keyframes: list[str]
    site: dict
    history: list[dict]
    models_started: float
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


def data_block(name, value):
    """Model-written or stored text goes to the other model as clearly marked data, not as instructions."""
    return f"<{name}>\n{json.dumps(value, ensure_ascii=False)}\n</{name}>"


def require(result, what):
    if result.get("status") not in ACCEPTED:
        raise DispatchFailed(f"{what} was not accepted: {result.get('reason', result)}")
    return result


def build_graph(tools, chat, checkpointer, perceive=perceive_clip, clock=time.time):
    """tools: ToolClient; chat: ChatModel (messages -> text, raises ModelUnavailable); checkpointer: LangGraph's."""

    def out_of_time(state):
        return clock() - state["models_started"] > MODEL_DEADLINE_S

    def perceive_node(state):
        summary, frames = perceive(state["video_path"], state["boundary"])
        # Spring stores the risk before any model runs, so its re-check can fail closed (invariant 3).
        result = tools.record_assessment(state["analysis_id"], summary["risk"], summary)
        if result.get("status") != "recorded":
            raise DispatchFailed(f"record_assessment was not accepted: {result.get('reason', result)}")
        return {"summary": summary, "keyframes": frames, "revisions": 0}

    def load_context(state):
        site = tools.get_site_procedures(state["site_id"])
        return {"site": site, "history": tools.get_site_history(state["site_id"], 14), "models_started": clock()}

    def responder(state):
        if out_of_time(state):
            return {"notes": [f"models took longer than {MODEL_DEADLINE_S:.0f} s; the guard decides now"]}
        context = [data_block("site", state["site"]), data_block("recent_incidents_14_days", state["history"])]
        if state.get("verdict") and state["verdict"]["decision"] == "revise":
            context.append("A verifier sent your previous proposal back. Its reasons:\n"
                           + data_block("verifier_reasons", state["verdict"]["reasons"]))
        output, notes = propose(state["keyframes"], state["summary"], chat=chat, context="\n".join(context))
        return {"responder": output.model_dump() if output else None, "notes": notes}

    def verifier(state):
        if state.get("responder") is None:
            return {"verdict": Verdict(decision="reject", reasons=["no valid proposal"]).model_dump()}
        if out_of_time(state):
            return {"verdict": Verdict(decision="reject", reasons=["not verified in time"]).model_dump(),
                    "notes": ["no time left to verify: unverified text isn't sent, the template is used"]}
        messages = [{"role": "system", "content": VERIFIER},
                    {"role": "user", "content": [
                        {"type": "text", "text": data_block("tracker_summary", state["summary"]) + "\n"
                                                 + data_block("responder_output", state["responder"])}]
                     + [{"type": "image_url", "image_url": {"url": u}} for u in state["keyframes"]]}]
        try:
            verdict = parse_verdict(chat(messages))
        except (ValidationError, ModelUnavailable) as e:
            # Decided with the user (GM-8): unverified text is never sent. The text checks catch form, not false
            # content, so the guard sends its tracker-based template at the same level and time instead.
            log.warning("verifier unavailable: %s", e)
            return {"verdict": Verdict(decision="reject", reasons=["verifier unavailable"]).model_dump(),
                    "notes": [(f"verifier unavailable ({type(e).__name__}): the responder's unverified text is not "
                               "sent; the template is used")]}
        revisions = state.get("revisions", 0) + (verdict.decision == "revise")
        notes = [f"verifier: {verdict.decision}" + (f" ({'; '.join(verdict.reasons)})" if verdict.reasons else "")]
        return {"verdict": verdict.model_dump(), "revisions": revisions, "notes": notes}

    def after_verifier(state):
        if state["verdict"]["decision"] == "revise" and state["revisions"] <= MAX_REVISIONS and not out_of_time(state):
            return "responder"
        return "policy_guard"

    def policy_guard(state):
        output = ResponderOutput.model_validate(state["responder"]) if state.get("responder") else None
        verdict = Verdict.model_validate(state["verdict"]) if state.get("verdict") else Verdict(decision="approve")
        rejected = verdict.decision == "reject"
        proposal = output.proposal if output and not rejected else Proposal()
        notes = []
        if verdict.decision == "revise":
            notes.append(f"verifier still asked for changes after {MAX_REVISIONS} revisions; the guard decides")
        # A rejected responder's claims (including "aggressive") are not evidence; the verifier's flag still counts.
        aggression = verdict.aggression or bool(output and not rejected and output.assessment.behaviour == "aggressive")
        site = state["site"]
        decision = guard(state["summary"], proposal, requires_approval=bool(site.get("requires_approval", True)),
                         aggression=aggression, place=site.get("name"))
        return {"decision": decision.model_dump(), "notes": notes + decision.notes}

    def dispatch_now(state):
        decision, aid = GuardDecision.model_validate(state["decision"]), state["analysis_id"]
        sent = []
        for i, alert in enumerate(decision.send_now):
            key = alert_key(aid, "NOW", i)
            sent.append({"stage": "now", "key": key, "severity": alert.severity,
                         "result": require(tools.send_village_alert(aid, key, "NOW", alert), f"alert {key}")})
        if decision.officer_report:
            key = alert_key(aid, "NOW", "report")
            sent.append({"stage": "now", "key": key, "severity": "officer",
                         "result": require(tools.notify_wildlife_officer(aid, key, decision.officer_report),
                                           f"officer report {key}")})
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
        sent = []
        for i, alert in enumerate(decision.escalation):
            key = alert_key(aid, "ESCALATION", i)
            sent.append({"stage": "escalation", "key": key, "severity": alert.severity,
                         "result": require(tools.send_village_alert(aid, key, "ESCALATION", alert), f"alert {key}")})
        return {"dispatched": sent}

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
    """Run until finished or until the approval gate waits ('__interrupt__' in the result).

    A redelivered job for an analysis that already has a checkpoint continues from it (or returns the finished
    state) instead of starting again, so SAM, the models and the side effects don't run twice (invariant 6).
    """
    snapshot = graph.get_state(config(analysis_id))
    if snapshot.values:
        return graph.invoke(None, config(analysis_id)) if snapshot.next else snapshot.values
    return graph.invoke({"analysis_id": analysis_id, "site_id": site_id, "video_path": video_path,
                         "boundary": list(boundary)}, config(analysis_id))


def resume(graph, analysis_id, decision) -> dict[str, Any]:
    """Continue a run waiting at the approval gate with 'approve', 'reject' or 'timeout'."""
    return graph.invoke(Command(resume={"decision": decision}), config(analysis_id))
