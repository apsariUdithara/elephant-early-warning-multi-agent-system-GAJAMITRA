"""Perception failures must surface as errors, never as "no elephants" (safety invariant 1). No API calls."""
import io
import json
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


@pytest.fixture(autouse=True)
def isolated_cache(tmp_path, monkeypatch):
    monkeypatch.setattr(perception, "SAM_CACHE_DIR", tmp_path / "sam-cache")
    return tmp_path / "sam-cache"


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
    assert client.options == [{"timeout": perception.SAM_TIMEOUT_S, "max_retries": 0}]


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


def test_completed_result_is_cached_and_reused(monkeypatch, clip, isolated_cache):
    fake_meta(monkeypatch, [("response.output_text.delta", BOXES), ("response.completed", None)])
    first = segment_video(clip)
    fake_meta(monkeypatch, error=AssertionError("SAM must not be called again for the same clip"))
    assert segment_video(clip) == first
    assert len(list(isolated_cache.glob("*.json"))) == 1


def test_failed_result_is_not_cached(monkeypatch, clip, isolated_cache):
    fake_meta(monkeypatch, [("response.output_text.delta", BOXES), ("response.failed", None)])
    with pytest.raises(PerceptionError):
        segment_video(clip)
    assert not isolated_cache.exists() or not list(isolated_cache.iterdir())


def test_cache_key_depends_on_clip_and_prompt(monkeypatch, clip, tmp_path, isolated_cache):
    fake_meta(monkeypatch, [("response.completed", None)])
    other = tmp_path / "other.mp4"
    other.write_bytes(b"a different clip")
    segment_video(clip)
    segment_video(other)
    segment_video(clip, concept="calf")
    assert len(list(isolated_cache.glob("*.json"))) == 3


def cached_file(cache_dir):
    (entry,) = cache_dir.glob("*.json")
    return entry


@pytest.mark.parametrize("damage", [
    lambda text: "",                                                   # zero-byte file (crash before flush)
    lambda text: text[: len(text) // 2],                               # cut short
    lambda text: text.replace("x1=10", "x1=99"),                       # altered boxes: hash no longer matches
    lambda text: json.dumps({**json.loads(text), "text": "", "len": 0}),  # altered to "no elephants", stale hash
])
def test_damaged_cache_entry_is_a_miss_never_no_elephants(monkeypatch, clip, isolated_cache, damage):
    # Invariant 1: a cache file that doesn't verify must never read as an empty result.
    fake_meta(monkeypatch, [("response.output_text.delta", BOXES), ("response.completed", None)])
    expected = segment_video(clip)
    entry = cached_file(isolated_cache)
    entry.write_text(damage(entry.read_text(encoding="utf-8")), encoding="utf-8")
    calls = []
    client = fake_meta(monkeypatch, [("response.output_text.delta", BOXES), ("response.completed", None)])
    client.files.create = lambda **kw: calls.append(1) or SimpleNamespace(id="file-2")
    assert segment_video(clip) == expected   # SAM was asked again, not the damaged file
    assert calls == [1]


def test_unreadable_output_is_not_cached(monkeypatch, clip, isolated_cache):
    fake_meta(monkeypatch, [("response.output_text.delta", "Service unavailable"), ("response.completed", None)])
    with pytest.raises(PerceptionError):
        segment_video(clip)
    assert not isolated_cache.exists() or not list(isolated_cache.iterdir())


def test_unwritable_cache_still_returns_the_result(monkeypatch, clip, tmp_path):
    blocked = tmp_path / "not-a-dir"
    blocked.write_text("a file where the cache folder should be")
    monkeypatch.setattr(perception, "SAM_CACHE_DIR", blocked / "sam-cache")
    fake_meta(monkeypatch, [("response.output_text.delta", BOXES), ("response.completed", None)])
    _, size = segment_video(clip)
    assert size == (200, 100)


def test_timeout_in_the_middle_of_the_stream_is_a_perception_failure(monkeypatch, clip):
    from openai import APITimeoutError

    class BrokenStream(FakeStream):
        def __enter__(self):
            def events():
                yield SimpleNamespace(type="response.output_text.delta", delta=BOXES)
                raise APITimeoutError(request=None)
            return events()

    client = fake_meta(monkeypatch, [])
    client.responses.stream = lambda **kw: BrokenStream([])
    with pytest.raises(PerceptionError):
        segment_video(clip)


def test_stream_longer_than_the_deadline_is_a_perception_failure(monkeypatch, clip):
    clock = iter([0.0, 10.0, perception.SAM_DEADLINE_S + 1])
    monkeypatch.setattr(perception.time, "monotonic", lambda: next(clock))
    fake_meta(monkeypatch, [("response.output_text.delta", BOXES), ("response.output_text.delta", BOXES),
                            ("response.completed", None)])
    with pytest.raises(PerceptionError, match="longer than"):
        segment_video(clip)


def test_cache_key_depends_on_the_model(monkeypatch, clip, isolated_cache):
    fake_meta(monkeypatch, [("response.completed", None)])
    segment_video(clip)
    monkeypatch.setattr(perception, "SAM_MODEL", "sam-3.2")
    segment_video(clip)
    assert len(list(isolated_cache.glob("*.json"))) == 2
