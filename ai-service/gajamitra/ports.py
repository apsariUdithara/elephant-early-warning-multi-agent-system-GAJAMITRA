"""Ports to the outside world for the agent graph, and the stub used by the CLI and tests.

The graph never performs side effects itself: every action goes through a ToolClient. In production that is the
Spring Boot MCP server (plan Phases 3-4), which re-checks policy, stores idempotency keys and sends SMS.
"""
from typing import Protocol

from gajamitra.policy import load_table
from gajamitra.schemas import OfficerReport, VillageAlert


class ChatModel(Protocol):
    def __call__(self, messages: list[dict]) -> str: ...


class ToolClient(Protocol):
    def record_assessment(self, analysis_id: str, risk: str, summary: dict) -> dict: ...
    def get_site_procedures(self, site_id: str) -> dict: ...
    def get_site_history(self, site_id: str, days: int) -> list[dict]: ...
    def send_village_alert(self, analysis_id: str, idempotency_key: str, alert: VillageAlert) -> dict: ...
    def notify_wildlife_officer(self, analysis_id: str, idempotency_key: str, report: OfficerReport) -> dict: ...


class StubToolClient:
    """In-memory stand-in for Spring's MCP tools, with the same contract rules (plan Phase 3, MCP server):
    a repeated idempotency key returns the stored result and sends nothing (invariant 4); an alert with no stored
    risk, or above the policy table's maximum for that risk, is rejected (invariant 3). Records every call."""

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

    def send_village_alert(self, analysis_id, idempotency_key, alert):
        self.calls.append(("send_village_alert", idempotency_key, alert.severity))
        if idempotency_key in self.results:
            return self.results[idempotency_key]
        risk = self.risks.get(analysis_id)
        if risk is None:
            return {"status": "rejected", "reason": "no stored risk for this analysis"}
        table = load_table()
        limit = table.risk[risk].max_severity
        if limit is None or table.severity_order.index(alert.severity) > table.severity_order.index(limit):
            return {"status": "rejected", "reason": f"{alert.severity} is above the maximum for {risk} risk"}
        self.deliveries.append((idempotency_key, alert.severity))
        self.results[idempotency_key] = {"status": "simulated"}
        return self.results[idempotency_key]

    def notify_wildlife_officer(self, analysis_id, idempotency_key, report):
        self.calls.append(("notify_wildlife_officer", idempotency_key))
        if idempotency_key not in self.results:
            self.deliveries.append((idempotency_key, "officer"))
            self.results[idempotency_key] = {"status": "simulated"}
        return self.results[idempotency_key]
