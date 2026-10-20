# Release process and checklist

Plan: **0.5.0** goes to Play **internal testing**; after testing and fixes, **1.0.0** is the first public release; then 1.x for
improvements and bug fixes (1.0.1, 1.1.0, ...). Git side of releases: [17-git-workflow](17-git-workflow.md).

## Versioning
- `versionName` is `X.Y.Z`. `0.x` are testing builds; `1.0.0` is public.
- `versionCode` = X*10000 + Y*100 + Z (0.5.0 -> 500, 1.0.0 -> 10000, 1.2.3 -> 10203). Play needs it to increase with every upload;
  this rule guarantees it. A unit test checks the rule and that the newest changelog entry equals `versionName`.
- Change both with `bash tools/bump-version.sh X.Y.Z` (it also adds a changelog stub and regenerates the web pages).

## One-time setup
### A. Upload key (on your computer)
1. `bash tools/create-upload-key.sh`. It asks for a folder (outside the repository), creates `sentrack-upload.jks` and writes
   `keystore.properties` in the repository root (git-ignored). `app/build.gradle.kts` reads that file; without it the release build
   is unsigned.
2. **Back up the keystore and its password in two separate places** (password manager plus an encrypted drive).
3. In Play Console choose **Play App Signing** when creating the app (default). Google then holds the real signing key, and a lost
   upload key can be reset by Google support.

### B. GitHub (when the repository exists)
Add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (the script prints how). Set the branch protection and
other recommended settings in [17-git-workflow](17-git-workflow.md).

### C. Play Console
- [ ] Developer account (one-time fee). Newer personal accounts must run a **closed test with at least 12 testers for 14 days**
      before production; plan for it. Internal testing has no such wait.
- [ ] Create the app: name `SenTrack`, default language English (India), App, Free.
- [ ] **Privacy policy URL**: publish `docs/site/privacy-policy.html` (for example GitHub Pages) and paste its address.
- [ ] **Data safety** and **App content** answers: see [14-store-listing](14-store-listing.md).
- [ ] **Permission declarations**: exact alarms (reminders the user sets), location (punch in/out, only when tapped).
- [ ] Store listing text and graphics: [14-store-listing](14-store-listing.md).

## Every release, in order
1. Branch from `main` (`chore/release-X.Y.Z`).
2. `bash tools/bump-version.sh X.Y.Z`; fill in the new entry at the top of `docs/legal/changelog.txt` (Added, Changed, Fixed, Security,
   in plain language for users); delete empty sections.
3. Update `docs/legal/privacy.txt`, `terms.txt`, `manual.txt` if behaviour changed. Then `bash tools/make-legal-pages.sh`.
4. Run `./gradlew test` and, on a phone, `./gradlew :core-data:connectedDebugAndroidTest`. Add a migration test case for every schema
   version users may have.
5. Build: `bash tools/build-release.sh`. It checks the tree is clean, pages are current, versions agree, runs the unit tests and builds
   `app/build/outputs/bundle/release/app-release.aab` and the signed `.apk`.
6. **Test the release APK on your phone** (`adb install -r <apk>`): every tab, on-device AI, backup and restore, reminders after a
   reboot, biometric unlock, and an upgrade over the previous build keeping its data. Confirm screenshots are blocked.
7. Open the PR (`chore(release): X.Y.Z`), squash-merge, then tag: `git switch main && git pull --ff-only &&
   git tag -a vX.Y.Z -m "SenTrack X.Y.Z" && git push origin vX.Y.Z`. The Release workflow drafts a GitHub release with the signed files.
8. Play Console, **Testing, Internal testing** (or Production for 1.0.0): create a release, upload `app-release.aab`, paste the
   changelog entry as the release notes (500 characters maximum), review and roll out. Add testers by email and send them the opt-in link.
9. After rollout, watch the Play Console *Android vitals* and crash reports, and fix forward with a patch release.

## First upload specifics
- The first upload of an app must be done by hand in Play Console (the API cannot create it).
- Internal testing is available immediately; testers install through the opt-in link and Play updates them automatically.
- Keep `versionCode` strictly increasing: a rejected upload still uses up its number.

## Before 1.0.0 (public)
- [ ] Two weeks or more of real use on your own phone, and the closed-test period if required.
- [ ] Replace the hand-written open-source list with generated notices; run a dependency vulnerability scan.
- [ ] Have the privacy policy and terms reviewed; add the developer's legal name and contact.
- [ ] Decide public or private distribution (private means staying on internal or closed testing).
- [ ] Re-run the full release-build test; confirm a backup from 0.5.0 restores on 1.0.0.
- [ ] Run the migration test with a case for every released schema version.
