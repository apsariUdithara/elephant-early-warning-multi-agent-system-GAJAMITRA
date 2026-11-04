"""Run the labelled eval cases. SPENDS META CREDITS, so it only runs with --yes-spend-credits.

Set the Meta API key in the environment first (same as the prototype), then:

    cd ai-service; ..\\.venv\\Scripts\\python ..\\evals\\run.py --yes-spend-credits
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "ai-service"))
from gajamitra.agent import meta_chat  # noqa: E402
from gajamitra.evals import load_cases, report, run_all  # noqa: E402

p = argparse.ArgumentParser()
p.add_argument("--cases", default=str(Path(__file__).with_name("cases.json")))
p.add_argument("--yes-spend-credits", action="store_true")
a = p.parse_args()
if not a.yes_spend_credits:
    sys.exit("This calls the Meta API and costs credits. Re-run with --yes-spend-credits.")
print(report(run_all(load_cases(a.cases), meta_chat)))
