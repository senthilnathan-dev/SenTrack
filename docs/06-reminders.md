# Reminders Engine (Phase 7, built early)

Shared by every module. Standalone reminders are made from the bell in the top bar; other features create their own reminders through `ReminderRepository` and they appear in the same list (see the table below).

## Behaviour
- Standalone reminders from the Reminders page (bell in the top bar), plus the feature-created reminders listed below
- When: one date and time; repeat is derived from it (none, every day, chosen weekdays, every month, every year). A monthly repeat started on the 29th, 30th or 31st runs on that day, or on the last day of a shorter month (`ScheduleSpec.MonthlyClamped`)
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
- `core-sync`: `ScheduleSpec`, `CronSchedule`
- `feature-reminders`: list, editor, permission and settings screens

## Reminders created by features
Each feature owns one reminder per item and uses a fixed id, so saving the item updates or removes its reminder.

| Feature | Reminder id | Rings | Owner kind |
|---|---|---|---|
| Habits (daily, weekly) | `habit-<id>` | at the chosen time, on the chosen days | `personal:habit` |
| Plans with a target date | `item-<id>` | 9 am on the target date (one-off) | `personal:plan` |
| Important dates | `date-<id>` | N days before at a chosen time; yearly dates repeat, others ring once | `personal:date` |
| Certificates with an expiry | `cert-<id>` | 9 am, 30 days before expiry (one-off) | `career:cert` |
| Interviews | `interview-<id>` | 1 hour before (one-off) | `career:interview` |
| Repeating finance entries | `recurring-<id>` | 9 am on the due day, every month | `finance:recurring` |

Archiving or finishing an item pauses or removes its reminder; deleting the item deletes it. Habits for monthly, quarterly and yearly
periods have no automatic reminder (the user can add one by hand).

## Schedules
`ScheduleSpec` (in `core-sync`) describes repeats in plain terms: every N hours, daily, weekly on chosen days, monthly on days 1 to 28
(`Monthly`), monthly on days 29 to 31 with last-day clamping (`MonthlyClamped`), yearly, and advanced cron. `nextAfter` gives the
exact next run for every kind, which `ReminderTiming` and the backup scheduler both use.
