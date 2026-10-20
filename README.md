# SenTrack

A private, offline-first Android app for tracking **finance**, **personal life**, **work** and **career** in one place. Everything is stored encrypted on the phone. An encrypted Google Drive backup and an optional AI assistant (on-device or with your own key) are the only things that can reach the network, and both are opt-in.

- Package: `in.senthilnathan_dev.sentrack`
- Platform: Android 16+ (minSdk and targetSdk 36), Kotlin + Jetpack Compose (Material 3)
- Version: **0.5.0** (first internal-testing build). Releases are tagged `vX.Y.Z`; see [CHANGELOG.md](CHANGELOG.md)
- Status: all planned feature phases are built. Current work is hardening and release preparation: [docs/13-release-checklist.md](docs/13-release-checklist.md)

## Documentation
Start with the [documentation index](docs/README.md). The short list:

| Doc | Purpose |
|---|---|
| [00-project-decisions](docs/00-project-decisions.md) | Scope, decisions as built, phase status |
| [01-architecture](docs/01-architecture.md) | Modules, layers, data flow, dependency rules |
| [02-security-model](docs/02-security-model.md) | Threats, keys, encryption, what is and is not protected |
| [15-data-model](docs/15-data-model.md) | Database schema versions, tables, migrations |
| [13-release-checklist](docs/13-release-checklist.md) | How to version, test, sign and publish a release |
| [10-legal-about](docs/10-legal-about.md) | How the privacy policy, terms, user guide and changelog are kept in step |

## Build and run
Prerequisites: Git, Android Studio (stable) with Android SDK Platforms 36 and 37, and internet on the first sync (Gradle downloads its own JDK 25; nothing to install by hand). Full guide: [docs/16-development-setup.md](docs/16-development-setup.md).

1. Open the project folder in Android Studio and let Gradle sync.
2. Select a device (a physical phone with Wireless debugging works well) and press Run.
3. Command line: `./gradlew :app:assembleDebug`

Keep the Gradle cache off the system drive by setting `GRADLE_USER_HOME` (this machine uses `D:\Android\.gradle`).

## Test
- Unit tests (on the computer): `./gradlew test`
- Database migration test (needs a connected phone): `./gradlew :core-data:connectedDebugAndroidTest`
- Release build check: switch the build variant to `release` and test on the phone (R8 is on). See the checklist.

## Changing words users read
The privacy policy, terms, user guide and release notes live in `docs/legal/*.txt` and are shared by the app and the web pages. After editing them run `bash tools/make-legal-pages.sh`, then the unit tests. Details in [docs/10-legal-about.md](docs/10-legal-about.md).

## Conventions
- Version catalog: all dependency versions live in `gradle/libs.versions.toml`.
- Every public class and function carries KDoc; comments explain *why*, not *what*.
- Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`), semantic versioning (see the release checklist).
- Database changes bump the schema version, keep the exported schema file in `core-data/schemas` and get a migration test.
- Secrets, keystores and signing files are never committed (see `.gitignore`).
- Every change that alters what data is stored, collected or sent also updates `docs/legal/privacy.txt`.

## Contributing
Humans and AI agents are both welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md) (branches, commits, pull requests, rules),
[AGENTS.md](AGENTS.md) (instructions for AI agents), [docs/16-development-setup.md](docs/16-development-setup.md) (set up any
machine) and [docs/17-git-workflow.md](docs/17-git-workflow.md). Security issues: [SECURITY.md](SECURITY.md). Conduct:
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Licence: [LICENSE](LICENSE) (all rights reserved for now).

## Releasing
`bash tools/bump-version.sh X.Y.Z`, then `bash tools/build-release.sh` for the signed bundle and APK. Pushing a `vX.Y.Z` tag runs the
Release workflow on GitHub. Full runbook: [docs/13-release-checklist.md](docs/13-release-checklist.md).
