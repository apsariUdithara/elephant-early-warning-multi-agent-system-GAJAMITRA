"""Phase 0 check: confirm the Meta Model API behaves the way GajaMitra's code assumes (plan Phase 0, steps 2-4).

Run by hand only. It calls the real API and spends a small amount of credit (the SAM step more):
    cd ai-service
    $env:MODEL_API_KEY = "LLM|..."
    ..\\.venv\\Scripts\\python scripts\\phase0_check.py                    # model, text, image, JSON, tools
    ..\\.venv\\Scripts\\python scripts\\phase0_check.py --clip path\\to.mp4   # also run SAM 3.1 on one clip

Results are printed and written to data/phase0/ (git-ignored), including SAM's raw output, so the parser can be
checked against the real format. Never run from tests or CI.
"""
import argparse
import base64
import json
import os
import sys
import time
from pathlib import Path

import cv2
import numpy as np
from openai import OpenAI, OpenAIError

from gajamitra.track import SamOutputError, parse_sam

BASE_URL = "https://api.meta.ai/v1"
EXPECTED_MODELS = ("muse-spark-1.3", "sam-3.1", "muse-wonder-1.0")
SPARK = "muse-spark-1.3"
OUT = Path(__file__).resolve().parents[2] / "data" / "phase0"


class Report:
    def __init__(self):
        self.rows = []

    def add(self, check, ok, detail):
        mark = {True: "PASS", False: "FAIL", None: "INFO"}[ok]
        self.rows.append((check, mark, detail))
        print(f"[{mark}] {check}: {detail}", flush=True)

    def write(self):
        OUT.mkdir(parents=True, exist_ok=True)
        lines = ["| Check | Result | Detail |", "|---|---|---|"]
        lines += [f"| {c} | {m} | {safe(d)} |" for c, m, d in self.rows]
        (OUT / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"\nReport written to {OUT / 'report.md'}")


def safe(detail):
    """Model replies are untrusted: keep them as plain text in the markdown table."""
    return detail.replace("|", "/").replace("\n", " ").replace("<", "&lt;").replace("`", "'")


def text_of(response):
    return (response.choices[0].message.content or "").strip()


def check_models(client, report):
    try:
        ids = sorted(m.id for m in client.models.list())
    except OpenAIError as e:
        report.add("models.list", None, f"not available ({type(e).__name__}); checking models by calling them")
        return
    missing = [m for m in EXPECTED_MODELS if m not in ids]
    report.add("models.list", not missing, f"missing {missing}" if missing else f"all expected models present ({len(ids)} total)")


def check_text(client, report):
    reply = text_of(client.chat.completions.create(model=SPARK, messages=[{"role": "user", "content": "Reply with OK."}]))
    report.add("Muse Spark text", "OK" in reply.upper(), repr(reply[:80]))


def check_image(client, report):
    img = np.zeros((64, 64, 3), np.uint8)
    img[:] = (0, 0, 255)  # BGR red
    url = "data:image/jpeg;base64," + base64.b64encode(cv2.imencode(".jpg", img)[1]).decode()
    reply = text_of(client.chat.completions.create(model=SPARK, messages=[{"role": "user", "content": [
        {"type": "text", "text": "What colour fills this image? One word."},
        {"type": "image_url", "image_url": {"url": url}}]}]))
    report.add("Muse Spark image input", "red" in reply.lower(), repr(reply[:80]))


def check_json(client, report):
    reply = text_of(client.chat.completions.create(model=SPARK, messages=[
        {"role": "system", "content": "Reply with only JSON, no other text."},
        {"role": "user", "content": 'Return {"herd_size": 3, "risk": "medium"} exactly.'}]))
    body = reply.removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    try:
        ok = json.loads(body) == {"herd_size": 3, "risk": "medium"}
        report.add("Muse Spark JSON-only reply", ok, repr(reply[:120]))
    except json.JSONDecodeError:
        report.add("Muse Spark JSON-only reply", False, f"not JSON: {reply[:120]!r}")


def check_tools(client, report):
    tool = {"type": "function", "function": {"name": "record_risk", "description": "Record a risk level.",
                                             "parameters": {"type": "object", "properties": {
                                                 "risk": {"type": "string", "enum": ["low", "medium", "high"]}},
                                                 "required": ["risk"]}}}
    msg = client.chat.completions.create(model=SPARK, tools=[tool], messages=[
        {"role": "user", "content": "Record the risk level high using the tool."}]).choices[0].message
    calls = msg.tool_calls or []
    ok = bool(calls) and calls[0].function.name == "record_risk" and json.loads(calls[0].function.arguments) == {"risk": "high"}
    report.add("Muse Spark tool calling", ok, f"{len(calls)} call(s): {calls[0].function.arguments if calls else msg.content!r}"[:120])


def check_sam(client, report, clip):
    started, chunks, events = time.time(), [], []
    with open(clip, "rb") as f:
        file = client.files.create(file=f, purpose="user_data")
    try:
        with client.responses.stream(model="sam-3.1", input=[{"type": "message", "role": "user", "content": [
                {"type": "input_text", "text": "elephant"}, {"type": "input_file", "file_id": file.id}]}]) as stream:
            for event in stream:
                events.append(event.type)
                if event.type == "response.output_text.delta":
                    chunks.append(event.delta)
    finally:
        # Keep whatever arrived (even from a broken stream) and don't leave footage in Meta's file store.
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / "sam_raw.txt").write_text("".join(chunks), encoding="utf-8")
        try:
            client.files.delete(file.id)
        except OpenAIError as e:
            report.add("SAM clip deleted from Meta", False, f"{type(e).__name__}: delete file {file.id} by hand")
    text = "".join(chunks)
    kinds = sorted(set(events))
    report.add("SAM 3.1 stream events", "response.completed" in kinds,
               f"{len(events)} events, types {kinds} in {time.time() - started:.0f} s")
    try:
        frames, size = parse_sam(text)
        with_boxes = sum(1 for b in frames.values() if b)
        report.add("SAM 3.1 output parses", True, f"frame size {size}, {len(frames)} frames, {with_boxes} with boxes")
        cap = cv2.VideoCapture(str(clip))
        fps, count = cap.get(cv2.CAP_PROP_FPS) or 0, cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0
        cap.release()
        report.add("SAM frame coverage", None, f"clip has {int(count)} frames at {fps:.1f} fps; SAM returned "
                                               f"{len(frames)} (every frame? {len(frames) == int(count)})")
    except SamOutputError as e:
        report.add("SAM 3.1 output parses", False, f"{e}; raw text saved to data/phase0/sam_raw.txt")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--clip", type=Path, help="MP4 to run through SAM 3.1 (costs about $0.20 per 1,000 frames)")
    args = parser.parse_args()
    if not os.environ.get("MODEL_API_KEY"):
        sys.exit("Set MODEL_API_KEY first (Meta Model API dashboard).")
    client = OpenAI(base_url=BASE_URL, api_key=os.environ["MODEL_API_KEY"], timeout=120)
    report = Report()
    checks = [check_models, check_text, check_image, check_json, check_tools]
    if args.clip:
        checks.append(lambda c, r: check_sam(c, r, args.clip))
    for check in checks:
        try:
            check(client, report)
        # A malformed reply (no choices, bad tool JSON) or a bad --clip path fails that check, not the whole run.
        except (OpenAIError, ValueError, IndexError, AttributeError, OSError) as e:
            report.add(getattr(check, "__name__", "SAM 3.1"), False, f"{type(e).__name__}: {e}"[:200])
    report.write()
    return 0 if all(m != "FAIL" for _, m, _ in report.rows) else 1


if __name__ == "__main__":
    sys.exit(main())
