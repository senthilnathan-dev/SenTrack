# Reminders Engine (Phase 7, built early)

Shared by every module: Finance (bills, FD maturity, SIP dates), Personal (important dates, habits), Work (tasks, leave).

## Behaviour
- Standalone reminders now (Notifications bell in the top bar); other modules attach reminders later via `ReminderRepository`
- When: one date and time; repeat is derived from it (none, every day, chosen weekdays, every month, every year)
- How it rings: Notification, or Alarm (Android alarm-clock API: status-bar alarm icon, never delayed by battery saving)
- Snooze 10 min and Done buttons on the notification
- Survives restart, app update, and time or time-zone changes (`BootReceiver`); a one-off missed while the phone was off is shown straight away
- Works while the vault is **locked**

## Privacy design
The encrypted database holds the full reminder (title, note). An alarm must ring when the database key is not in memory, so a small mirror of upcoming alarms (`ReminderStore`) lives in private app storage:
- By default notifications are generic ("SenTrack reminder"), the lock-screen version is generic too, and **no title is stored** in the mirror
- Setting "Show reminder text in notifications" stores titles in the mirror and shows them; off by default
- After each unlock the app reconciles: one-offs that rang while locked are marked completed, and the mirror is refreshed from the database

## Permissions
`POST_NOTIFICATIONS` (asked at runtime), `SCHEDULE_EXACT_ALARM` (user grants in system settings; without it notifications may be a few minutes late; Alarm style does not need it), `RECEIVE_BOOT_COMPLETED`, `VIBRATE`.

## Code
- `core-alerts`: `ReminderDef` + `ReminderTiming` (pure, tested), `ReminderStore`, `AlarmScheduler`, `ReminderNotifier`, `ReminderCenter`, receivers, `ReminderRepository`
- `core-data/reminders`: `ReminderEntity`, `ReminderDao` (schema v5)
- `feature-reminders`: list, editor, permission and settings screens
