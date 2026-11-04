"""Typed boundaries for the agents. Model output is parsed into these models and rejected if it doesn't fit."""
import re
from typing import Annotated, Literal

from pydantic import AfterValidator, BaseModel, ConfigDict, Field

Risk = Literal["low", "medium", "high", "critical"]
Severity = Literal["advisory", "warning", "danger"]

# Alert text goes out by SMS. These checks are deterministic: a model can't talk its way past them.
MAX_ALERT_CHARS = 320       # about 5 Unicode SMS parts; long alerts arrive split or out of order
MAX_REPORT_CHARS = 2000
CONTROL = re.compile(r"[\x00-\x08\x0b-\x1f\x7f]")
LINK = re.compile(r"(?i)\b(?:https?://|www\.)|\b[\w-]+\.(?:com|net|org|lk|io|link|xyz)\b")
PHONE = re.compile(r"\+?\d[\d\s-]{6,}\d")  # 8+ digits: a phone number, never something a warning should contain
SINHALA = re.compile(r"[඀-෿]")
TAMIL = re.compile(r"[஀-௿]")


def _safe_text(text: str) -> str:
    if CONTROL.search(text):
        raise ValueError("contains control characters")
    if LINK.search(text):
        raise ValueError("contains a link")
    if PHONE.search(text):
        raise ValueError("contains a phone number")
    return text


def _in_script(pattern: re.Pattern, name: str):
    def check(text: str) -> str:
        if not pattern.search(text):
            raise ValueError(f"must be written in {name}")
        return text
    return check


AlertText = Annotated[str, Field(min_length=1, max_length=MAX_ALERT_CHARS), AfterValidator(_safe_text)]


class Strict(BaseModel):
    """Unknown fields are rejected, values are never coerced (a "yes" is not a bool) and can't change later."""
    model_config = ConfigDict(extra="forbid", frozen=True, strict=True)


class VillageAlert(Strict):
    severity: Severity
    en: AlertText
    si: Annotated[AlertText, AfterValidator(_in_script(SINHALA, "Sinhala"))]
    ta: Annotated[AlertText, AfterValidator(_in_script(TAMIL, "Tamil"))]


class OfficerReport(Strict):
    report: Annotated[str, Field(min_length=1, max_length=MAX_REPORT_CHARS), AfterValidator(
        lambda text: _safe_text(text.replace("\n", " ")) and text)]


class Proposal(Strict):
    """What the responder agent proposes. Nothing here is sent until the policy guard has decided."""
    alerts: list[VillageAlert] = []
    officer_report: OfficerReport | None = None


class Assessment(Strict):
    behaviour: Literal["calm", "alert", "warning", "aggressive", "conflict-risk"]
    calves: bool
    reasoning: str = Field(min_length=1, max_length=1000)


class ResponderOutput(Strict):
    """Everything the responder returns. Parsed from model text; anything that doesn't fit is rejected."""
    assessment: Assessment
    proposal: Proposal


class Verdict(Strict):
    """The verifier's check of the responder's output. It can't change levels; only the guard sets them."""
    decision: Literal["approve", "revise", "reject"]
    reasons: list[Annotated[str, Field(max_length=300)]] = Field(default=[], max_length=5)
    aggression: bool = False


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
