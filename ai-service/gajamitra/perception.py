"""Meta Model API perception: SAM 3.1 tracks the herd; keyframes are drawn for Muse Spark."""
import base64
import functools
import os

import cv2
from openai import OpenAI

from gajamitra.track import parse_sam


@functools.cache
def meta():
    """Created on first use, so importing this module (tests, CI) needs no API key."""
    return OpenAI(base_url="https://api.meta.ai/v1", api_key=os.environ["MODEL_API_KEY"])


def segment_video(path, concept="elephant"):
    """Upload the clip and stream SAM 3.1's per-frame boxes (object IDs persist across frames)."""
    with open(path, "rb") as f:
        file = meta().files.create(file=f, purpose="user_data")
    chunks = []
    # Request shape follows meta-model-cookbook 07_sam/01_api_basics/python/02_upload_and_segment_video.py.
    with meta().responses.stream(model="sam-3.1", input=[{"type": "message", "role": "user", "content": [
            {"type": "input_text", "text": concept},
            {"type": "input_file", "file_id": file.id}]}]) as stream:
        for event in stream:
            if event.type == "response.output_text.delta":
                chunks.append(event.delta)
    return parse_sam("".join(chunks))


def keyframes(path, frames, n=4):
    """n evenly spaced frames that contain elephants, boxes and IDs drawn on, as JPEG data URLs."""
    hits = sorted(f for f, boxes in frames.items() if boxes)
    picks = [hits[round(i * (len(hits) - 1) / max(n - 1, 1))] for i in range(min(n, len(hits)))]
    cap, urls = cv2.VideoCapture(path), []
    for f in dict.fromkeys(picks):
        cap.set(cv2.CAP_PROP_POS_FRAMES, f)
        ok, img = cap.read()
        if not ok:
            continue
        for oid, (x1, y1, x2, y2) in frames[f].items():
            cv2.rectangle(img, (int(x1), int(y1)), (int(x2), int(y2)), (0, 200, 255), 2)
            cv2.putText(img, f"#{oid}", (int(x1), int(y1) - 6), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 200, 255), 2)
        urls.append("data:image/jpeg;base64," + base64.b64encode(cv2.imencode(".jpg", img)[1]).decode())
    cap.release()
    return urls
