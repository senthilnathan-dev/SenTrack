# AGENTS.md

Instructions for AI coding agents (and a fast orientation for humans) working in this repository. Human contributors should
also read [CONTRIBUTING.md](CONTRIBUTING.md); the rules there apply to you too. When this file and a doc disagree, the code
wins, then fix the doc.

## What this project is
SenTrack: a private, offline-first Android app (Kotlin, Jetpack Compose, Material 3, Room on SQLCipher) with four areas
(Finance, Personal, Work, Career), reminders that ring while locked, an encrypted Google Drive backup, and an optional AI
assistant (on-device model or the user's own cloud key). Single user, single phone, Android 16 (API 36) only. Version and status:
[README.md](README.md), [docs/00-project-decisions.md](docs/00-project-decisions.md).

## Read first
| Need | File |
|---|---|
| Doc index | [docs/README.md](docs/README.md) |
| Modules and rules | [docs/01-architecture.md](docs/01-architecture.md) |
| Security and privacy constraints | [docs/02-security-model.md](docs/02-security-model.md) |
| Database, migrations | [docs/15-data-model.md](docs/15-data-model.md) |
| UI kit and layout rules | [docs/04-ui-guidelines.md](docs/04-ui-guidelines.md) |
| AI design | [docs/11-ai.md](docs/11-ai.md) |
| Release steps | [docs/13-release-checklist.md](docs/13-release-checklist.md) |
| Git workflow | [docs/17-git-workflow.md](docs/17-git-workflow.md) |
| Machine setup | [docs/16-development-setup.md](docs/16-development-setup.md) |

## Repository map
```
app/               MainActivity, AppContainer (manual DI), vault + settings screens, backup UI/worker, shell, About, AI screens
core-security/     vault, crypto, file vault          core-data/   Room database, entities, DAOs, repositories, schemas/
core-sync/         snapshot backup, Drive, schedules  core-alerts/ reminders engine
core-attachments/  encrypted file import/export/view  core-ui/     UI kit + AiAssist interface/button
core-ai/           providers, on-device LLM, model catalog/downloader, answer styles, prompts, number guard
feature-finance/ feature-personal/ feature-work/ feature-career/ feature-reminders/    screens and pure logic per area
docs/              developer docs;  docs/legal/ = user-facing source text;  docs/site/ = generated web pages (do not hand-edit)
tools/             make-legal-pages.sh, bump-version.sh, build-release.sh, create-upload-key.sh
```

## Commands
```bash
./gradlew test                                   # unit tests (JVM)
./gradlew :app:assembleDebug                     # debug APK
./gradlew lintDebug                              # lint
./gradlew :core-data:connectedDebugAndroidTest   # migration test (needs a phone/emulator)
bash tools/make-legal-pages.sh                   # regenerate docs/site + CHANGELOG.md after editing docs/legal/*.txt
bash tools/bump-version.sh X.Y.Z                 # version bump (name, code, changelog stub)
bash tools/check-no-personal-info.sh             # fails on personal emails, serials, local paths in tracked files and commits
```
If your environment cannot run Gradle (some sandboxes fail with "Unable to establish loopback connection"), do **not** claim the
change builds or passes tests. Say it is unverified, and ask the human to build in Android Studio and paste errors.

## Rules you must follow
1. **Privacy first.** Never add logging of user data. No network access, new permission, new stored data type or third-party
   service without updating `docs/legal/privacy.txt` (a unit test fails otherwise) and `docs/02-security-model.md`.
2. **Never commit secrets**: keystores, `keystore.properties`, `local.properties`, API keys, real user data. Do not print them.
3. **Outward-facing actions need explicit human approval**: `git push`, creating tags or releases, publishing pages, sending
   anything to GitHub or Play, deleting branches. Local commits on a feature branch are fine when asked. Never force-push, rewrite
   published history, or skip hooks.
4. **Small, focused changes** on a branch (`feat/…`, `fix/…`, `docs/…`, `chore/…`), Conventional Commit messages, with the
   `Co-Authored-By` trailer for your assistant. Do not commit directly to `main`.
5. **Database changes**: bump the version, add the migration, commit the generated `core-data/schemas/.../N.json`, extend
   `MigrationTest`. Never edit or delete a released schema file or reuse a version number.
6. **Docs are part of the change.** Update the matching `docs/*.md`; for user-visible changes add a `docs/legal/changelog.txt` entry
   (newest first), run `bash tools/make-legal-pages.sh`, and commit the generated files.
7. **Architecture**: feature modules depend on `core-*`, never on each other; screens use repositories not Room; no AI code in
   feature modules (use `LocalAiAssist` and `AiAssistButton`); build UI from `core-ui`.
8. **Never write personal contact details** (email, phone, home paths, device serials) into code, docs, commits or issues. Use no-reply addresses; run `bash tools/check-no-personal-info.sh`.
9. **Honest reporting.** Say what you ran and what you did not. A failing test is reported with its output, not hidden.

## Conventions
- Money: `Long` paise. Dates: epoch days. Timestamps: epoch ms. Ids: UUID strings.
- Archive before delete; permanent delete needs the typed word DELETE (company name for a company) and also erases attachments
  and reminders.
- Plain, friendly language for users: no jargon, say what happened and what to do next. Icons for common actions.
- Comments explain *why*; public API has KDoc. Keep logic that can be tested out of composables and test it.
- Kotlin official style; trailing commas are fine; lines up to about 140 characters.

## Gotchas learned the hard way
- A property from **another module** cannot be smart-cast (`'x' is a public API property declared in different module`): copy it
  to a local `val` first.
- A new subtype of a sealed interface (for example `ScheduleSpec`) breaks every exhaustive `when` over it. Search all modules for
  `is ScheduleSpec.` before adding one.
- Trailing-lambda parameters must be last. Do not leave duplicate `@Composable` annotations or duplicate imports after edits.
- When editing with `sed`, `\n` inside a replacement becomes a real newline and breaks Kotlin string literals; use a proper editor
  or edit tool for anything containing escapes. Do not run blind regex replacements across the repo.
- Room: if the build says `Schema 'N.json' required`, a schema file is missing or a version was skipped without an auto-migration
  from the last committed schema.
- Debug builds allow screenshots; release builds set `FLAG_SECURE`. Test release behaviour on a release build.
- AGP 9 uses built-in Kotlin and a new DSL; prefer APIs the existing build files already use.
- Do not use `QUERY_ALL_PACKAGES`; the Workspace app picker works through a `<queries>` entry.
- The on-device model catalog entries (`LocalModels`) pin a URL, size and SHA-256; update all three together and re-test.

## Definition of done
- [ ] Builds and `./gradlew test` pass (or you clearly state they could not be run)
- [ ] New logic has unit tests; migrations have a migration test
- [ ] Docs, user guide and changelog updated; `tools/make-legal-pages.sh` run
- [ ] Privacy policy updated if data, permissions or network use changed
- [ ] No secrets or personal data; no new logging of user data
- [ ] Conventional commit on a feature branch; PR description filled using the template
