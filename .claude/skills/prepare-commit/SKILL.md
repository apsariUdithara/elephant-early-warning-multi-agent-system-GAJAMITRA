---
name: prepare-commit
description: 'Prepare a GajaMitra commit - inspect staged and unstaged changes, keep unrelated and sensitive files out, choose the approved change type, write a "<type>: <message>" message with no scope and no AI attribution, then commit with the developer''s own Git identity. Use when work on a task branch is ready to commit.'
argument-hint: "[optional: intended message or type]"
disable-model-invocation: true
---

# Prepare commit

## Inputs
- `git status --short`, `git diff`, `git diff --cached`, current branch (`git branch --show-current`).
- The task scope (what the user asked for).

## Steps
1. **Branch check:** never commit on `main`. If on `main`, stop and create/ask for the task branch (`<type>/GM-<n>-<slug>`).
2. **Review every change** (staged and unstaged). Sort files into: in scope / unrelated / generated / sensitive.
   - Unrelated changes (including the developer's own uncommitted work) are **left alone**: don't stage, revert or stash them.
   - Never stage: `.env*` except `.env.example`, keys, `data/`, `scratch/`, build output (`target/`, `dist/`, `node_modules/`), footage, `HANDOVER.md`.
3. **Secret scan** of what will be committed: `git diff --cached` for Meta keys (`LLM|`), Twilio SIDs/tokens (`AC` + 32 hex), private keys, passwords, real phone numbers (`+94…`). The guard hook also blocks these; finding one is a stop, not a warning.
4. **Stage explicitly** by path (`git add <paths>`), not `git add -A`.
5. **Choose the type** from the table in `CLAUDE.md` by what the change *does* for users:
   - new behaviour → `feat`; wrong behaviour corrected → `fix`; faster, same behaviour → `perf`
   - restructure, same behaviour → `refactor`; docs only → `docs`; tests only → `test`
   - deps/build → `build`; CI → `ci`; formatting → `style`; housekeeping → `chore`; undo → `revert`
   - breaking a contract or behaviour users rely on → add `!` (`feat!: …`) and explain in a `BREAKING CHANGE:` footer
   - mixed change? split into separate commits rather than picking the "biggest" type.
6. **Write the message:** `<type>: <imperative summary, lower case, no trailing period, ≤ 72 chars>`. Optional body: why, not what. **No scope, no `Co-authored-by`, no "Generated with" line.**
7. **Commit:** `git commit -m "<message>"` (add `-m "<body>"` if needed). Never `--author`, never `--no-verify`, never `git config user.*`.
8. **Confirm:** `git log -1 --format='%an <%ae>%n%B'` shows the developer's identity and the exact message.

## Report
```
Committed <sha> on <branch>: <message>
Files: <list>   Left unstaged (not part of this task): <list or none>
Secret scan: clean | blocked (<what>)
```
