# Security Model

Covers the design and what is built as of v0.5.0. Findings from the hardening review are in
[12-security-review](12-security-review.md).

## Assets
Finance records, tax documents, notes and attachments, work records and location stamps, career records and resumes, reminders,
AI API keys.

## Threats considered
Lost or stolen phone, malware or other apps, shoulder surfing and screenshots, cloud backup exposure, rooted device,
forgotten passphrase, tampered backups, tampered AI model files.

## Controls
| Area | Control |
|---|---|
| Data at rest | SQLCipher (AES-256) database; every attachment, resume, certificate and proof file in the file vault with AES-256-GCM |
| Keys | One random 256-bit data key, wrapped by (1) an Argon2id key from the passphrase (64 MiB, 3 passes), (2) a key from the recovery code (HKDF), and (3) optionally a per-enrolment Android Keystore key unlocked by biometrics. Database and file keys are derived from the data key with HKDF ("database", "files") |
| Recovery | One-time recovery code shown at setup, held by the user only; never stored in Drive. Generating a new one invalidates the old |
| Access | Passphrase or biometric unlock; auto-lock when the screen turns off and after a chosen background time (immediately, 30 s default, 1 min, 5 min); back-off after repeated wrong passphrases; `FLAG_SECURE` on the window (dialogs inherit it) |
| External screens | Choosing a file or signing in to Google does not lock the app: a 5-minute grace applies only to screens SenTrack itself starts |
| Backup | Android's own backup is disabled. The Drive backup copies data that is already encrypted, uses only the private `appDataFolder`, strips the biometric slot, and is verified by checksum on restore |
| Network | Offline by default. HTTPS only. No analytics, ads or trackers. API keys travel in request headers, never in URLs |
| AI | Off by default. On-device runs send nothing. Cloud use shows the exact text and needs confirmation. The API key is stored in the encrypted database. Model files are HTTPS-downloaded, pinned by SHA-256 and loaded only if the hash matches |
| Destructive actions | Archive first. Permanent deletion needs the word DELETE (the company name for a company) and also erases attachments and reminders |
| Caches | Decrypted previews live in a short-lived cache that is deleted on close and whenever the app locks. The loaded AI model is freed on lock |
| Clipboard | Text copied from the AI assistant is marked sensitive |
| Links | Workspace links accept only `http` and `https` |
| Supply chain | Versions pinned in the version catalog. A dependency vulnerability scan is **not yet automated** (see the release checklist) |

## What is stored outside the encrypted database
Private app storage, protected by Android but not encrypted by SenTrack:
- Reminder **times** in a small mirror so alarms ring while locked; reminder **titles** only if the user turns on "Show
  reminder text in notifications" (off by default; see [06-reminders](06-reminders.md)).
- Settings: auto-lock time, backup schedule and options, the Google account email chosen for backup, and the last release notes
  version seen.
- The on-device AI model file (a public open model).
The privacy policy states all of this.

## Out of scope
Protection against a fully compromised OS or an attacker with both the unlocked phone and the passphrase. A rooted phone can
read the items listed above, but not the encrypted records.

## Implementation notes
- Vault: random data key wrapped as above; database opened with a raw key derived from the data key. Verified on a Samsung A34
  (Android 16): the file header is random bytes with no `SQLite format` marker and no plaintext strings.
- File vault: per-file random key wrapped by a vault sub-key and bound to the file id; 64 KiB AES-GCM chunks with a final-chunk
  flag (detects tampering, truncation, extension and swapping); opaque UUID file names.
- Change passphrase re-wraps the key without re-encrypting data.
- Biometric slot: each enrolment uses a fresh, uniquely named Keystore key (its id is stored in the slot), so a stale key can
  never be reused after disable, restore or re-enable; any mismatch resets the slot cleanly and tells the user.
- Release builds block screenshots. Debug builds (never installed by users) do not, so store screenshots can be taken.

## Known limits
- The passphrase is held in a Compose string while typed and cannot be wiped; the wrong-passphrase back-off counter is in
  memory only (offline attack resistance comes from Argon2id).
- The backup manifest is not signed, so someone with Drive access could substitute an older valid backup (rollback).
- No certificate pinning for cloud services (standard TLS).
- Copied text stays readable by the foreground app until replaced.
