---
name: implement-feature
description: 'Implement an approved GajaMitra task end to end - create the task branch, implement only the agreed scope, verify with tests, run the relevant reviews, commit with approved message types, push the branch, open the PR and stop for human review. Use after /plan-feature has been approved, or for small tasks the user has clearly scoped.'
argument-hint: "GM-<n> <task or reference to the approved plan>"
disable-model-invocation: true
---

# Implement feature

## Inputs
- Task number `GM-<n>` (ask if missing; never invent), and the approved plan or a clearly scoped request.

## Steps
1. **Start safely:** `git status --short` and `git branch --show-current`. Note the developer's uncommitted changes and leave them untouched. `git fetch origin`, then `git switch -c <type>/GM-<n>-<slug> origin/main` (or switch to the existing task branch).
2. **Implement only the agreed scope.** Follow the root `CLAUDE.md` and the path rules that load for each file. Reuse what exists; no new dependency without approval; no unrelated refactors. Safety-relevant files: write or update the invariant test first.
3. **Verify:** `/test-and-verify`. Fix failures that are inside the scope; report the rest.
4. **Review:** read your full diff (`git diff main...HEAD`). Run only the relevant reviews: `/safety-audit` for safety-relevant files, plus contract/DB/messaging/UI/localisation skills for those areas. Fix in-scope findings; list the others.
5. **Docs:** update the plan, README or ARCHITECTURE if behaviour, setup or architecture changed.
6. **Commit:** `/prepare-commit` (one or more commits, approved types, no attribution).
7. **PR:** `/prepare-pr GM-<n>`: pushes the task branch, opens the PR against `main`, waits for CI.
8. **Stop.** Never merge or approve. Hand over with the report.

## Stop and ask instead of continuing when
- the change would weaken an invariant, change a calibration constant or the policy table, or change escalation/approval behaviour;
- a contract change needs a `schema_version` bump;
- a new dependency, service or infrastructure piece seems necessary;
- the plan and the code disagree in a way that changes the design.

## Report
Use the `/prepare-pr` report, plus: invariants touched and their tests, review findings not fixed, and decisions the human still needs to make.
