# SenTrack

A private, offline-first Android app for tracking **finance**, **personal life** and **work/career** in one place, with encrypted storage and an encrypted Google Drive backup.

- Package: `in.senthilnathan_dev.sentrack`
- Platform: Android 16+ (minSdk/targetSdk 36), Kotlin + Jetpack Compose
- Status: Phase 0 (foundation). See [docs/00-project-decisions.md](docs/00-project-decisions.md)

## Documentation
| Doc | Purpose |
|---|---|
| [00-project-decisions](docs/00-project-decisions.md) | Scope, fixed decisions, phases |
| [01-architecture](docs/01-architecture.md) | Layers, modules, data flow |
| [02-security-model](docs/02-security-model.md) | Threat model, key management, encryption |
| [adr/](docs/adr) | Architecture decision records (why we chose what we chose) |

## Build and run
Prerequisites: Android Studio (stable), Android SDK 36, JDK 21.

1. Open the project folder in Android Studio and let Gradle sync.
2. Select a device (a physical phone with Wireless debugging works well) and press Run.
3. Command line: `./gradlew :app:assembleDebug`

Keep the Gradle cache off the system drive by setting `GRADLE_USER_HOME` (this machine uses `D:\Android\.gradle`).

## Conventions
- Version catalog: all dependency versions live in `gradle/libs.versions.toml`.
- Every public class/function carries KDoc; comments explain *why*, not *what*.
- Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`), semantic versioning.
- Secrets, keystores and signing files are never committed (see `.gitignore`).
