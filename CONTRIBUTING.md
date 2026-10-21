# Contributing to SenTrack

Thank you for helping. SenTrack is a private, offline-first Android tracker (finance, personal, work, career), so the rules
below put **privacy, correctness and small reviewable changes** first. They apply equally to people and to AI coding agents
(agents should also read [AGENTS.md](AGENTS.md)).

By contributing you agree to the terms in [LICENSE](LICENSE) (all rights reserved; contributions are licensed to the owner).
Everyone taking part follows the [Code of Conduct](CODE_OF_CONDUCT.md). Security problems go through [SECURITY.md](SECURITY.md),
never a public issue.

## Quick start
1. Set up your machine: [docs/16-development-setup.md](docs/16-development-setup.md).
2. Find or open an issue and say you are working on it (so two people do not do the same thing).
3. Branch from `main`, make a focused change, open a pull request. The full flow is in
   [docs/17-git-workflow.md](docs/17-git-workflow.md).

## Branches
`main` is always buildable and releasable. Work on short-lived branches named `<type>/<short-description>`:

| Prefix | For |
|---|---|
| `feat/` | a user-visible feature |
| `fix/` | a bug fix |
| `docs/` | documentation only |
| `chore/` | build, tooling, dependencies, housekeeping |
| `refactor/` | behaviour-preserving restructuring |
| `test/` | tests only |

Examples: `feat/leave-carry-forward`, `fix/reminder-last-day`, `docs/data-model`. Do not commit directly to `main`.

## Commits
[Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary` in the imperative, under about 72
characters, with a body that explains **why** when it is not obvious.

- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`, `build`, `ci`, `revert`.
- Scopes (use the closest): `finance`, `personal`, `work`, `career`, `reminders`, `backup`, `security`, `ai`, `ui`, `data`,
  `about`, `release`, `deps`.
- AI-assisted commits end with a `Co-Authored-By:` trailer naming the assistant.
- Breaking or data-affecting changes say so in the body.

Examples:
```
feat(finance): repeating entries support the last day of the month
fix(reminders): monthly reminders from the 29th use the last day of short months
docs: document the migration chain
```

## Pull requests
Open a PR against `main` using the template. Keep it to one concern and, ideally, under about 400 changed lines (generated
files and schemas excepted). A PR is ready when:

- [ ] `./gradlew test` passes and new logic has unit tests (pure logic is kept out of composables so it can be tested).
- [ ] `bash tools/make-legal-pages.sh` leaves no diff (CI checks).
- [ ] If the change is visible to users: an entry in `docs/legal/changelog.txt` (and `docs/legal/manual.txt` if it needs explaining).
- [ ] If it changes what data is stored, collected or sent, or adds a permission or network service: `docs/legal/privacy.txt`
      is updated (a unit test fails otherwise) and the security model doc if relevant.
- [ ] If it changes the database: version bumped, migration added, the new `N.json` schema committed, migration test extended
      (see [docs/15-data-model.md](docs/15-data-model.md)).
- [ ] The matching document in `docs/` is updated in the same PR.
- [ ] No secrets, keystores, API keys, personal data or `local.properties` in the diff.

PRs are squash-merged, so the PR title becomes the commit message and must follow the commit format above.

## Code conventions
- Kotlin official style (`.editorconfig` is provided). Comments explain *why*, not *what*; public classes and functions have KDoc.
- Architecture: feature modules depend on `core-*` modules, never on each other; screens use repositories, never Room directly;
  features contain no AI code and use `LocalAiAssist`. See [docs/01-architecture.md](docs/01-architecture.md).
- UI: build screens from the shared kit in `core-ui` ([docs/04-ui-guidelines.md](docs/04-ui-guidelines.md)). Plain language for
  users (no jargon), icons for common actions, archive before delete, typed `DELETE` for permanent deletion.
- Money is `Long` paise, dates are epoch days, ids are UUID strings.
- Cross-module properties cannot be smart-cast: copy to a local `val` first.
- Add new dependencies through `gradle/libs.versions.toml`, with a reason in the PR. Prefer no new dependency.

## Privacy and security rules (non-negotiable)
- Never log user data. There are no logging calls in the app today; keep it that way.
- Nothing leaves the device unless the user switched on a feature that says so, and the privacy policy names it.
- Never put secrets or personal data in URLs, commit messages, issues or test fixtures.
- Do not weaken locking, encryption, `FLAG_SECURE`, or the generic lock-screen reminder text without a documented decision (ADR).

## Tests
- `./gradlew test`: unit tests (run on your computer).
- `./gradlew :core-data:connectedDebugAndroidTest`: database migration test (needs a connected phone or emulator).
- Manual: install the **release** build on a phone before a release and click through every tab.

## Issues and labels
Use the issue forms (bug report, feature request). Labels: `bug`, `enhancement`, `docs`, `security` (use SECURITY.md instead for
vulnerabilities), `good first issue`, `help wanted`, `needs-info`, `blocked`, and a module label (`finance`, `personal`, `work`,
`career`, `ai`, `backup`, `reminders`, `build`). Milestones follow releases: `0.5.0` (internal testing), `1.0.0`, `1.x`.

## Questions
Open a discussion or an issue with the `question` label. For anything about how the pieces fit, start with
[docs/README.md](docs/README.md).

## Your own privacy (recommended for contributors)
Commits are public and permanent. Use a **GitHub no-reply address** for commits, not a personal or work email:
1. GitHub, Settings, Emails: tick *Keep my email addresses private* and *Block command line pushes that expose my email*.
2. Copy your no-reply address (it looks like `<id>+<username>@users.noreply.github.com`) and set it for this repository:
   `git config user.email "<id>+<username>@users.noreply.github.com"`.
3. Check what Git will use: `git config user.name && git config user.email` (a global setting can silently apply to a fresh clone).
`bash tools/check-no-personal-info.sh` (also run by CI) fails if a personal email, a device serial, local paths, or IDE or assistant
settings are committed in any file. Add `--commits` to also check commit author emails.
