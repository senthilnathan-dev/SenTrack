# Release checklist (not done yet)

What is still needed before SenTrack goes to the Play Store. Tick items off here as they are done.

## Build and signing
- [ ] Create an **upload key** once and keep it safe, outside the repository (the `.gitignore` already excludes keystores):
      `keytool -genkeypair -v -keystore sentrack-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
- [ ] Back the keystore and its passwords up in two places. Losing the upload key is recoverable with Play App Signing; losing
      the *app signing* key is not, so enrol in **Play App Signing** (default for new apps).
- [ ] Add a `signingConfigs.release` that reads the keystore path and passwords from `local.properties` or environment
      variables, never from the repository.
- [ ] Build the **App Bundle**: `./gradlew :app:bundleRelease`. Test the *release* build on the phone (R8 is on): every tab,
      on-device AI, backup and restore, reminders after a reboot, biometric unlock.
- [ ] Run `./gradlew :core-data:connectedDebugAndroidTest` (migration test) on the phone.
- [ ] Bump `versionCode` for every upload; keep `versionName` and the changelog in `AboutContent.kt` in step.

## Store requirements
- [ ] **Privacy policy URL** (public page). Publish the text from `AboutContent.privacy`; get it reviewed.
- [ ] **Data safety form**: the app collects no data on its own servers. Declare: optional Google Drive backup (user's own
      Drive), optional AI requests to a provider the user chooses with their own key, location only on punch (stays on the
      device), a one-off model download from Hugging Face. Data is encrypted at rest; users can delete it.
- [ ] **Permissions declarations**: exact alarms (reminders), notifications, location (punch in/out), biometric. Have a
      sentence ready for each.
- [ ] App content: target audience (adults), no ads, no user-generated content shared with others.
- [ ] **Listing**: name, short and full description, at least 2 phone screenshots (taken with a debug build, since release
      builds block screenshots by design), a 512 px icon and a 1024x500 feature graphic.
- [ ] A real **launcher icon** (the current one is the template default).
- [ ] Pick the track: **Internal testing** first (up to 100 testers, no review wait), then Closed, then Production. New
      personal developer accounts must run a closed test with at least 12 testers for 14 days before production.

## Legal and product decisions still open
- [ ] Public or private distribution.
- [ ] Developer legal name and contact on the Terms and Privacy pages.
- [ ] Generated third-party licence notices (replace the hand-written list).
- [ ] Dependency vulnerability scan.
