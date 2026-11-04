"""GajaMitra Claude Code guard hook (stdlib only).

Modes (first argument):
  pre         PreToolUse for Bash / Read / Edit / Write: block or ask before risky actions.
  post        PostToolUse for Edit / Write: remind Claude to audit safety-critical changes.
  tests-only  PreToolUse for the test-engineer agent: allow edits to test files only.

Blocking uses exit code 2 with the reason on stderr. "Ask" uses the PreToolUse JSON output
(permissionDecision = "ask"). If the hook itself fails, it exits 1: Claude Code shows the error
and continues, and the deny rules in .claude/settings.json remain as a backstop.
Self-test: python .claude/hooks/test_guard.py
"""
import json
import os
import re
import subprocess
import sys
from pathlib import PurePosixPath

# Keep in sync with CLAUDE.md, commitlint.config.mjs, .releaserc.json and .github/workflows/pr-checks.yml.
TYPES = "feat|fix|perf|refactor|docs|style|test|build|ci|chore|revert"
SUBJECT = re.compile(rf"^({TYPES})!?: \S")

SECRETS = [
    (re.compile(r"LLM\|\d+\|[A-Za-z0-9_\-]{8,}"), "Meta Model API key"),
    (re.compile(r"\bAC[0-9a-f]{32}\b"), "Twilio account SID"),
    (re.compile(r"(?i)twilio_auth_token\s*[=:]\s*['\"]?[0-9a-f]{32}"), "Twilio auth token"),
    (re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"), "private key"),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36}\b|\bgithub_pat_[A-Za-z0-9_]{40,}"), "GitHub token"),
    (re.compile(r"\bsk-[A-Za-z0-9]{32,}"), "API secret key"),
]

SAFETY_CRITICAL = (
    "ai-service/gajamitra/track.py", "ai-service/gajamitra/perception.py", "ai-service/gajamitra/agent.py",
    "ai-service/gajamitra/policy.py", "ai-service/gajamitra/graph.py", "ai-service/gajamitra/prompts.py",
    "ai-service/gajamitra/schemas.py", "ai-service/gajamitra/worker.py", "policy/policy_table.json",
)
SAFETY_CRITICAL_DIRS = ("backend/src/main/java/com/gajamitra/alert/", "backend/src/main/java/com/gajamitra/approval/",
                        "backend/src/main/java/com/gajamitra/sms/", "backend/src/main/java/com/gajamitra/mcp/",
                        "backend/src/main/java/com/gajamitra/messaging/")
ASK_EDIT = {
    "policy/policy_table.json": "the shared safety policy table (Python and Java must change together; needs user approval)",
    ".releaserc.json": "release configuration",
    "commitlint.config.mjs": "commit message rules",
    ".claude/settings.json": "Claude Code permissions and hooks",
    ".claude/hooks/guard.py": "this guard hook",
}
CONSTANTS = re.compile(r"^\s*(HIGH_ETA_S|MIN_CLOSING_PX_S|VELOCITY_WINDOW_S|NEAR_BODIES)\s*=", re.M)
TEST_PATHS = re.compile(r"^(ai-service/tests/|backend/src/test/|frontend/src/.*\.test\.tsx?$|frontend/src/.*/__tests__/|"
                        r"\.claude/hooks/test_guard\.py$)")


class Deny(Exception):
    pass


class Ask(Exception):
    pass


def project_dir(data):
    return os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or os.getcwd()


def rel(path, root):
    """Repo-relative POSIX path, or the absolute path if it is outside the repo."""
    p = os.path.normpath(os.path.join(root, path))
    r = os.path.relpath(p, root) if os.path.splitdrive(p)[0].lower() == os.path.splitdrive(root)[0].lower() else p
    return PurePosixPath(r.replace("\\", "/")).as_posix()


def git(root, *args):
    try:
        return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True, encoding="utf-8",
                              errors="replace", timeout=10).stdout
    except (OSError, subprocess.SubprocessError):
        return ""


# ---------------------------------------------------------------- Bash

def commit_subject(cmd, root):
    """First line of the commit message, or None when it can't be read from the command."""
    m = re.search(r"(?:-m|--message)(?:\s+|=)(\"(?:[^\"\\]|\\.)*\"|'[^']*')", cmd)
    if m:
        msg = m.group(1)[1:-1]
        if msg.startswith("$(cat <<"):
            m = None
        else:
            return msg.split("\n")[0].replace('\\"', '"')
    m = re.search(r"<<-?\s*['\"]?(\w+)['\"]?\s*\n(.*?)\n\s*\1\b", cmd, re.S)
    if m:
        return m.group(2).strip("\n").split("\n")[0].strip()
    m = re.search(r"(?:-F|--file)(?:\s+|=)(\S+)", cmd)
    if m and m.group(1) != "-":
        try:
            with open(os.path.join(root, m.group(1).strip("'\"")), encoding="utf-8") as f:
                return f.readline().strip()
        except OSError:
            return None
    return None


def check_bash(cmd, root):
    c = " ".join(cmd.split()) if "\n" not in cmd else cmd
    git_cmd = r"\bgit\b(?:\s+-[Cc]\s+\S+)*\s+"

    # Merging, approving and repository settings belong to the human developer.
    if re.search(r"\bgh\s+pr\s+merge\b", c):
        raise Deny("Merging pull requests is reserved for the human developer.")
    if re.search(r"\bgh\s+pr\s+review\b.*(--approve|\s-a\b)", c):
        raise Deny("Approving pull requests is reserved for the human developer.")
    if re.search(r"\bgh\s+repo\s+edit\b", c) or (
            re.search(r"\bgh\s+api\b", c) and re.search(r"(protection|rulesets|/merge|/collaborators|/settings)", c)
            and re.search(r"(-X|--method)\s*(PUT|PATCH|POST|DELETE)", c, re.I)):
        raise Deny("Changing repository settings, branch protection or merging via the API needs the developer.")
    if re.search(r"\bgh\s+release\s+(create|edit|delete)\b", c):
        raise Deny("Releases are created only by semantic-release in CI.")

    # Identity and attribution.
    if re.search(git_cmd + r"config\b.*\buser\.(name|email)\s+\S", c):
        raise Deny("Never change the developer's Git identity.")
    pr = re.search(r"\bgh\s+pr\s+(create|edit)\b", c)
    if re.search(git_cmd + r"commit\b", c) or pr:
        text = cmd
        body_file = re.search(r"(?:--body-file|-F)(?:\s+|=)(\S+)", c) if pr else None
        if body_file:
            try:
                with open(os.path.join(root, body_file.group(1).strip("'\"")), encoding="utf-8") as f:
                    text += "\n" + f.read()
            except OSError:
                pass
        if re.search(r"co-authored-by\s*:", text, re.I) or re.search(r"generated with \[?claude code", text, re.I):
            raise Deny("No AI co-author or attribution lines in commits or PRs (team rule).")
    if pr:
        title = re.search(r"(?:--title|-t)(?:\s+|=)(\"[^\"]*\"|'[^']*')", c)
        if title and not re.match(rf"^({TYPES})!?: \S.* - GM-[1-9][0-9]*$", title.group(1)[1:-1]):
            raise Deny("PR title must be '<type>: <message> - GM-<n>' (ask the developer for the task number; "
                       f"never invent one). Got: {title.group(1)}")

    # Tags and pushes.
    if re.search(git_cmd + r"tag\b(?!\s+(-l|--list|-n|--contains|--points-at|-v|--verify)\b)\s+\S", c):
        raise Deny("Tags are created only by semantic-release. Don't tag manually.")
    push = re.search(git_cmd + r"push\b(.*)", c)
    if push:
        args = push.group(1).split("&&")[0].split(";")[0].split("|")[0]
        if re.search(r"(\s--force\b|\s--force-with-lease\b|\s-f\b|\s\+\S)", " " + args):
            raise Deny("Force-pushing needs explicit permission from the developer.")
        if re.search(r"\s--(tags|mirror|all|delete)\b|\s-d\b", " " + args):
            raise Deny("Pushing tags, mirrors, all branches or deleting remote branches needs the developer.")
        refs = [a for a in args.split() if not a.startswith("-")]
        targets = refs[1:] if len(refs) > 1 else []
        if any(re.search(r"(^|:)(refs/heads/)?main$", t) for t in targets):
            raise Deny("Never push to main. Push the task branch and open a PR.")
        if (not targets or "HEAD" in targets) and git(root, "branch", "--show-current").strip() == "main":
            raise Deny("You are on main. Never push to main; switch to a task branch.")

    # Commits (options are read from the commit command itself, not from chained commands).
    commit = re.search(git_cmd + r"commit\b", c)
    if commit:
        opts = re.split(r"\s(?:&&|\|\||;|\|)\s", c[commit.end():])[0]
        opts = re.sub(r"\"(?:[^\"\\]|\\.)*\"|'[^']*'", "''", opts)  # ignore option-like text inside the message
        if re.search(r"--author\b", opts):
            raise Deny("Never use --author: commits keep the developer's identity.")
        if re.search(r"--no-verify\b|(^|\s)-n\b", opts):
            raise Deny("Don't skip commit checks with --no-verify.")
        if git(root, "branch", "--show-current").strip() == "main":
            raise Deny("Don't commit on main. Create a task branch: <type>/GM-<n>-<slug>.")
        if re.search(r"--amend\b", opts):
            raise Ask("Amending rewrites an existing commit. Only do this if the developer asked for it.")
        subject = commit_subject(cmd, root)
        if subject is not None and not SUBJECT.match(subject):
            raise Deny(f"Commit message must be '<type>: <message>' with type in {TYPES.replace('|', ', ')} "
                       f"(optional '!' for breaking). Got: {subject!r}")
        staged = git(root, "diff", "--cached", "-U0") + (git(root, "diff", "-U0") if re.search(r"\s(-a|--all)\b", c) else "")
        added = "\n".join(l for l in staged.split("\n") if l.startswith("+") and not l.startswith("+++"))
        for pattern, label in SECRETS:
            if pattern.search(added):
                raise Deny(f"Staged changes contain what looks like a {label}. Remove it before committing.")

    # Destructive commands: ask first.
    destructive = [
        (git_cmd + r"reset\s+--hard\b", "git reset --hard discards work"),
        (git_cmd + r"clean\s+-\w*f", "git clean deletes untracked files"),
        (git_cmd + r"branch\s+-D\b", "git branch -D deletes a branch"),
        (git_cmd + r"checkout\s+(--\s|\.(\s|$))", "git checkout -- discards changes"),
        (git_cmd + r"restore\b(?!.*--staged\b)", "git restore discards changes"),
        (git_cmd + r"stash\s+(drop|clear)\b", "dropping a stash loses work"),
        (git_cmd + r"rebase\b", "rebase rewrites history"),
        (r"\bdocker(-compose|\s+compose)\b.*\bdown\b.*(-v\b|--volumes\b)", "this deletes database volumes"),
        (r"\bdocker\s+volume\s+(rm|prune)\b", "this deletes Docker volumes"),
        (r"(?i)\b(psql|pg_\w+|mysql|sqlite3|flyway)\b.*\b(drop\s+(table|schema|database)|truncate\s+table)\b",
         "this destroys data"),
    ]
    for pattern, why in destructive:
        if re.search(pattern, c):
            raise Ask(f"Destructive command: {why}. Run it only with the developer's explicit approval.")
    rm = re.search(r"\brm\s+(-\w*r\w*|--recursive)\b(.*)", c)
    if rm:
        targets = [t for t in rm.group(2).split("&&")[0].split() if not t.startswith("-")]
        if not targets or not all(rel(t.strip("'\""), root).startswith("scratch/") for t in targets):
            raise Ask("Recursive delete outside scratch/. Run it only with the developer's explicit approval.")


# ---------------------------------------------------------------- files

def check_path(tool, inp, root, tests_only=False):
    path = inp.get("file_path") or inp.get("notebook_path") or inp.get("path") or ""
    if not path:
        return
    r = rel(path, root)
    name = PurePosixPath(r).name
    if (name == ".env" or name.startswith(".env.")) and name != ".env.example":
        raise Deny(f"{r} may contain secrets. Claude must not read or edit .env files; use .env.example.")
    if tool == "Read":
        return
    if tests_only:
        if not TEST_PATHS.match(r):
            raise Deny(f"The test-engineer agent may edit test files only; {r} is production code or config.")
        return
    if r == "CHANGELOG.md":
        raise Deny("CHANGELOG.md is generated by semantic-release. Never edit it by hand.")
    if r.startswith("backend/src/main/resources/db/migration/") and git(root, "ls-files", "--", r).strip():
        raise Deny(f"{r} is a committed Flyway migration. Never edit it; add a new migration instead.")
    if r in ASK_EDIT:
        raise Ask(f"This edits {ASK_EDIT[r]}. Confirm the developer approved this change.")
    if r.startswith(".github/workflows/"):
        raise Ask("This edits a CI/release workflow, which changes how every PR is checked or released. Confirm it was approved.")
    if r == "ai-service/gajamitra/track.py":
        text = "\n".join(str(inp.get(k, "")) for k in ("old_string", "new_string", "content"))
        if tool == "Write" or CONSTANTS.search(text):
            raise Ask("This changes track.py risk calibration (HIGH_ETA_S, MIN_CLOSING_PX_S, VELOCITY_WINDOW_S, "
                      "NEAR_BODIES) or rewrites the file. Needs the developer's approval and a test.")


def post_context(inp, root):
    r = rel(inp.get("file_path") or "", root)
    if r in SAFETY_CRITICAL or r.startswith(SAFETY_CRITICAL_DIRS):
        return (f"{r} is safety-critical (see CLAUDE.md 'Safety invariants'). Before finishing: run the tests that "
                "protect the invariants it touches (/test-and-verify) and /safety-audit on the change.")
    return None


# ---------------------------------------------------------------- main

def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "pre"
    try:
        data = json.loads(sys.stdin.read() or "{}")
    except json.JSONDecodeError as e:
        print(f"guard.py: could not parse hook input ({e}); not checked.", file=sys.stderr)
        return 1
    tool, inp, root = data.get("tool_name", ""), data.get("tool_input") or {}, project_dir(data)
    try:
        if mode == "post":
            ctx = post_context(inp, root)
            if ctx:
                print(json.dumps({"hookSpecificOutput": {"hookEventName": "PostToolUse", "additionalContext": ctx}}))
        elif tool == "Bash":
            check_bash(inp.get("command", ""), root)
        elif tool in ("Read", "Edit", "Write", "MultiEdit", "NotebookEdit"):
            check_path(tool, inp, root, tests_only=(mode == "tests-only"))
    except Deny as e:
        print(f"Blocked by GajaMitra guard: {e}", file=sys.stderr)
        return 2
    except Ask as e:
        print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "ask",
                                                 "permissionDecisionReason": f"GajaMitra guard: {e}"}}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
