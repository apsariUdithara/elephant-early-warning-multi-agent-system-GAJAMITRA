"""Turn SAM 3.1 per-frame boxes into elephant tracks and village-boundary risk. No API calls here."""
import math
import re

FRAME = re.compile(r"<(\d+)f>")
OBJ = re.compile(r"(\d+)<\|box;([^|]*)\|>")

# Calibration knobs: tuned for short demo clips. Real camera traps watch slow herds, so raise both.
HIGH_ETA_S = 60  # predicted arrival sooner than this -> "high"
MIN_CLOSING_PX_S = 2.0  # slower drift than this is jitter, not approach
VELOCITY_WINDOW_S = 2.0
NEAR_BODIES = 3.0  # closer to the line than this many body heights -> at least "medium", even standing still


class SamOutputError(ValueError):
    """SAM 3.1 sent text that isn't the expected box format. Never treat this as "no elephants"."""


def parse_sam(text):
    """SAM 3.1 streamed text -> ({frame: {obj_id: (x1, y1, x2, y2)}}, (frame_w, frame_h)).

    Empty text means SAM matched nothing (it sends no deltas then). Non-empty text without a single
    readable box raises SamOutputError, so an error message or a format change can't pass as "no elephants".
    """
    frames, size = {}, None
    parts = FRAME.split(text)
    for i in range(1, len(parts), 2):
        boxes = {}
        for obj_id, fields in OBJ.findall(parts[i + 1]):
            kv = dict(f.split("=", 1) for f in fields.split(";") if "=" in f)
            try:
                boxes[int(obj_id)] = tuple(float(kv[k]) for k in ("x1", "y1", "x2", "y2"))
                size = (int(float(kv["w"])), int(float(kv["h"])))
            except (KeyError, ValueError) as e:
                raise SamOutputError(f"unreadable box for object {obj_id} in frame {parts[i]}: {fields[:80]!r}") from e
        frames[int(parts[i])] = boxes
    if text.strip() and size is None:
        raise SamOutputError(f"SAM output has no readable boxes: {text.strip()[:120]!r}")
    return frames, size


def tracks(frames, fps):
    """{obj_id: [(t, x, y, h)]}: box bottom-centre (where the elephant touches the ground) and box height."""
    out = {}
    for f in sorted(frames):
        for oid, (x1, y1, x2, y2) in frames[f].items():
            out.setdefault(oid, []).append((f / fps, (x1 + x2) / 2, y2, y2 - y1))
    return out


def velocity(track):
    """Least-squares px/s over the last VELOCITY_WINDOW_S seconds of a track."""
    pts = [p for p in track if p[0] >= track[-1][0] - VELOCITY_WINDOW_S]
    mt = sum(p[0] for p in pts) / len(pts)
    var = sum((p[0] - mt) ** 2 for p in pts)
    if var == 0:
        return 0.0, 0.0
    return (sum((p[0] - mt) * p[1] for p in pts) / var,
            sum((p[0] - mt) * p[2] for p in pts) / var)


def assess(frames, fps, boundary):
    """boundary = (ax, ay, bx, by, px, py) in pixels: line A-B, and P is any point on the village side."""
    ax, ay, bx, by, px, py = boundary
    ex, ey = bx - ax, by - ay
    norm = math.hypot(ex, ey) or 1.0

    def side(x, y):
        return (ex * (y - ay) - ey * (x - ax)) / norm

    village = 1 if side(px, py) > 0 else -1
    elephants = []
    for oid, tr in tracks(frames, fps).items():
        _, x, y, h = tr[-1]
        vx, vy = velocity(tr)
        dist = -village * side(x, y)  # > 0 outside the village, <= 0 already inside
        closing = village * (ex * vy - ey * vx) / norm  # > 0 means moving toward the village
        eta = dist / closing if dist > 0 and closing > MIN_CLOSING_PX_S else None
        elephants.append({"id": oid, "x": round(x), "y": round(y), "vx": round(vx, 1), "vy": round(vy, 1),
                          "dist_px": round(dist), "near": 0 < dist <= NEAR_BODIES * h,
                          "approaching": closing > MIN_CLOSING_PX_S,
                          "eta_s": None if eta is None else round(eta, 1), "seen_s": round(tr[-1][0] - tr[0][0], 1)})

    etas = [e["eta_s"] for e in elephants if e["eta_s"] is not None]
    if any(e["dist_px"] <= 0 for e in elephants):
        risk = "critical"
    elif etas and min(etas) < HIGH_ETA_S:
        risk = "high"
    elif etas or any(e["near"] for e in elephants):
        risk = "medium"
    else:
        risk = "low"
    return {"herd_size": len(elephants), "risk": risk, "elephants": elephants}
