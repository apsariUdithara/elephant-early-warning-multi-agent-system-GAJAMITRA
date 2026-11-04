"""Typed boundaries for the agents. Model output is parsed into these models and rejected if it doesn't fit."""
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Risk = Literal["low", "medium", "high", "critical"]
Severity = Literal["advisory", "warning", "danger"]


class Strict(BaseModel):
    """Unknown fields are rejected and values can't change after validation."""
    model_config = ConfigDict(extra="forbid", frozen=True)


class VillageAlert(Strict):
    severity: Severity
    en: str = Field(min_length=1)
    si: str = Field(min_length=1)
    ta: str = Field(min_length=1)


class OfficerReport(Strict):
    report: str = Field(min_length=1)


class Proposal(Strict):
    """What the responder agent proposes. Nothing here is sent until the policy guard has decided."""
    alerts: list[VillageAlert] = []
    officer_report: OfficerReport | None = None


class GuardDecision(Strict):
    send_now: list[VillageAlert]
    officer_report: OfficerReport | None
    escalation: list[VillageAlert]
    hold_for_approval: bool
    notes: list[str]


class RiskRule(Strict):
    max_severity: Severity | None
    send_now_severity: Severity | None
    escalation_severity: Severity | None
    officer_report: bool
    hold_escalation: bool


class PolicyTable(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)  # ignores the "_doc" note
    severity_order: list[Severity]
    risk: dict[Risk, RiskRule]
