"""Generates docs/architecture/05-data-model.svg. Run from the repo root."""
from html import escape

W, HDR, ROW = 330, 32, 20
COL = {1: 40, 2: 440, 3: 840}
T = {  # name: (col, y, rows)
    "site": (1, 90, ["id  UUID  PK", "name", "villages", "officer_contact", "mode  SHADOW | LIVE",
                     "requires_approval  bool", "approval_timeout_s  default 120"]),
    "subscriber": (1, 330, ["id  UUID  PK", "site_id  FK → site", "phone", "language  EN | SI | TA"]),
    "alert_delivery": (1, 500, ["id  UUID  PK", "alert_id  FK → alert", "subscriber_id  FK → subscriber",
                                "status, provider_id", "UNIQUE (alert_id, subscriber_id)"]),
    "analysis": (2, 90, ["id  UUID  PK  (= Langfuse trace id)", "site_id  FK → site", "video_path",
                         "boundary  6 × 0–1", "status  QUEUED … COMPLETED | FAILED", "risk  set by record_assessment",
                         "summary  JSONB", "result  JSONB", "timings, cost", "created_at"]),
    "alert": (2, 400, ["id  UUID  PK", "analysis_id  FK → analysis", "idempotency_key  UNIQUE",
                       "stage  NOW | ESCALATION", "severity  advisory | warning | danger",
                       "text_en, text_si, text_ta", "shadow  bool"]),
    "officer_report": (2, 640, ["id  UUID  PK", "analysis_id  FK → analysis", "idempotency_key  UNIQUE", "text"]),
    "approval_request": (3, 90, ["id  UUID  PK", "analysis_id  FK → analysis", "proposed_alert  JSONB",
                                 "status  PENDING | APPROVED | REJECTED | TIMED_OUT", "expires_at",
                                 "decided_by  FK → app_user", "note, decided_at"]),
    "app_user": (3, 330, ["id  UUID  PK", "username  UNIQUE", "password_hash  BCrypt", "role  ADMIN | OFFICER"]),
    "audit_log": (3, 500, ["id  UUID  PK", "analysis_id  FK → analysis", "actor  AGENT | OFFICER | SYSTEM",
                           "action", "payload  JSONB", "at  timestamptz"]),
}


def box(name):
    c, y, rows = T[name]
    return COL[c], y, W, HDR + ROW * len(rows) + 10


out = []
for name, (c, y, rows) in T.items():
    x, y, w, h = box(name)
    out.append(f'  <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" class="tbl"/>')
    out.append(f'  <path d="M{x},{y+HDR} H{x+w}" stroke="#15803d"/>')
    out.append(f'  <rect x="{x}" y="{y}" width="{w}" height="{HDR}" rx="8" class="hdr"/>')
    out.append(f'  <text class="tn" x="{x+14}" y="{y+21}">{name}</text>')
    for i, r in enumerate(rows):
        cls = "k" if ("PK" in r or "FK" in r or "UNIQUE" in r) else "c"
        out.append(f'  <text class="{cls}" x="{x+14}" y="{y+HDR+16+ROW*i}">{escape(r)}</text>')

# relations: (path, label_at_1, label_at_many)
def rel(d, one, many):
    out.append(f'  <path class="rel" d="{d}"/>')
    for (x, y), s in ((one, "1"), (many, "*")):
        out.append(f'  <text class="card" x="{x}" y="{y}">{s}</text>')

rel("M370,160 H440", (376, 154), (428, 154))                      # site -> analysis
rel("M205,272 V330", (211, 288), (211, 324))                      # site -> subscriber
rel("M205,452 V500", (211, 468), (211, 494))                      # subscriber -> alert_delivery
rel("M605,332 V400", (611, 350), (611, 394))                      # analysis -> alert
rel("M440,560 H370", (430, 554), (374, 554))                      # alert -> alert_delivery
rel("M770,160 H840", (776, 154), (828, 154))                      # analysis -> approval_request
rel("M770,270 H820 V580 H840", (776, 264), (828, 574))            # analysis -> audit_log
rel("M770,300 H795 V700 H770", (776, 294), (776, 694))            # analysis -> officer_report
rel("M1005,330 V272", (1011, 326), (1011, 288))                   # app_user -> approval_request

H = 860
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1210 {H}" width="1210" height="{H}" role="img" aria-labelledby="t5">
  <title id="t5">GajaMitra data model</title>
  <defs>
    <style>
      text{{font-family:-apple-system,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;fill:#1f2933}}
      .title{{font-size:22px;font-weight:700}}.sub{{font-size:13px;fill:#52606d}}
      .tn{{font-size:14px;font-weight:700;fill:#14532d}}.c{{font-size:12px;fill:#334e68}}.k{{font-size:12px;fill:#1f2933;font-weight:600}}
      .tbl{{fill:#ffffff;stroke:#15803d;stroke-width:1.5}}.hdr{{fill:#dcfce7;stroke:#15803d;stroke-width:1.5}}
      .rel{{stroke:#3e4c59;stroke-width:1.5;fill:none}}.card{{font-size:12px;font-weight:700;fill:#b45309}}
      .lg{{fill:#eff6ff;stroke:#1d4ed8;stroke-width:1.5;stroke-dasharray:6 4}}.s{{font-size:12px;fill:#52606d}}
    </style>
  </defs>
  <rect width="1210" height="{H}" fill="#ffffff"/>
  <text class="title" x="40" y="44">GajaMitra · Data model (PostgreSQL)</text>
  <text class="sub" x="40" y="68">Schema public, owned by Spring Boot through Flyway. UNIQUE keys make every alert, report and SMS happen at most once.</text>
''' + "\n".join(out) + f'''
  <rect class="lg" x="40" y="720" width="330" height="100" rx="8"/>
  <text class="tn" x="54" y="745" style="fill:#1e3a8a">schema langgraph</text>
  <text class="s" x="54" y="769">Checkpoint tables created by AsyncPostgresSaver.setup()</text>
  <text class="s" x="54" y="789">Owned by the Python worker, never touched by Flyway</text>
  <text class="s" x="54" y="809">thread_id = analysis.id</text>
</svg>
'''
open("docs/architecture/05-data-model.svg", "w", encoding="utf-8").write(svg)
for n in T:
    print(n, box(n))
