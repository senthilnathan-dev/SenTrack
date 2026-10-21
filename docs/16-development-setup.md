# Development setup (any machine)

Goal: clone the repository on a new computer and have it building and running on a phone without guesswork.

## You need
| Tool | Notes |
|---|---|
| Git (on Windows: Git for Windows, which includes Git Bash) | The helper scripts in `tools/` are bash scripts |
| Android Studio (stable channel) | Installs the Android SDK and a bundled JDK |
| Android SDK Platform **37** (compile) and **36** (target) | SDK Manager, SDK Platforms tab |
| An Android 16 phone (API 36) with Developer options, or an Android 16 emulator | minSdk is 36, so older phones cannot install it |
| Internet on the first Gradle sync | Downloads Gradle 9.6 and a JDK (see below) |
| About 10 GB of free disk | Gradle cache, SDK, build output |

**JDK:** you do not install one by hand. `gradle/gradle-daemon-jvm.properties` asks Gradle for JDK 25 and the Foojay resolver
(configured in `settings.gradle.kts`) downloads it on first use. The Android code itself compiles to Java 11. In Android Studio,
Settings, Build Tools, Gradle, *Gradle JDK* should be left on "Gradle Daemon JVM" (or any JDK 25).

You do **not** need: a signing key (debug builds are signed automatically), any API key (AI is optional), a Google Cloud project
(only for developing the Drive backup, see below), or a physical USB cable (Wi-Fi debugging works).

## Clone and open
```bash
git clone <repository-url> SenTrack
cd SenTrack
git config user.name  "Your Name"          # repo-local identity, so commits are attributed correctly
git config user.email "<id>+<username>@users.noreply.github.com"   # your GitHub no-reply address, never a personal or work email
```
A global `user.email` (for example a work address) silently applies to every repository that has no local setting, so always set the local one and check it with `git config user.email`. See "Your own privacy" in CONTRIBUTING.md.

Line endings are normalised by `.gitattributes` (LF in the repository on every system), so no extra Git setting is needed.

1. Android Studio, *Open*, choose the `SenTrack` folder. Let Gradle sync (first sync takes several minutes).
2. Android Studio writes `local.properties` with your SDK path. It is git-ignored: never commit it. On a command line without
   Android Studio, set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to your SDK folder instead.
3. Optional: keep Gradle's cache off the system drive: set the environment variable `GRADLE_USER_HOME` (the original machine
   uses `D:\Android\.gradle`).

## Run on a phone
1. On the phone: Settings, About phone, tap *Build number* seven times; then Settings, Developer options.
2. USB: turn on *USB debugging*, connect, accept the prompt. **Or** Wi-Fi: turn on *Wireless debugging*, choose *Pair device with
   pairing code*, then on the computer:
   ```bash
   adb pair <ip>:<pairing-port>      # enter the 6-digit code
   adb connect <ip>:<connect-port>
   adb devices
   ```
   (`adb` is in `<sdk>/platform-tools`.)
3. Pick the device in Android Studio and press Run, or `./gradlew :app:installDebug`.
4. First launch: create a passphrase and **save the recovery code**. Data lives only on that install; uninstalling erases it.
   Debug builds allow screenshots; release builds block them on purpose.

## Everyday commands
```bash
./gradlew test                                   # unit tests
./gradlew :app:assembleDebug                     # build the debug APK
./gradlew lintDebug                              # lint
./gradlew :core-data:connectedDebugAndroidTest   # migration test, with a phone connected
bash tools/make-legal-pages.sh                   # after editing docs/legal/*.txt
bash tools/bump-version.sh 0.6.0                 # new version (see docs/13-release-checklist.md)
```
The repository includes the Gradle wrapper, so use `./gradlew` (`gradlew.bat` on Windows), not a system Gradle.

## Things only some work needs
- **Release builds** need your own upload key: `bash tools/create-upload-key.sh` (creates `keystore.properties`, git-ignored). See
  [13-release-checklist](13-release-checklist.md).
- **Google Drive backup** works through the user's own Google account and the Drive `appdata` scope. To develop it on your own
  build you may need an OAuth client for your package name and signing key in Google Cloud Console; the original author's client is
  not part of the repository.
- **On-device AI** downloads a model (hundreds of MB to a few GB) from the internet when you tap Set up; use Wi-Fi.
- **Cloud AI** needs your own API key, typed in the app. Keys never go in the repository.

## Troubleshooting
| Symptom | Fix |
|---|---|
| `SDK location not found` | Open the project in Android Studio once, or create `local.properties` with `sdk.dir=<path to SDK>` |
| `Failed to find Platform SDK with path: platforms;android-37` | SDK Manager, install *Android SDK Platform 37* |
| Gradle cannot find a JDK 25 / toolchain download fails | Check internet or proxy; or install a JDK 25 and point Gradle JDK to it |
| `Schema 'N.json' required but not found` (Room/KSP) | A schema file is missing. Pull the latest `core-data/schemas`; never skip a version without an auto-migration from the last committed schema |
| `'when' expression must be exhaustive` after pulling | A sealed type gained a case; add a branch where the compiler points |
| `Unable to establish loopback connection` when running Gradle | A sandboxed or restricted terminal blocks Gradle's local socket. Run Gradle from Android Studio or a normal terminal |
| Terminal warning `sun.misc.Unsafe ... will be removed` | Harmless warning from a build-tool library; ignore |
| `LF will be replaced by CRLF` warnings on Windows | Harmless; `.gitattributes` keeps the repository in LF |
| App installs but crashes only in release | R8 stripped something; send the stack trace; keep rules are in `core-data/consumer-rules.pro` and `core-ai/consumer-rules.pro` |
| Phone not listed by `adb devices` | Re-pair Wi-Fi debugging, or use USB and accept the debugging prompt |
| Unit test `LegalTextTest` fails | You edited `docs/legal/*` or the version; run `bash tools/make-legal-pages.sh` and fix the changelog entry |

## Where to go next
[docs/README.md](README.md) for the documentation map, [CONTRIBUTING.md](../CONTRIBUTING.md) for how to propose changes,
[17-git-workflow](17-git-workflow.md) for branches, commits and releases.
