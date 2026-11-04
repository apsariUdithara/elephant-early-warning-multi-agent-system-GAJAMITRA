"""Ports to the outside world for the agent graph, and the stub used by the CLI and tests.

The graph never performs side effects itself: every action goes through a ToolClient. In production that is the
Spring Boot MCP server (plan Phases 3-4), which re-checks policy, stores idempotency keys and sends SMS.
Contract (plan Phase 3, MCP tools): idempotency keys are "{analysis_id}:{stage}:{index}" with stage "now" or
"escalation" (the officer report uses index "report"); a side-effect tool answers {"status": "accepted"} or
{"status": "rejected", "reason": ...}; record_assessment answers {"status": "recorded"}.
"""
from typing import Literal, Protocol

from gajamitra.policy import load_table
from gajamitra.schemas import OfficerReport, VillageAlert

Stage = Literal["NOW", "ESCALATION"]
ACCEPTED = frozenset({"accepted", "simulated"})  # "simulated": the stub and the prototype send nothing for real


class ModelUnavailable(RuntimeError):
    """A model call failed or timed out. Callers fall back to the deterministic guard; they never wait silently."""


class ChatModel(Protocol):
    def __call__(self, messages: list[dict]) -> str: ...  # raises ModelUnavailable


class ToolClient(Protocol):
    def record_assessment(self, analysis_id: str, risk: str, summary: dict) -> dict: ...
    def get_site_procedures(self, site_id: str) -> dict: ...
    def get_site_history(self, site_id: str, days: int) -> list[dict]: ...
    def send_village_alert(self, analysis_id: str, idempotency_key: str, stage: Stage, alert: VillageAlert) -> dict: ...
    def notify_wildlife_officer(self, analysis_id: str, idempotency_key: str, report: OfficerReport) -> dict: ...


def alert_key(analysis_id: str, stage: Stage, index: int | str) -> str:
    return f"{analysis_id}:{stage.lower()}:{index}"


class StubToolClient:
    """In-memory stand-in for Spring's MCP tools, with the same contract rules as the planned Spring side:
    a repeated idempotency key returns the stored result and sends nothing (invariant 4); a key that doesn't belong
    to the analysis and stage is rejected; an alert with no stored risk (invariant 3), above the table maximum, or
    above the stage limit (send_now_severity / escalation_severity) is rejected. Records every call."""

    def __init__(self, sites=None):
        self.sites = sites or {}
        self.calls, self.deliveries, self.results, self.risks = [], [], {}, {}

    def record_assessment(self, analysis_id, risk, summary):
        self.calls.append(("record_assessment", analysis_id, risk))
        self.risks[analysis_id] = risk
        return {"status": "recorded"}

    def get_site_procedures(self, site_id):
        self.calls.append(("get_site_procedures", site_id))
        return self.sites.get(site_id, {"name": None, "requires_approval": True})

    def get_site_history(self, site_id, days):
        self.calls.append(("get_site_history", site_id, days))
        return []

    def send_village_alert(self, analysis_id, idempotency_key, stage, alert):
        self.calls.append(("send_village_alert", idempotency_key, alert.severity))
        if not idempotency_key.startswith(alert_key(analysis_id, stage, "")):
            return {"status": "rejected", "reason": "idempotency key doesn't match the analysis and stage"}
        if idempotency_key in self.results:
            return self.results[idempotency_key]
        risk = self.risks.get(analysis_id)
        if risk is None:
            return {"status": "rejected", "reason": "no stored risk for this analysis"}
        table = load_table()
        rule, rank = table.risk[risk], table.severity_order.index
        limit = rule.send_now_severity if stage == "NOW" else rule.escalation_severity
        if limit is None or rank(alert.severity) > rank(limit):
            return {"status": "rejected", "reason": f"{alert.severity} not allowed at {stage} for {risk} risk"}
        self.deliveries.append((idempotency_key, alert.severity))
        self.results[idempotency_key] = {"status": "simulated"}
        return self.results[idempotency_key]

    def notify_wildlife_officer(self, analysis_id, idempotency_key, report):
        self.calls.append(("notify_wildlife_officer", idempotency_key))
        if idempotency_key not in self.results:
            self.deliveries.append((idempotency_key, "officer"))
            self.results[idempotency_key] = {"status": "simulated"}
        return self.results[idempotency_key]
