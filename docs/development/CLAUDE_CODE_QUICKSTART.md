# Claude Code quick start for GajaMitra

Nine everyday workflows. Each shows what to type in Claude Code and what you get back. Details and limits are in the [toolkit guide](CLAUDE_CODE_TOOLKIT.md).

**Before the first session:** install the GitHub CLI and log in (`winget install GitHub.cli`, then `gh auth login`), and have Python 3.9+ on `PATH` for the guard hook.

## 1. Understand the project
```
Read CLAUDE.md and docs/ARCHITECTURE.md, then explain how an analysis flows from upload to SMS and which parts exist today.
```
Claude answers from the handbook, the architecture doc and the code. Ask follow-ups about specific files; the matching path rules load automatically.

## 2. Plan a feature
```
/plan-feature add the policy guard (policy.py) that reads policy/policy_table.json GM-7
```
You get a plan: files, contracts, invariants touched and how they stay true, failure handling, tests, and decisions for you. Nothing is written until you approve.

## 3. Implement a feature
```
/implement-feature GM-7 implement the approved plan
```
Claude creates `feat/GM-7-…` from `origin/main`, implements only the agreed scope, runs tests and the relevant reviews, commits (`feat: …`), pushes the branch, opens the PR and stops. It asks before anything that changes an invariant, a calibration constant, the policy table or a contract version, or adds a dependency.

## 4. Debug an issue
```
/debug-service analysis stays QUEUED forever after upload
```
Claude reproduces the problem, finds the first component that goes wrong, fixes the root cause in the shared code path, and adds a test that failed before the fix.

## 5. Review changes
```
/review-pr           (your local branch vs main)
/review-pr 12        (a pull request)
/safety-audit        (only the 12 safety invariants, in depth)
```
You get a prioritised list (blockers first) with file and line, plus a recommendation to you. Claude never approves or merges.

## 6. Run tests
```
/test-and-verify
/test-and-verify all
```
A table of suites, commands and results, including what didn't run and why. Claude never reports a result it didn't see.

## 7. Prepare a commit
```
/prepare-commit
```
Claude reviews staged and unstaged changes, leaves unrelated work alone, scans for secrets, picks the type and commits as you, with no attribution line. A bad message or a commit on `main` is blocked by the guard.

## 8. Open a pull request
```
/prepare-pr GM-7
```
Title `feat: add policy guard - GM-7`, the full description (tests, safety, security, migration, compatibility, limitations), push, `gh pr create`, CI status. Then it stops: **you review and squash-merge.**

## 9. Prepare for a release
```
/release-readiness
/pre-release-audit
```
The first predicts the next version from commits since the last tag and checks the release setup. The second also runs the specialist reviews and all tests across that range. Releases themselves happen only in CI when you merge to `main`.
