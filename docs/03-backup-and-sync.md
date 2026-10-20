# Backup and Sync (Phase 2)

## Decisions
- Storage: Google Drive hidden `appDataFolder` (scope `drive.appdata`, non-sensitive, no Google review), with a `SenTrackAppBackup` subfolder
- Model: full snapshot of data that is **already encrypted at rest**; no key is needed to create one, so backups can run while locked
- Retention: last 3 by default, configurable
- Schedule: cron-style expressions plus presets; minimum gap 15 minutes; network policy Wi-Fi only / mobile only / any
- Backup key slots: **setting**. Default = recovery code only (the passphrase slot is stripped, so a stolen backup has nothing to brute-force). Optional: include the passphrase slot, with a warning that passphrase strength then protects the backup
- The biometric slot is never included (it is bound to the phone's Keystore)
- Anyone using the Play Store app signs in with their own Google account; backups use their own Drive quota. No Google Cloud account is needed by users; the Cloud project belongs to the developer

## Snapshot file (`.stbk`, a zip)
| Entry | Content |
|---|---|
| `manifest.txt` | version, creation time, app version, whether the passphrase slot is present, SHA-256 + size of every part |
| `vault.hdr` | vault header (wrapped keys), biometric slot removed |
| `sentrack.db` (+ `-wal` if present) | SQLCipher database, copied while closed |
| `files/<uuid>.bin` | encrypted attachments |

Restore verifies every checksum and the entry whitelist (blocks zip-slip) before touching local data, stages the
files, swaps them in, and rolls back if any step fails.

## Known limits
- The manifest is not signed (signing would need the vault key). Parts are individually authenticated by their own
  encryption, but an attacker with Drive access could substitute an older valid backup (rollback)
- Background jobs may run a few minutes late (Android Doze)

## Status
- Done: snapshot engine, cron parser, vault header export, Drive REST client (fake-server tests), Google authorizer, backup manager, Backup screen (connect, back up now, list, restore, retention, passphrase-slot setting)
- Done: account chooser (shows and pins the Google account, switch, disconnect), WorkManager scheduling (one delayed job to the next cron time, re-queued after each run; network policy and charging as constraints; 2 retries with back-off)
- Done: plain-language schedules (ScheduleSpec: every N hours, daily, chosen weekdays, monthly, plus advanced cron) with a time picker, day chips and a next-run preview
- Done: monthly schedules work on any day of the month. Days 1 to 28 are exact; the 29th to 31st run on that day, or on the last day of a shorter month (`ScheduleSpec.MonthlyClamped`). Previews and the WorkManager job use the schedule's own `nextAfter`, which is exact for every kind
- Not done: a failure notification for scheduled backups, an in-app history of past runs

## What a backup contains (v0.5.0)
The snapshot is the whole database file plus every vault file, so every feature is included automatically: Finance, Personal,
Work, Career, reminders and the AI settings (including a saved API key, encrypted). Not included: the on-device AI model file
(public and re-downloadable), caches, and the small private settings files (backup schedule, auto-lock time, reminder mirror), which
are not part of the backup, so they start from defaults on a new phone and the schedule must be set again. Reminders themselves live in the database and are registered again with Android after unlocking.
