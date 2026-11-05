"""Generates docs/architecture/04-analysis-sequence.svg. Run from the repo root."""
from html import escape

X = {"B": 110, "S": 340, "Q": 560, "W": 780, "M": 1000, "T": 1190}
HEAD = [("B", "Browser", "React dashboard", "fe"), ("S", "Spring Boot", "backend", "sb"),
        ("Q", "RabbitMQ", "queues", "inf"), ("W", "AI worker", "LangGraph", "py"),
        ("M", "Meta Model API", "SAM 3.1 · Muse Spark", "ext"), ("T", "Twilio", "SMS", "ext")]
# (kind, from, to, label) ; kind: m=message, r=reply(dashed), n=note on lifeline `from`
STEPS = [
    ("m", "B", "S", "POST /api/analyses (MP4, boundary, siteId)"),
    ("n", "S", None, "validate · store MP4 · insert analysis QUEUED"),
    ("r", "S", "B", "202 Accepted {id}"),
    ("m", "B", "S", "GET /api/analyses/{id}/events (SSE)"),
    ("m", "S", "Q", "analysis.requests"),
    ("m", "Q", "W", "deliver (prefetch 1)"),
    ("m", "W", "M", "SAM 3.1: clip + \"elephant\""),
    ("r", "M", "W", "boxes + stable IDs (stream)"),
    ("n", "W", None, "track.assess → risk = high"),
    ("m", "W", "S", "MCP record_assessment(risk) — sync, before any LLM"),
    ("m", "W", "M", "responder: keyframes + summary"),
    ("m", "W", "M", "verifier: same evidence"),
    ("n", "W", None, "policy_guard → warning now, danger held"),
    ("m", "W", "S", "MCP send_village_alert(warning, key …:now:0)"),
    ("m", "S", "T", "SMS warning in EN / SI / TA (LIVE site)"),
    ("n", "W", None, "interrupt() · checkpoint saved · message acked"),
    ("m", "W", "Q", "event: approval_required"),
    ("m", "Q", "S", "deliver event"),
    ("m", "S", "B", "SSE: approval card + countdown"),
    ("m", "B", "S", "POST /api/approvals/{id} approve"),
    ("n", "S", None, "or the timeout job: same conditional update"),
    ("m", "S", "Q", "analysis.resume {approve | timeout}"),
    ("m", "Q", "W", "deliver"),
    ("m", "W", "S", "MCP send_village_alert(danger, key …:escalation:0)"),
    ("m", "S", "T", "SMS danger in EN / SI / TA"),
    ("m", "W", "Q", "event: completed"),
    ("m", "Q", "S", "deliver event"),
    ("m", "S", "B", "SSE: completed"),
]
Y0, DY = 175, 40
H = Y0 + DY * len(STEPS) + 40
out = [f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1300 {H}" width="1300" height="{H}" role="img" aria-labelledby="t4">
  <title id="t4">GajaMitra analysis sequence</title>
  <defs>
    <marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0L10,5L0,10z" fill="#3e4c59"/></marker>
    <style>
      text{{font-family:-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;fill:#1f2933}}
      .title{{font-size:26px;font-weight:700}}.sub{{font-size:16px;fill:#52606d}}
      .h{{font-size:17px;font-weight:600}}.xs{{font-size:14px;fill:#52606d}}
      .lbl{{font-size:15px;fill:#1f2933;paint-order:stroke;stroke:#fff;stroke-width:4px}}
      .line{{stroke:#3e4c59;stroke-width:1.5;fill:none}}.reply{{stroke-dasharray:6 4}}
      .life{{stroke:#9aa5b1;stroke-width:1;stroke-dasharray:4 4}}
      .fe{{fill:#eef2ff;stroke:#4f46e5;stroke-width:1.5}}.sb{{fill:#ecfdf3;stroke:#15803d;stroke-width:1.5}}
      .py{{fill:#eff6ff;stroke:#1d4ed8;stroke-width:1.5}}.inf{{fill:#fffbeb;stroke:#b45309;stroke-width:1.5}}
      .ext{{fill:#f8fafc;stroke:#64748b;stroke-width:1.5;stroke-dasharray:6 4}}
      .note{{fill:#fefce8;stroke:#ca8a04;stroke-width:1}}
      .frame{{fill:none;stroke:#c2410c;stroke-width:1.5;stroke-dasharray:8 4}}
    </style>
  </defs>
  <rect width="1300" height="{H}" fill="#ffffff"/>
  <text class="title" x="40" y="44">GajaMitra · One analysis, end to end (high risk, site requires approval)</text>
  <text class="sub" x="40" y="68">The warning goes out before anyone is asked. Only the escalation to danger waits for the officer, and a timeout sends it anyway.</text>''']
for k, name, sub, cls in HEAD:
    x = X[k]
    out.append(f'  <rect class="{cls}" x="{x-75}" y="90" width="150" height="48" rx="8"/>')
    out.append(f'  <text class="h" x="{x}" y="110" text-anchor="middle">{escape(name)}</text>')
    out.append(f'  <text class="xs" x="{x}" y="127" text-anchor="middle">{escape(sub)}</text>')
    out.append(f'  <line class="life" x1="{x}" y1="138" x2="{x}" y2="{H-20}"/>')
# approval frame: from "event: approval_required" to the escalation SMS
i0 = next(i for i, s in enumerate(STEPS) if s[3] == "event: approval_required")
i1 = next(i for i, s in enumerate(STEPS) if s[3].startswith("SMS danger"))
fy0, fy1 = Y0 + DY * i0 - 30, Y0 + DY * i1 + 16
out.append(f'  <rect class="frame" x="22" y="{fy0}" width="1256" height="{fy1-fy0}" rx="6"/>')
out.append(f'  <text class="lbl" x="32" y="{fy0+16}" style="fill:#c2410c;font-weight:600">human in the loop: escalation only</text>')
for i, (kind, a, b, label) in enumerate(STEPS):
    y = Y0 + DY * i
    if kind == "n":
        w = 7 * len(label) + 24
        out.append(f'  <rect class="note" x="{X[a]-w/2:.0f}" y="{y-16}" width="{w}" height="24" rx="4"/>')
        out.append(f'  <text class="xs" x="{X[a]}" y="{y}" text-anchor="middle" style="fill:#713f12">{escape(label)}</text>')
        continue
    x1, x2 = X[a], X[b]
    x2e = x2 - 4 if x2 > x1 else x2 + 4
    cls = "line reply" if kind == "r" else "line"
    out.append(f'  <path class="{cls}" d="M{x1},{y} H{x2e}" marker-end="url(#a)"/>')
    out.append(f'  <text class="lbl" x="{(x1+x2)/2:.0f}" y="{y-7}" text-anchor="middle">{escape(label)}</text>')
out.append("</svg>\n")
open("docs/architecture/04-analysis-sequence.svg", "w", encoding="utf-8").write("\n".join(out))
print("ok", H)
