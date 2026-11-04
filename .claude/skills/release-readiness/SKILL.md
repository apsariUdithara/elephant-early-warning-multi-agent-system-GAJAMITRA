---
name: release-readiness
description: 'Read-only check of GajaMitra''s semantic-release setup and what the next release would be - commits since the last tag, their types, the predicted version bump, CI status on main, .releaserc.json and release workflow sanity, and CHANGELOG.md generation. Never creates tags, bumps versions or edits the changelog.'
argument-hint: "[branch; default: main]"
---

# Release readiness

**Purpose:** answer "what will the next release be, and will the automation produce it correctly?" without touching anything.

## Steps
1. **Last release:** `git fetch --tags` then `git describe --tags --abbrev=0 origin/main` (none → first release will be `1.0.0`).
2. **Commits since then:** `git log <tag>..origin/main --format='%h %s'`. Classify each subject with the table in `CLAUDE.md`:
   - breaking (`type!:` or a `BREAKING CHANGE:` footer, check `%b`) → major
   - `feat` → minor
   - `fix`, `perf`, `revert` → patch
   - other approved types → no release
   - anything not matching `<type>: <message>` → flag it (it is ignored by the release and should have been caught by pr-checks)
3. **Predicted version:** the highest bump wins. Optionally confirm with a dry run: `npx semantic-release --dry-run --no-ci --branches <current-branch>` (needs `npm ci` at the repo root; it prints the next version and notes without tagging or pushing).
4. **CI on main:** `gh run list --branch main --limit 5` (CI, pr-checks, release). A failing CI on the release commit blocks the release workflow.
5. **Config sanity:** `.releaserc.json` release rules match the table in `CLAUDE.md`; `release.yml` triggers on `main` only, has `concurrency`, uses the GitHub App token, and is the only release workflow; `CHANGELOG.md` is listed in the git plugin assets.
6. **Changelog range:** the notes cover exactly `<tag>..HEAD`.

## Constraints
- Never run `semantic-release` without `--dry-run`, never `git tag`, never edit `CHANGELOG.md` or version fields.
- Don't claim a release happened unless the release workflow run and the tag both exist.

## Report
```
Release readiness (main @ <sha>)
Last release: <tag or none>
Commits since: <n>  (feat x, fix y, perf z, revert w, breaking b, no-release n, non-conforming k)
Predicted next version: <version> (<reason>)  | dry run: <ran: output | not run: reason>
CI on main: <status>
Config issues: <list or none>
```
