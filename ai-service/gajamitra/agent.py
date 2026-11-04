"""Prototype single agent: Muse Spark reads behaviour and calls tools. Replaced by the LangGraph graph in Phase 2."""
import json

import cv2

from gajamitra.perception import keyframes, meta, segment_video
from gajamitra.track import assess

SPARK = "muse-spark-1.3"

SYSTEM = """You are GajaMitra, an early-warning agent protecting a Sri Lankan village and its elephants.
You get keyframes from a boundary camera (SAM 3.1 boxes and IDs drawn on) and a tracker summary
(herd size, each elephant's distance to the village boundary in pixels, whether it is near the boundary,
approach speed, ETA in seconds).

1. Judge herd behaviour from the frames: calm (feeding, slow wandering), alert (head up, ears forward,
   standing still), warning (ears spread, trunk raised, head shaking), aggressive (mock charge, rapid advance),
   or conflict-risk (moving purposefully toward crops or houses). Note calves; herds with calves are protective.
2. Act in proportion, using the tools. Never escalate beyond what the evidence supports:
   - low risk and calm: no action.
   - medium risk (approaching, or close to the boundary): send_village_alert (advisory or warning).
   - high or critical risk: send_village_alert (danger) and notify_wildlife_officer.
   Alerts must be short and practical (where, how many, what to do) in English, Sinhala and Tamil.
3. When finished, reply with only JSON:
   {"behaviour": "<state>", "calves": true|false, "reasoning": "<two sentences citing what you saw>"}"""

TOOLS = [
    {"type": "function", "function": {
        "name": "send_village_alert",
        "description": "Broadcast an alert to registered villagers' phones.",
        "parameters": {"type": "object", "properties": {
            "severity": {"type": "string", "enum": ["advisory", "warning", "danger"]},
            "english": {"type": "string"}, "sinhala": {"type": "string"}, "tamil": {"type": "string"}},
            "required": ["severity", "english", "sinhala", "tamil"]}}},
    {"type": "function", "function": {
        "name": "notify_wildlife_officer",
        "description": "Send an incident report to the Department of Wildlife Conservation duty officer.",
        "parameters": {"type": "object", "properties": {"report": {"type": "string"}}, "required": ["report"]}}},
]


def send_village_alert(**alert):
    # ponytail: alerts only reach the dashboard; plug in an SMS/voice gateway for real villages.
    return {"status": "delivered_to_dashboard"}


def notify_wildlife_officer(report):
    return {"status": "delivered_to_dashboard"}


ACTIONS = {f.__name__: f for f in (send_village_alert, notify_wildlife_officer)}


def run_agent(images, summary, max_steps=5):
    events = []
    messages = [{"role": "system", "content": SYSTEM},
                {"role": "user", "content": [{"type": "text", "text": "Tracker summary:\n" + json.dumps(summary)}]
                 + [{"type": "image_url", "image_url": {"url": u}} for u in images]}]
    for _ in range(max_steps):
        msg = meta().chat.completions.create(model=SPARK, messages=messages, tools=TOOLS).choices[0].message
        if not msg.tool_calls:
            try:
                return events, json.loads(msg.content.strip().removeprefix("```json").strip("`"))
            except (json.JSONDecodeError, AttributeError):
                return events, {"reasoning": msg.content}
        messages.append(msg.model_dump(exclude_none=True))
        for call in msg.tool_calls:
            args = json.loads(call.function.arguments or "{}")
            result = ACTIONS[call.function.name](**args) if call.function.name in ACTIONS else {"error": "unknown tool"}
            events.append({"tool": call.function.name, "args": args, "result": result})
            messages.append({"role": "tool", "tool_call_id": call.id, "content": json.dumps(result)})
    return events, {"reasoning": f"stopped after {max_steps} steps"}


def analyze(path, boundary_norm):
    """boundary_norm = (ax, ay, bx, by, px, py) as 0-1 fractions of the frame."""
    cap = cv2.VideoCapture(path)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    cap.release()
    frames, size = segment_video(path)
    if not size:
        return {"fps": fps, "frames": {}, "summary": {"herd_size": 0, "risk": "low", "elephants": []},
                "actions": [], "assessment": {"reasoning": "No elephants detected."}}
    w, h = size
    boundary = [v * (w if i % 2 == 0 else h) for i, v in enumerate(boundary_norm)]
    summary = assess(frames, fps, boundary)
    actions, assessment = run_agent(keyframes(path, frames), summary)
    return {"fps": fps, "width": w, "height": h, "frames": frames, "summary": summary,
            "actions": actions, "assessment": assessment}
