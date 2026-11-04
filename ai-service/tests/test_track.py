from gajamitra.track import assess, parse_sam

# Two frames of SAM 3.1 output; the mask payload deliberately contains '|' (valid in base85).
SAMPLE = ("<0f>0<|box;x1=10;y1=10;x2=30;y2=50;w=200;h=100|><|mask;x=0;y=0;data=2,2,~a|b|>,"
          "2<|box;x1=150;y1=0;x2=170;y2=20;w=200;h=100|>"
          "<10f>0<|box;x1=10;y1=20;x2=30;y2=60;w=200;h=100|>")


def test_track():
    frames, size = parse_sam(SAMPLE)
    assert size == (200, 100)
    assert set(frames[0]) == {0, 2} and set(frames[10]) == {0}

    # Boundary along y=80, village below it. Elephant 0's feet go 50 -> 60 px in 1 s (fps 10): 20 px away at 10 px/s.
    s = assess(frames, 10, (0, 80, 200, 80, 100, 95))
    e0 = next(e for e in s["elephants"] if e["id"] == 0)
    assert e0["eta_s"] == 2.0 and e0["approaching"]
    assert s["risk"] == "high" and s["herd_size"] == 2

    # Village above the line instead: elephant 0 (y=60) is already on the village side.
    s = assess(frames, 10, (0, 80, 200, 80, 100, 5))
    assert s["risk"] == "critical"

    # Standing still 10 px from the line (box 30 px tall, so within 3 body heights): not approaching, but not "low".
    still = {0: {0: (10, 40, 30, 70)}, 10: {0: (10, 40, 30, 70)}}
    assert assess(still, 10, (0, 80, 200, 80, 100, 95))["risk"] == "medium"
    assert assess(still, 10, (0, 200, 200, 200, 100, 215))["risk"] == "low"  # 130 px away: far
