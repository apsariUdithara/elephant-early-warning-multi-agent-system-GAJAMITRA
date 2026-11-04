"""Run the labelled eval cases. SPENDS META CREDITS, so it only runs with --yes-spend-credits.

Set the Meta API key in the environment first (same as the prototype), then:

    cd ai-service; ..\\.venv\\Scripts\\python ..\\evals\\run.py --yes-spend-credits
"""
import argparse
import os
import sys
from pathlib import Path

envfile = Path(__file__).resolve().parents[1] / ".env"
if envfile.exists():  # values go into this process only and are never printed
    for line in envfile.read_text(encoding="utf-8").splitlines():
        k, sep, v = line.partition("=")
        if sep and not k.strip().startswith("#"):
            os.environ.setdefault(k.strip(), v.strip().strip('"'))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ai-service"))
from gajamitra.agent import meta_chat  # noqa: E402
from gajamitra.evals import load_cases, report, run_all  # noqa: E402

p = argparse.ArgumentParser()
p.add_argument("--cases", default=str(Path(__file__).with_name("cases.json")))
p.add_argument("--yes-spend-credits", action="store_true")
a = p.parse_args()
if not a.yes_spend_credits:
    sys.exit("This calls the Meta API and costs credits. Re-run with --yes-spend-credits.")
root = Path(__file__).resolve().parents[1]
cases = load_cases(a.cases)
for c in cases:  # clips are listed relative to the repo root
    if c.get("clip"):
        c["clip"] = str(root / c["clip"])
print(report(run_all(cases, meta_chat)))
