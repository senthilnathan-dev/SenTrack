# ADR 0003: Reminders keep a small mirror outside the encrypted database

- Status: Accepted (2026-10-20)

## Context
An alarm must ring even when the vault is locked, but the encrypted database cannot be opened without the user's passphrase.

## Decision
Keep a small mirror of upcoming alarms (`ReminderStore`) in private app storage. By default it holds only times and ids, and
notifications are generic. Reminder titles are stored in the mirror, and shown, only if the user opts in. The database remains the
source of truth and the mirror is rebuilt after every unlock.

## Consequences
Reminders work offline and locked. The mirror is not encrypted by SenTrack, which is disclosed in the privacy policy
(`docs/legal/privacy.txt`) and listed in `docs/02-security-model.md`.
