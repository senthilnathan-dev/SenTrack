# About, privacy, terms, licenses

Settings -> **About SenTrack** opens `AboutHost` (`app/.../ui/about`), which has its own pages and Back handling:
version (read from the installed package, so it cannot disagree with the build), **Privacy policy**, **Terms of use**,
**Open-source licenses**, **What's new** (changelog) and a contact line.

All wording lives in `AboutContent.kt`, away from UI code. **Rule: if a change alters what data is stored, collected or
sent, update the privacy text in the same commit.**

## Before a Play Store release (not done yet)
* Play requires a **publicly hosted privacy policy URL** in the store listing and the Data safety form. Publish the same
  text (for example GitHub Pages) and keep it in step with `AboutContent.privacy`.
* The texts are a careful first draft for a private-data app, **not legal advice**: have them reviewed before release,
  and add the developer's legal name and contact.
* Replace the hand-written license list with generated notices (a license-report Gradle plugin) and include the full text
  of each license, so the list cannot go stale as dependencies change.
* Keep `versionCode`/`versionName` in `app/build.gradle.kts` in step with the changelog.
