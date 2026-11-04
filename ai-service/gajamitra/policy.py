"""Deterministic policy guard: sets what the agents' proposal may send, from policy/policy_table.json.

LLMs propose; this code decides (safety invariants 1, 2, 7, 8). For each tracker risk the table gives the exact
level sent now, the level an escalation is sent at (held for approval when the site requires it) and whether the
officer is notified. The guard raises or lowers proposals to those levels, fills in template text when the
responder proposed nothing, and records every change in `notes`.
"""
import functools
from collections.abc import Mapping
from pathlib import Path
from typing import get_args

from gajamitra.schemas import GuardDecision, OfficerReport, PolicyTable, Proposal, Risk, VillageAlert

TABLE_PATH = Path(__file__).resolve().parents[2] / "policy" / "policy_table.json"

# Fallback alerts for when the responder proposes none at medium risk or above.
# The Sinhala and Tamil wording needs a native-speaker review before real use (plan Phase 6).
TEMPLATES = {
    "warning": {
        "en": "Elephant warning: {n} elephant(s) {near}. Stay indoors and keep away from the fields until officers "
              "say it is safe.",
        "si": "අලි අනතුරු ඇඟවීම: {near} අලි {n}ක් සිටී. නිලධාරීන් ආරක්ෂිත යැයි පවසන තුරු නිවස තුළම සිටින්න; "
              "කෙත්වතු වෙත යන්න එපා.",
        "ta": "யானை எச்சரிக்கை: {near} {n} யானைகள் உள்ளன. அதிகாரிகள் பாதுகாப்பானது என்று கூறும் வரை "
              "வீட்டுக்குள்ளேயே இருங்கள்; வயல்களுக்குச் செல்ல வேண்டாம்.",
    },
    "danger": {
        "en": "DANGER: {n} elephant(s) inside the village boundary {near}. Go indoors now and stay inside.",
        "si": "අලි අනතුර: {near} අලි {n}ක් ගම් සීමාව තුළට ඇතුළු වී ඇත. වහාම නිවස තුළට ගොස් එහිම සිටින්න.",
        "ta": "யானை ஆபத்து: {near} {n} யானைகள் கிராம எல்லைக்குள் நுழைந்துள்ளன. உடனே வீட்டுக்குள் சென்று "
              "அங்கேயே இருங்கள்.",
    },
}
NEAR_PLACE = {"en": "near {place}", "si": "{place} අසල", "ta": "{place} அருகே"}
NEAR_VILLAGE = {"en": "near the village", "si": "ගම අසල", "ta": "கிராமத்துக்கு அருகே"}


@functools.cache
def load_table(path: Path = TABLE_PATH) -> PolicyTable:
    """Read and check the table once. An inconsistent table fails loudly instead of guarding wrongly."""
    table = PolicyTable.model_validate_json(path.read_text(encoding="utf-8"))
    if set(table.risk) != set(get_args(Risk)):
        raise ValueError(f"policy table must define every risk level {get_args(Risk)}, got {sorted(table.risk)}")
    rank = table.severity_order.index
    for risk, rule in table.risk.items():
        for level in (rule.send_now_severity, rule.escalation_severity):
            if level and (rule.max_severity is None or rank(level) > rank(rule.max_severity)):
                raise ValueError(f"policy table: {risk} sends {level} above its max_severity {rule.max_severity}")
    return table


def template_alert(severity: str, herd_size: int, place: str | None = None) -> VillageAlert:
    texts = {}
    for lang, text in TEMPLATES[severity].items():
        near = NEAR_PLACE[lang].format(place=place) if place else NEAR_VILLAGE[lang]
        texts[lang] = text.format(n=herd_size, near=near)
    return VillageAlert(severity=severity, **texts)


def template_report(summary: Mapping) -> OfficerReport:
    elephants = summary.get("elephants", [])
    parts = [f"GajaMitra automatic report: risk {summary['risk']}, {summary['herd_size']} elephant(s) tracked"]
    if elephants:
        nearest = min(e["dist_px"] for e in elephants)
        parts.append(f"nearest {nearest} px from the boundary" + (" (on the village side)" if nearest <= 0 else ""))
    etas = [e["eta_s"] for e in elephants if e.get("eta_s") is not None]
    if etas:
        parts.append(f"earliest arrival in about {min(etas)} s")
    parts.append(f"{sum(1 for e in elephants if e.get('approaching'))} approaching")
    return OfficerReport(report="; ".join(parts) + ". Generated from tracker data because the responder wrote no report.")


def guard(summary: Mapping, proposal: Proposal, *, requires_approval: bool, aggression: bool = False,
          place: str | None = None) -> GuardDecision:
    """Decide what is sent now, what is escalated, and whether the escalation waits for the officer.

    `summary` is the tracker output (`track.assess`); its risk is the only input that sets levels.
    `aggression` is the verifier's flag; it adds an officer report at any risk.
    """
    table = load_table()
    risk = summary.get("risk")
    if risk not in table.risk:
        raise ValueError(f"unknown tracker risk {risk!r}: refusing to guess a safe level")
    rule, rank, notes = table.risk[risk], table.severity_order.index, []

    # Keep the most severe proposed alert (the first one among equals); the others are dropped.
    alerts = sorted(proposal.alerts, key=lambda a: rank(a.severity), reverse=True)
    chosen = alerts[0] if alerts else None
    if len(alerts) > 1:
        notes.append(f"{len(alerts) - 1} extra proposed alert(s) dropped; kept the {chosen.severity} one")

    send_now, escalation = [], []
    if rule.send_now_severity is None:
        if chosen:
            notes.append(f"risk {risk}: no village alert allowed; dropped the proposed {chosen.severity} alert")
    else:
        level = rule.send_now_severity
        if chosen is None:
            chosen = template_alert(level, summary["herd_size"], place)
            notes.append(f"risk {risk}: responder proposed no alert; sent the {level} template alert")
        elif chosen.severity != level:
            change = "raised" if rank(chosen.severity) < rank(level) else "lowered"
            notes.append(f"risk {risk}: {change} the alert from {chosen.severity} to {level}")
        send_now = [chosen.model_copy(update={"severity": level})]
        if rule.escalation_severity and chosen.severity == rule.escalation_severity:
            escalation = [chosen.model_copy(update={"severity": rule.escalation_severity})]

    report = proposal.officer_report
    if rule.officer_report or aggression:
        if report is None:
            report = template_report(summary)
            notes.append("officer report required but not proposed; sent the template report")
    elif report is not None:
        notes.append(f"risk {risk}: officer report not needed; dropped")
        report = None

    hold = bool(escalation) and rule.hold_escalation and requires_approval
    if escalation:
        notes.append(f"{rule.escalation_severity} escalation " + ("held for officer approval" if hold
                     else "sent after the warning (no approval required)"))
    return GuardDecision(send_now=send_now, officer_report=report, escalation=escalation,
                         hold_for_approval=hold, notes=notes)
