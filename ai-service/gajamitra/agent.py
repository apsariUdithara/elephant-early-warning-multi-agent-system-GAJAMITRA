"""Prototype responder: Muse Spark reads behaviour and *proposes* alerts; the policy guard decides what is sent.

Replaced by the LangGraph responder/verifier graph in plan Phase 2. The model never sends anything itself:
its JSON is validated against `schemas.ResponderOutput`, and `policy.guard` sets the levels (invariants 7, 8).
"""
import json
import logging

import cv2
from openai import OpenAIError
from pydantic import ValidationError

from gajamitra.perception import keyframes, meta, segment_video
from gajamitra.policy import guard
from gajamitra.prompts import RESPONDER
from gajamitra.schemas import Proposal, ResponderOutput
from gajamitra.track import assess

SPARK = "muse-spark-1.3"
# The prototype has no officer approval screen yet (Spring Boot, plan Phase 3), so nothing is held: a high-risk
# escalation is shown as sent right after the warning.
PROTOTYPE_REQUIRES_APPROVAL = False
# One retry with the validation error fed back; after that the guard's template alert is used.
MAX_ATTEMPTS = 2

log = logging.getLogger("gajamitra.agent")


def meta_chat(messages):
    """ChatModel port backed by Muse Spark on the Meta Model API: messages -> reply text."""
    return meta().chat.completions.create(model=SPARK, messages=messages).choices[0].message.content


def parse_responder(text):
    """Model text -> ResponderOutput. Accepts a ```json fence; anything else that doesn't fit is rejected."""
    body = (text or "").strip()
    if body.startswith("```"):
        body = body.removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    return ResponderOutput.model_validate_json(body)


def propose(images, summary, chat=None, context=""):
    """Ask the responder for an assessment and a proposal. Returns (ResponderOutput or None, notes).

    `chat` is the ChatModel port (default: Muse Spark). `context` adds site details, recent history or the
    verifier's critique to the request.
    """
    chat = chat or meta_chat
    request = "Tracker summary:\n" + json.dumps(summary) + (f"\n\n{context}" if context else "")
    messages = [{"role": "system", "content": RESPONDER},
                {"role": "user", "content": [{"type": "text", "text": request}]
                 + [{"type": "image_url", "image_url": {"url": u}} for u in images]}]
    notes = []
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            text = chat(messages)
        except OpenAIError as e:
            log.error("responder call failed: %s", e)
            notes.append(f"responder call failed ({type(e).__name__}); the guard decides without a proposal")
            return None, notes
        try:
            return parse_responder(text), notes
        except ValidationError as e:
            problem = e.errors()[0].get("msg", "invalid JSON")
            log.warning("responder output rejected (attempt %d): %s", attempt, problem)
            notes.append(f"responder output rejected (attempt {attempt}): {problem}")
            messages += [{"role": "assistant", "content": text or ""},
                         {"role": "user", "content": f"That reply was rejected: {problem}. Reply with only the JSON "
                                                     "object described in the instructions."}]
    notes.append("no valid proposal; the guard decides without one")
    return None, notes


def dispatch(decision):
    """Prototype delivery: alerts and reports go to the dashboard only (SMS arrives with Spring Boot)."""
    status = "simulated"
    actions = [{"tool": "send_village_alert", "stage": "now", "args": a.model_dump(), "result": {"status": status}}
               for a in decision.send_now]
    if decision.officer_report:
        actions.append({"tool": "notify_wildlife_officer", "stage": "now", "args": decision.officer_report.model_dump(),
                        "result": {"status": status}})
    held = "held_for_approval" if decision.hold_for_approval else status
    actions += [{"tool": "send_village_alert", "stage": "escalation", "args": a.model_dump(), "result": {"status": held}}
                for a in decision.escalation]
    return actions


def respond(images, summary):
    """Responder proposal -> guard decision -> simulated dispatch."""
    output, notes = propose(images, summary)
    proposal = output.proposal if output else Proposal()
    aggression = bool(output and output.assessment.behaviour == "aggressive")
    decision = guard(summary, proposal, requires_approval=PROTOTYPE_REQUIRES_APPROVAL, aggression=aggression)
    assessment = output.assessment.model_dump() if output else {"behaviour": "unknown", "calves": False,
                                                                  "reasoning": "No valid responder assessment."}
    return assessment, decision, notes + decision.notes


def analyze(path, boundary_norm):
    """boundary_norm = (ax, ay, bx, by, px, py) as 0-1 fractions of the frame."""
    cap = cv2.VideoCapture(path)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    cap.release()
    frames, size = segment_video(path)
    if not size:
        return {"fps": fps, "frames": {}, "summary": {"herd_size": 0, "risk": "low", "elephants": []},
                "actions": [], "notes": [], "assessment": {"reasoning": "No elephants detected."}}
    w, h = size
    boundary = [v * (w if i % 2 == 0 else h) for i, v in enumerate(boundary_norm)]
    summary = assess(frames, fps, boundary)
    assessment, decision, notes = respond(keyframes(path, frames), summary)
    return {"fps": fps, "width": w, "height": h, "frames": frames, "summary": summary,
            "actions": dispatch(decision), "notes": notes, "assessment": assessment}
