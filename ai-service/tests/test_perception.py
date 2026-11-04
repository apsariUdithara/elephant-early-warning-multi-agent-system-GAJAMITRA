"""Perception failures must surface as errors, never as "no elephants" (safety invariant 1). No API calls."""
import io
from types import SimpleNamespace

import pytest
from fastapi import HTTPException, UploadFile

from gajamitra import app as web
from gajamitra import perception
from gajamitra.perception import PerceptionError, segment_video

BOXES = "<0f>0<|box;x1=10;y1=10;x2=30;y2=50;w=200;h=100|>"


class FakeStream:
    def __init__(self, events):
        self.events = events

    def __enter__(self):
        return iter(self.events)

    def __exit__(self, *exc):
        return False


def fake_meta(monkeypatch, events=None, error=None):
    def stream(**kwargs):
        if error:
            raise error
        return FakeStream([SimpleNamespace(type=t, delta=d) for t, d in events])

    client = SimpleNamespace(files=SimpleNamespace(create=lambda **kw: SimpleNamespace(id="file-1")),
                             responses=SimpleNamespace(stream=stream), options=[])
    client.with_options = lambda **kw: client.options.append(kw) or client
    monkeypatch.setattr(perception, "meta", lambda: client)
    return client


@pytest.fixture
def clip(tmp_path):
    path = tmp_path / "clip.mp4"
    path.write_bytes(b"not really a video")
    return str(path)


def test_completed_stream_returns_boxes(monkeypatch, clip):
    fake_meta(monkeypatch, [("response.output_text.delta", BOXES), ("response.completed", None)])
    frames, size = segment_video(clip)
    assert size == (200, 100) and set(frames[0]) == {0}


def test_completed_stream_without_matches_means_no_elephants(monkeypatch, clip):
    fake_meta(monkeypatch, [("response.completed", None)])
    assert segment_video(clip) == ({}, None)


@pytest.mark.parametrize("events", [
    [("response.output_text.delta", BOXES), ("response.failed", None)],  # failed after partial output
    [("response.incomplete", None)],
    [("error", None)],
    [("response.output_text.delta", BOXES)],                             # stream cut off: no final event
])
def test_stream_that_does_not_complete_is_a_perception_failure(monkeypatch, clip, events):
    fake_meta(monkeypatch, events)
    with pytest.raises(PerceptionError):
        segment_video(clip)


def test_unreadable_output_is_a_perception_failure(monkeypatch, clip):
    fake_meta(monkeypatch, [("response.output_text.delta", "Service unavailable"), ("response.completed", None)])
    with pytest.raises(PerceptionError):
        segment_video(clip)


def test_api_error_is_a_perception_failure(monkeypatch, clip):
    from openai import APIConnectionError
    fake_meta(monkeypatch, error=APIConnectionError(request=None))
    with pytest.raises(PerceptionError):
        segment_video(clip)


def test_sam_calls_use_the_explicit_timeout(monkeypatch, clip):
    client = fake_meta(monkeypatch, [("response.completed", None)])
    segment_video(clip)
    assert client.options == [{"timeout": perception.SAM_TIMEOUT_S}]


def test_app_reports_perception_failure_as_502_not_low_risk(monkeypatch, caplog):
    def failing_analyze(path, boundary):
        raise PerceptionError("SAM 3.1 stream ended without completing (response.failed)")

    monkeypatch.setattr(web, "analyze", failing_analyze)
    upload = UploadFile(io.BytesIO(b"clip"), filename="clip.mp4")
    with pytest.raises(HTTPException) as err:
        web.analyze_video(video=upload, boundary="0,0.5,1,0.5,0.5,0.9")
    assert err.value.status_code == 502
    assert "elephants may be present" in err.value.detail
    assert any("perception failed for clip.mp4" in r.getMessage() and r.levelname == "ERROR" for r in caplog.records)
