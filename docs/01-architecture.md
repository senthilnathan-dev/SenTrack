# Architecture

## Style
Single-activity Jetpack Compose app, offline-first, with shared `core-*` modules for infrastructure and one `feature-*`
module per product area. The encrypted local database is the source of truth.

```
Compose screens (feature-*, app)
        |  read Flows, call suspend functions
Repositories (core-data, core-alerts)        pure logic (Calculator, Math, Engine objects, unit-tested)
        |
Room + SQLCipher database (core-data)   +   encrypted file vault (core-security)
        |
Backup snapshot (core-sync) -> Google Drive appDataFolder
```

## Modules
| Module | Responsibility |
|---|---|
| `app` | `MainActivity`, `SenTrackApp`, `AppContainer` (manual DI), vault and settings screens, backup UI and worker, the unlocked shell with the four tabs, About, AI settings and assist implementation |
| `core-security` | Vault (data key wrapped by passphrase, recovery code and optional biometric), crypto (Argon2id, chunked AES-GCM), file vault |
| `core-data` | The one Room database and its migrations; entities, DAOs and repositories for Finance, Reminders, Personal (notes, habits, plans and moments), Work and Career; exported schemas |
| `core-sync` | Snapshot backup engine, Google Drive client, cron and `ScheduleSpec` schedules, network policy |
| `core-alerts` | Reminders engine: scheduling, alarms, notifications, boot handling, the reminder repository |
| `core-attachments` | Import, export and viewing of encrypted attachments (PDF, image, video) |
| `core-ui` | Theme, shared components and pickers, and the shared AI assist interface and button |
| `core-ai` | AI providers and requests, on-device runtime wrapper, model catalog and downloader, device checks, answer styles, prompts and the number guard |
| `feature-finance` | Finance screens and pure calculators |
| `feature-personal` | Notes, habits, plans, moments, timeline |
| `feature-work` | Companies, attendance, leave, tasks, appreciation, permissions |
| `feature-career` | Learning, certificates, job hunt, resumes, workspace, checklists |
| `feature-reminders` | The Reminders page and editor |

## Rules
- Feature modules depend on `core-*` modules, never on each other. A feature may use another area's data only through
  `core-data` (for example Career reads companies from the Work repository).
- Screens never touch Room directly; they use repositories. Repositories return `Flow`s that the screen collects while it is
  visible, so data updates live and everything stops when the vault locks.
- The database is only open while the vault is unlocked (`DatabaseHolder`); locking closes it and frees secrets.
- Feature modules contain no AI code. They ask through `LocalAiAssist` (an interface in `core-ui`) and the app supplies the
  implementation, so features work unchanged with AI off.
- Pure logic (calculations, schedules, parsers, scoring) is kept out of composables so it can be unit-tested on the computer.

## Dependency injection
`AppContainer` (created by `SenTrackApp`) builds the long-lived objects: the vault, database holder, repositories, the
reminders engine, the backup manager, AI settings and assistant. Screens receive what they need as parameters from
`MainActivity` and the unlocked shell. There is no DI framework.

## Navigation
`MainActivity` shows a stage (setup, lock, recover, unlocked, settings, backup...) from `VaultViewModel`. Inside the unlocked
shell a bottom bar switches between Finance, Personal, Work and Career. Each tab keeps its own back stack of small sealed
route classes and handles the system Back button itself; section tabs sit just above the bottom bar.

## Offline-first
The local database is the source of truth. Network features (Drive backup, cloud AI, the model download) are optional and
never block normal use.

## Threading
Database access is `suspend` or `Flow` through Room. Long jobs that must outlive a screen (the AI model download and test,
scheduled backups) run in an app-wide scope or WorkManager. CPU-heavy model work runs on `Dispatchers.Default`.
