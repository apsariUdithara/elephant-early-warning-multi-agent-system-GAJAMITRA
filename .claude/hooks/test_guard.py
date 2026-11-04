"""Self-test for guard.py. Runs the hook as Claude Code would (JSON on stdin) inside a throwaway git repo.

Run: python .claude/hooks/test_guard.py   (also collected by pytest)
"""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

GUARD = Path(__file__).with_name("guard.py")


def run(payload, root, mode="pre"):
    env = {**os.environ, "CLAUDE_PROJECT_DIR": str(root)}
    p = subprocess.run([sys.executable, str(GUARD), mode], input=json.dumps(payload), capture_output=True,
                       text=True, encoding="utf-8", env=env, timeout=30)
    if p.returncode == 2:
        return "deny"
    out = json.loads(p.stdout) if p.stdout.strip() else {}
    hso = out.get("hookSpecificOutput", {})
    if hso.get("permissionDecision") == "ask":
        return "ask"
    if hso.get("additionalContext"):
        return "context"
    assert p.returncode == 0, p.stderr
    return "allow"


def bash(cmd):
    return {"tool_name": "Bash", "tool_input": {"command": cmd}}


def edit(path, **kw):
    return {"tool_name": kw.pop("tool", "Edit"), "tool_input": {"file_path": path, **kw}}


def sh(root, *args):
    subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@example.com", *args], cwd=root, check=True,
                   capture_output=True)


def make_repo(tmp):
    root = Path(tmp)
    sh(root, "init", "-q", "-b", "main")
    mig = root / "backend/src/main/resources/db/migration"
    mig.mkdir(parents=True)
    (mig / "V1__init.sql").write_text("create table site (id uuid primary key);\n")
    sh(root, "add", ".")
    sh(root, "commit", "-q", "-m", "chore: init")
    return root


def test_guard():
    with tempfile.TemporaryDirectory() as tmp:
        root = make_repo(tmp)
        heredoc_ok = "git commit -m \"$(cat <<'EOF'\nfix: keep warning when SAM output is empty\n\nbody\nEOF\n)\""
        heredoc_bad = "git commit -m \"$(cat <<'EOF'\nKeep warning\nEOF\n)\""

        # On main: committing or pushing the current branch is blocked.
        assert run(bash('git commit -m "feat: add x"'), root) == "deny"
        assert run(bash("git push"), root) == "deny"
        assert run(bash("git push origin HEAD"), root) == "deny"

        sh(root, "switch", "-q", "-c", "feat/GM-1-x")
        cases = {
            # Human-only actions
            "gh pr merge 3 --squash": "deny",
            "gh pr review 3 --approve": "deny",
            "gh api -X PUT repos/o/r/branches/main/protection": "deny",
            "gh release create v1.0.0": "deny",
            # Pushes
            "git push origin main": "deny",
            "git push origin HEAD:main": "deny",
            "git push --force origin feat/GM-1-x": "deny",
            "git push origin +feat/GM-1-x": "deny",
            "git push --tags": "deny",
            "git push -u origin feat/GM-1-x": "allow",
            "git push": "allow",
            # Tags and identity
            "git tag v1.0.0": "deny",
            "git tag -l": "allow",
            'git config user.name "Someone"': "deny",
            "git config --global user.email a@b.c": "deny",
            "git config user.name": "allow",
            # Commit messages and attribution
            'git commit -m "Add stuff"': "deny",
            'git commit -m "feat(api): scoped"': "deny",
            'git commit -m "feature: wrong type"': "deny",
            'git commit -m "feat: add risk dashboard"': "allow",
            'git commit -m "feat!: drop legacy field"': "allow",
            'git commit -m "docs: x" && git log -n 3': "allow",
            heredoc_ok: "allow",
            heredoc_bad: "deny",
            'git commit -m "feat: x" -m "Co-authored-by: Claude <noreply@anthropic.com>"': "deny",
            'git commit --author="Bot <b@b>" -m "fix: x"': "deny",
            'git commit -n -m "fix: x"': "deny",
            'git commit --amend --no-edit': "ask",
            # PRs
            'gh pr create --title "feat: add x" --body "b"': "deny",
            'gh pr create --title "chore: add toolkit - GM-1" --body "b"': "allow",
            'gh pr create --title "feat: add x - GM-1" --body "Generated with [Claude Code](https://claude.com)"': "deny",
            # Destructive commands
            "git reset --hard HEAD~1": "ask",
            "git rebase main": "ask",
            "git checkout -- .": "ask",
            "docker compose down -v": "ask",
            'psql -c "DROP TABLE alert"': "ask",
            "rm -rf backend/target": "ask",
            "rm -rf scratch/tmp": "allow",
            # Harmless commands that mention trigger words
            'grep -ri "co-authored-by" .': "allow",
            "git log --oneline -5 && head -n 3 README.md": "allow",
            "git status --short": "allow",
        }
        for cmd, want in cases.items():
            got = run(bash(cmd), root)
            assert got == want, f"{cmd!r}: expected {want}, got {got}"

        # Staged secrets block a valid commit message. Built at runtime so this file holds no secret literal.
        (root / "config.txt").write_text("key = " + "LLM" + "|123456|" + "abcdefghijklmnop\n")
        sh(root, "add", "config.txt")
        assert run(bash('git commit -m "chore: add config"'), root) == "deny"
        sh(root, "rm", "-q", "--cached", "config.txt")

        # PR body file with attribution
        (root / "body.md").write_text("Summary\n\n" + "Co-authored" + "-by: Claude\n")
        assert run(bash('gh pr create --title "fix: y - GM-2" --body-file body.md'), root) == "deny"

        # Files
        files = {
            (".env", "Read"): "deny",
            (".env.example", "Read"): "allow",
            (".env.local", "Edit"): "deny",
            ("CHANGELOG.md", "Edit"): "deny",
            ("backend/src/main/resources/db/migration/V1__init.sql", "Edit"): "deny",
            ("backend/src/main/resources/db/migration/V2__alerts.sql", "Write"): "allow",
            ("policy/policy_table.json", "Edit"): "ask",
            (".github/workflows/ci.yml", "Edit"): "ask",
            (".claude/settings.json", "Edit"): "ask",
            ("ai-service/gajamitra/track.py", "Write"): "ask",
            ("README.md", "Edit"): "allow",
        }
        for (path, tool), want in files.items():
            got = run(edit(str(root / path), tool=tool), root)
            assert got == want, f"{tool} {path}: expected {want}, got {got}"
        assert run(edit(str(root / "ai-service/gajamitra/track.py"), old_string="NEAR_BODIES = 3.0",
                        new_string="NEAR_BODIES = 2.0"), root) == "ask"
        assert run(edit(str(root / "ai-service/gajamitra/track.py"), old_string='"""Turn SAM', new_string='"""Turn SAM 3.1'),
                   root) == "allow"

        # test-engineer agent: test files only
        assert run(edit(str(root / "ai-service/gajamitra/track.py")), root, "tests-only") == "deny"
        assert run(edit(str(root / "ai-service/tests/test_policy.py")), root, "tests-only") == "allow"
        assert run(edit(str(root / "backend/src/test/java/com/gajamitra/AlertTest.java")), root, "tests-only") == "allow"

        # PostToolUse reminder
        assert run(edit(str(root / "policy/policy_table.json")), root, "post") == "context"
        assert run(edit(str(root / "README.md")), root, "post") == "allow"

        # Malformed input never blocks
        p = subprocess.run([sys.executable, str(GUARD), "pre"], input="not json", capture_output=True, text=True)
        assert p.returncode == 1


if __name__ == "__main__":
    test_guard()
    print("ok")
