"""The Phase 0 script's own logic, with a fake client (the real script is only run by hand)."""
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace

import pytest

spec = importlib.util.spec_from_file_location("phase0_check", Path(__file__).parents[1] / "scripts" / "phase0_check.py")
phase0 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(phase0)


def chat_client(content=None, tool_args=None):
    calls = [SimpleNamespace(function=SimpleNamespace(name="record_risk", arguments=tool_args))] if tool_args else None
    message = SimpleNamespace(content=content, tool_calls=calls)
    return SimpleNamespace(chat=SimpleNamespace(completions=SimpleNamespace(
        create=lambda **kw: SimpleNamespace(choices=[SimpleNamespace(message=message)]))))


def result(check, client):
    report = phase0.Report()
    check(client, report)
    return report.rows[-1][1]


@pytest.mark.parametrize("content, want", [
    ('{"herd_size": 3, "risk": "medium"}', "PASS"),
    ('```json\n{"herd_size": 3, "risk": "medium"}\n```', "PASS"),
    ("Sure! Here it is: {}", "FAIL"),
])
def test_json_check(content, want):
    assert result(phase0.check_json, chat_client(content)) == want


def test_tool_check():
    assert result(phase0.check_tools, chat_client(tool_args=json.dumps({"risk": "high"}))) == "PASS"
    assert result(phase0.check_tools, chat_client(content="I can't use tools")) == "FAIL"


def test_image_check():
    assert result(phase0.check_image, chat_client("Red.")) == "PASS"
    assert result(phase0.check_image, chat_client("Blue")) == "FAIL"


def test_sam_check_saves_raw_output_and_reports_events(tmp_path, monkeypatch):
    monkeypatch.setattr(phase0, "OUT", tmp_path)
    clip = tmp_path / "clip.mp4"
    clip.write_bytes(b"x")
    events = [SimpleNamespace(type="response.output_text.delta", delta="garbage"),
              SimpleNamespace(type="response.completed", delta=None)]

    class Stream:
        def __enter__(self):
            return iter(events)

        def __exit__(self, *exc):
            return False

    client = SimpleNamespace(files=SimpleNamespace(create=lambda **kw: SimpleNamespace(id="f")),
                             responses=SimpleNamespace(stream=lambda **kw: Stream()))
    report = phase0.Report()
    phase0.check_sam(client, report, clip)
    assert (tmp_path / "sam_raw.txt").read_text() == "garbage"
    assert [r[1] for r in report.rows] == ["PASS", "FAIL"]  # stream completed; output didn't parse
