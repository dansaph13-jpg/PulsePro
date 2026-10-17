# TS Gold Pulse Pro — Agent Rules

Read `docs/PULSEPRO_CONTEXT.md` before changing this project.

## Mandatory rules
- Work only in this repository for Pulse Pro tasks.
- Preserve the existing Treasury price source and Pusher connection
  unless the user explicitly requests a change to them.
- UI-only requests must not modify market-data ingestion, Pusher
  subscriptions, notification IDs, or price calculations unnecessarily.
- Keep the UI compact, simple, premium, and consistent with the user's
  latest approved design.
- Use sequential version numbers for every APK release.
- Inspect the relevant code and current diff; avoid broad unrelated rewrites.
- Never overwrite backups or include backup files in a commit.
- Never stage the entire repository with `git add .` or `git add -A`.
- Run `git diff --check` and build before committing.
- Keep the existing signing certificate so upgrades can install over
  the prior app. Verify the APK signature before release.
- Do not change targetSdk or permissions casually; explain compatibility
  consequences first.
- Do not claim features are tested merely because the build succeeded.
- Commit only files relevant to the requested change.
