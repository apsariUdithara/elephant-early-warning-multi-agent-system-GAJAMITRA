---
name: prepare-pr
description: 'Prepare and open a GajaMitra pull request - verify branch, commits, tests and CI, write the title "<type>: <message> - GM-<n>" with the developer-supplied task number, write the full description (summary, motivation, implementation, files, tests and results, CI, safety, security, migration, compatibility, screenshots, limitations), push the task branch and open the PR with gh. Never merges or approves.'
argument-hint: "[GM-<n> task number]"
disable-model-invocation: true
---

# Prepare PR

## Inputs
- Task number `GM-<n>` from `$ARGUMENTS` or the user. **If it isn't given, ask. Never invent one.**
- `git log main..HEAD --format='%h %s'`, `git diff --stat main...HEAD`, results of `/test-and-verify`.

## Steps
1. **Preconditions:** on a task branch (not `main`); working tree has no uncommitted changes that belong to this task; every commit subject matches `<type>: <message>` (fix with new commits or ask; never rewrite history without permission); `/test-and-verify` has run for this diff.
2. **Reviews:** if safety-relevant files changed, `/safety-audit` must have run; include its verdict. Use other review skills only for areas that changed.
3. **Title:** `<type>: <message> - GM-<n>`. Type = the most significant change type in the PR (feat > fix > perf > revert > others); add `!` if any commit is breaking. ≤ 72 characters before ` - GM-<n>`. This title becomes the squash-merge commit that semantic-release reads.
4. **Description** (fill every heading; write "None" rather than deleting one):
   ```
   ## Task
   GM-<n>
   ## Summary
   ## Motivation and context
   ## Implementation
   ## Files and services affected
   ## Tests
   | Suite | Command | Result |      (only suites actually run; list the ones not run and why)
   ## CI
   ## Safety impact        (invariants touched, /safety-audit verdict)
   ## Security impact
   ## Database / migration impact
   ## API / messaging compatibility
   ## Screenshots          (UI changes)
   ## Known limitations
   ## Outstanding issues
   ```
   No AI attribution line.
5. **Push the task branch:** `git push -u origin <branch>` (never `main`, never `--force` without explicit permission).
6. **Open:** `gh pr create --base main --head <branch> --title "<title>" --body-file <file in scratch/>`.
7. **CI:** `gh pr checks <number> --watch` (or report current status if it is still running).
8. **Stop.** Do not merge, approve, enable auto-merge or request merge.

## Report (then stop)
```
PR: <url>
Title: <title>
Branches: <head> → main
Commits: <sha subject, one per line>
Summary: <2-3 lines>
Tests: <table>   CI: <status>
Safety/security findings: <summary>
Known limitations: <list>
Reviewer attention: <what the human should look at closely>
The PR is open and waiting for your review. I won't merge it.
```
