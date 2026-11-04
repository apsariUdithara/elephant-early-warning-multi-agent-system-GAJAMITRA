"""Meta Model API perception: SAM 3.1 tracks the herd; keyframes are drawn for Muse Spark."""
import base64
import functools
import hashlib
import json
import logging
import os
import tempfile
import time
from pathlib import Path

import cv2
from openai import OpenAI, OpenAIError

from gajamitra.track import SamOutputError, parse_sam

# Stream events that end a response; only "response.completed" means the boxes are complete.
TERMINAL_EVENTS = ("response.completed", "response.failed", "response.incomplete", "error")
# Longest wait for any single network step of a SAM 3.1 call (connect, upload, or the gap between stream chunks).
SAM_TIMEOUT_S = 300.0
# Longest a whole SAM stream may take. Keep it below RabbitMQ's consumer_timeout once the worker exists (Phase 4).
SAM_DEADLINE_S = 900.0
# No hidden SDK retries: a retry would upload the clip again. A failure is a PerceptionError and the job is retried.
SAM_MAX_RETRIES = 0
SAM_MODEL = "sam-3.1"
# Completed, readable SAM output is cached by clip content + model + prompt, so re-runs and evals don't pay for SAM
# again. Entries are verified on read; anything that doesn't verify is a miss, never "no elephants".
# data/ is git-ignored; GAJAMITRA_SAM_CACHE moves it (use a folder only ai-service writes to).
SAM_CACHE_DIR = Path(os.environ.get("GAJAMITRA_SAM_CACHE") or Path(__file__).resolve().parents[2] / "data" / "sam-cache")
CACHE_VERSION = 1

log = logging.getLogger("gajamitra.perception")


class PerceptionError(RuntimeError):
    """SAM 3.1 didn't deliver a complete, readable result. Elephants may be present: risk is unknown."""


@functools.cache
def meta():
    """Created on first use, so importing this module (tests, CI) needs no API key."""
    return OpenAI(base_url="https://api.meta.ai/v1", api_key=os.environ["MODEL_API_KEY"])


def segment_video(path, concept="elephant", cache_dir=None):
    """SAM 3.1's per-frame boxes for the clip (object IDs persist across frames), cached by clip content.

    Raises PerceptionError when the call fails, the stream doesn't complete, or the output is unreadable,
    so a perception failure can never be reported as "no elephants" (safety invariant 1).
    """
    cached = _cache_path(path, concept, Path(cache_dir or SAM_CACHE_DIR))
    text = _read_cache(cached)
    if text is not None:
        return _parse(text)
    text = _stream_sam(path, concept)
    result = _parse(text)  # parse first: output that can't be read is never cached
    _write_cache(cached, text)
    return result


def _parse(text):
    try:
        return parse_sam(text)
    except SamOutputError as e:
        raise PerceptionError(str(e)) from e


def _cache_path(path, concept, cache_dir):
    clip = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            clip.update(block)
    key = json.dumps([clip.hexdigest(), SAM_MODEL, concept, CACHE_VERSION])  # unambiguous: no separator tricks
    return cache_dir / f"{hashlib.sha256(key.encode()).hexdigest()}.json"


def _read_cache(path):
    """The cached SAM text, or None (a miss) when the entry is missing, unreadable, cut short or altered."""
    try:
        entry = json.loads(path.read_text(encoding="utf-8"))
        text = entry["text"]
        if (entry.get("v") != CACHE_VERSION or entry.get("status") != "completed" or len(text) != entry["len"]
                or hashlib.sha256(text.encode("utf-8")).hexdigest() != entry["sha256"]):
            raise ValueError("entry doesn't verify")
        return text
    except FileNotFoundError:
        return None
    except (OSError, ValueError, KeyError, TypeError) as e:
        log.warning("ignoring SAM cache entry %s: %s", path.name, e)
        return None


def _write_cache(path, text):
    """Best effort: a cache that can't be written never fails an analysis SAM already completed."""
    entry = json.dumps({"v": CACHE_VERSION, "status": "completed", "len": len(text),
                        "sha256": hashlib.sha256(text.encode("utf-8")).hexdigest(), "text": text})
    tmp = None
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent, suffix=".tmp", delete=False) as f:
            tmp = f.name  # one temp file per writer, so concurrent writers never share a half-written file
            f.write(entry)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
    except OSError as e:
        log.warning("could not write SAM cache entry %s: %s", path.name, e)
        if tmp and os.path.exists(tmp):
            os.remove(tmp)


def _stream_sam(path, concept):
    """Upload the clip and return SAM 3.1's complete streamed text, or raise PerceptionError."""
    chunks, final = [], None
    client = meta().with_options(timeout=SAM_TIMEOUT_S, max_retries=SAM_MAX_RETRIES)
    started = time.monotonic()
    try:
        with open(path, "rb") as f:
            file = client.files.create(file=f, purpose="user_data")
        # Request shape follows meta-model-cookbook 07_sam/01_api_basics/python/02_upload_and_segment_video.py.
        with client.responses.stream(model=SAM_MODEL, input=[{"type": "message", "role": "user", "content": [
                {"type": "input_text", "text": concept},
                {"type": "input_file", "file_id": file.id}]}]) as stream:
            for event in stream:
                if time.monotonic() - started > SAM_DEADLINE_S:
                    raise PerceptionError(f"SAM 3.1 stream took longer than {SAM_DEADLINE_S:.0f} s")
                if event.type == "response.output_text.delta":
                    chunks.append(event.delta)
                elif event.type in TERMINAL_EVENTS:
                    final = event.type
    except OpenAIError as e:
        raise PerceptionError(f"SAM 3.1 request failed: {e}") from e
    if final != "response.completed":
        raise PerceptionError(f"SAM 3.1 stream ended without completing ({final or 'no final event'})")
    return "".join(chunks)


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
