# Security review (hardening pass 1, v0.4.0)

A read-through of the code paths added since the first security review, with what was checked, what was changed and what
is still open. This is a developer review, not a penetration test.

## Checked and fine
| Area | Finding |
|---|---|
| Data at rest | All records are in the SQLCipher database; attachments, resumes, certificates and proof files go through the encrypted file vault. New Work, Career and AI tables inherit this. |
| Backup scope | `BackupEngine` zips the whole database file plus every vault `.bin`, so new tables and files are backed up automatically. The on-device model lives in `files/models`, outside the backup (public, large, re-downloadable). |
| Android backup | `allowBackup=false`; only SenTrack's own encrypted backup copies data. |
| Screens | `FLAG_SECURE` is on the activity window; Compose dialogs use `SecureFlagPolicy.Inherit`, so dialogs (including AI answers) are covered too. |
| Logging | No logging calls anywhere in the app or modules, so nothing sensitive reaches logcat. |
| Network | Only HTTPS endpoints (Google Drive, Gemini, OpenAI, Anthropic, Hugging Face). Cleartext is off by default at target 36. API keys travel in headers, never in URLs. |
| Reminders | Notifications are generic ("SenTrack reminder") with private lock-screen visibility unless the user opts in to showing text. |
| Location | Read once per tap, only with permission, stored in the encrypted database, never logged or sent. |
| Model download | HTTPS only (redirects checked), resumable, SHA-256 verified against a pinned hash before the file is ever loaded; mismatches are deleted. |
| AI privacy | Off by default. Cloud use requires the user to see and confirm the exact text. On-device use sends nothing. |
| Lock | The loaded model is freed whenever the app locks; viewing caches are wiped as before. |

## Changed in this pass
* **Workspace links are http/https only** (`WorkspaceLogic.normalizeUrl`): `file:`, `intent:`, `javascript:` and other
  schemes are refused, with tests.
* **AI results are copied as sensitive clipboard content** (`copySensitive`), so Android hides them in clipboard previews.
* **R8 keep rules** for SQLCipher and LiteRT-LM as consumer rules in `core-data` and `core-ai`, so a minified release build
  does not strip classes that native code calls by name.
* **Version** set to 0.4.0 (code 4) to match the changelog.
* **Migration test** (`core-data/src/androidTest/.../MigrationTest.kt`): builds old databases from the exported schemas
  (versions 1, 6 and 8), migrates them to the current version with the real auto-migrations and checks that data and all
  current tables survive.

## Open items
* **Run the migration test on a phone**: `./gradlew :core-data:connectedDebugAndroidTest`. Add a case per release in the
  field. Until it has run, the 8 -> 12 jump (versions 9 to 11 were never built) is reasoned, not proven.
* **Test a release build** (R8 on) on the phone: sign in, open every tab, run the on-device AI, back up and restore.
  Minification is the most common cause of "works in debug, crashes in release".
* **AI API key**: stored encrypted in the database and inside encrypted backups. Acceptable, but a restore onto another phone
  also restores the key; the About text should say so before release.
* **Dependency audit**: run a vulnerability scan on the dependency tree before release.
* **No certificate pinning** for the cloud services; standard TLS only. Reasonable for this threat model.
* **Clipboard**: copied text is still readable by the foreground app; the AI dialog is the only place that copies.
* **Not reviewed**: a hostile `.litertlm` model file (the catalog is pinned by hash, so only listed models load).

## Addendum (0.5.0)
* `FLAG_SECURE` is skipped only when the app is **debuggable** (debug builds, so store screenshots can be taken). Release
  builds, the only ones users install, always set it. Verify on the release build that screenshots are blocked.
* The privacy policy, terms and user guide are now tested against the code (permissions and network services must be
  described), see `docs/10-legal-about.md`.
