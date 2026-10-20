# Release process and checklist

Releases: **0.5.0** goes to Play **internal testing**; after testing and fixes, **1.0.0** is the first public release;
then 1.x for improvements and bug fixes (1.0.1, 1.1.0, ...).

## Versioning
* `versionName`: 0.5.0 -> 1.0.0 -> 1.0.1 (bug fix) -> 1.1.0 (new features). Pre-1.0 numbers are testing builds.
* `versionCode`: a whole number that **must increase with every upload** to Play (currently 5). Never reuse one.
* Both live in `app/build.gradle.kts`. The changelog's first entry must match `versionName` (a unit test checks).

## Every release, in order
1. Branch from `main`; make the changes.
2. Add a release entry at the **top** of `docs/legal/changelog.txt` (Added / Changed / Fixed / Security, in plain
   language for users).
3. Update `docs/legal/privacy.txt`, `terms.txt` and `manual.txt` if behaviour changed.
4. `bash tools/make-legal-pages.sh`.
5. Bump `versionCode` and `versionName`.
6. Run unit tests (`./gradlew test`) and the migration test on a phone (`./gradlew :core-data:connectedDebugAndroidTest`).
   Add a migration test case for every schema version users may have.
7. Build and test the **release** build on the phone (R8 is on): every tab, on-device AI, backup and restore, reminders after a
   reboot, biometric unlock, upgrade over the previous build keeping data.
8. Merge to `main`, tag `vX.Y.Z`, build the bundle, upload to the chosen Play track.
9. Update the Play "What's new" text with the changelog entry (500 characters max).

## One-time setup: signing
1. Create an upload key (keep it outside the repository; `.gitignore` already excludes keystores):
   `keytool -genkeypair -v -keystore C:\keys\sentrack-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
2. Back the keystore and both passwords up in two separate places.
3. Create `keystore.properties` in the repository root (git-ignored):
   ```
   storeFile=C:/keys/sentrack-upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
   `app/build.gradle.kts` reads it. Without the file the release build is unsigned, so nothing breaks for anyone without the key.
4. Enrol in **Play App Signing** when creating the app in Play Console (default). Google keeps the real signing key; your
   upload key can be reset if lost.
5. Build the bundle: `./gradlew :app:bundleRelease` -> `app/build/outputs/bundle/release/app-release.aab`.

## Play Console setup (once)
- [ ] Developer account (one-time fee). Personal accounts created recently must run a **closed test with at least 12 testers
      for 14 days** before production access; plan for it. Internal testing has no such wait.
- [ ] Create the app: name SenTrack, default language English (India), App, Free.
- [ ] **Privacy policy URL**: publish `docs/site/privacy-policy.html` (for example GitHub Pages) and paste the address.
- [ ] **Data safety** and **App content** answers: see `docs/14-store-listing.md`.
- [ ] **Permissions declarations**: exact alarms (reminders the user sets), location (punch in/out, only when tapped).
- [ ] Store listing text and graphics: see `docs/14-store-listing.md`.
- [ ] Internal testing track: add testers by email, upload the 0.5.0 bundle, send them the opt-in link.

## Before 1.0.0 (public)
- [ ] Two weeks or more of real use on your own phone, and the closed-test period if required.
- [ ] Replace the hand-written open-source list with generated notices; run a dependency vulnerability scan.
- [ ] Have the privacy policy and terms reviewed; add the developer's legal name and contact.
- [ ] Decide public or private distribution (private = keep it on internal/closed testing).
- [ ] Re-run the full release-build test; confirm a backup from 0.5.0 restores on 1.0.0.
