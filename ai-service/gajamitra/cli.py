"""Run the agent graph on one clip, with stub tools (no Spring Boot, nothing really sent). Calls the Meta API.

    cd ai-service
    $env:MODEL_API_KEY = "LLM|..."
    ..\\.venv\\Scripts\\python -m gajamitra.cli path\\to\\clip.mp4 --boundary 0,0.5,1,0.5,0.5,0.9 --approval approve

The boundary is six 0-1 numbers: two points on the village boundary line, then one point on the village side.
If the run waits for officer approval, it is resumed with --approval (approve, reject or timeout).
"""
import argparse
import json
import sys
import uuid

from langgraph.checkpoint.memory import InMemorySaver

from gajamitra.agent import meta_chat
from gajamitra.graph import build_graph, resume, start
from gajamitra.perception import PerceptionError
from gajamitra.ports import StubToolClient


def boundary(text):
    values = [float(v) for v in text.split(",")]
    if len(values) != 6 or not all(0 <= v <= 1 for v in values):
        raise argparse.ArgumentTypeError("six numbers between 0 and 1: ax,ay,bx,by,px,py")
    return values


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("clip")
    parser.add_argument("--boundary", type=boundary, default=boundary("0,0.5,1,0.5,0.5,0.9"))
    parser.add_argument("--site-name", default=None, help="place name used in template alerts")
    parser.add_argument("--no-approval", action="store_true", help="site doesn't require officer approval")
    parser.add_argument("--approval", choices=["approve", "reject", "timeout"], default="approve",
                        help="officer answer if the run waits for approval (default: approve)")
    args = parser.parse_args(argv)

    tools = StubToolClient({"site": {"name": args.site_name, "requires_approval": not args.no_approval}})
    graph = build_graph(tools, meta_chat, InMemorySaver())
    analysis_id = uuid.uuid4().hex
    try:
        state = start(graph, analysis_id, "site", args.clip, args.boundary)
        if "__interrupt__" in state:
            print(f"Waiting for officer approval of the escalation; answering '{args.approval}'.", file=sys.stderr)
            state = resume(graph, analysis_id, args.approval)
    except PerceptionError as e:
        print(f"Perception failed, so elephants may be present: treat the risk as unknown. ({e})", file=sys.stderr)
        return 2
    print(json.dumps({"analysis_id": analysis_id, "risk": state["summary"]["risk"],
                      "herd_size": state["summary"]["herd_size"],
                      "assessment": (state.get("responder") or {}).get("assessment"),
                      "verdict": state.get("verdict"), "decision": state.get("decision"),
                      "approval": state.get("approval"), "dispatched": state.get("dispatched", []),
                      "notes": state.get("notes", [])}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
