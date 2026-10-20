## What and why
<!-- One or two sentences: what changes and why. Link the issue: "Closes #123". -->

## How it was tested
<!-- Unit tests added or run, and what you tried on a phone. Mention the phone and Android version for UI or alarm changes. -->

## Checklist
- [ ] The PR title follows Conventional Commits (`type(scope): summary`); the PR is squash-merged
- [ ] `./gradlew test` passes; new logic has unit tests
- [ ] `bash tools/make-legal-pages.sh` leaves no diff
- [ ] User-visible change: entry added to `docs/legal/changelog.txt` (and the user guide if needed)
- [ ] Changes what is stored, collected or sent, or adds a permission or network service: `docs/legal/privacy.txt` updated
- [ ] Database change: version bumped, migration added, new `N.json` committed, migration test extended
- [ ] Matching document in `docs/` updated
- [ ] No secrets, keystores, API keys or personal data in the diff
- [ ] Screens use the shared UI kit; no AI code in feature modules (use `LocalAiAssist`)

## Screenshots (debug build, made-up data)
<!-- Before and after, for UI changes. -->

## Risk and notes for reviewers
<!-- Anything risky, anything you were unsure about, follow-ups you left out on purpose. -->
