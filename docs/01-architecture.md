# Architecture

## Style
Clean architecture with MVVM, single-activity Jetpack Compose UI, offline-first.

```
UI (Compose screens) -> ViewModel (state) -> Use cases -> Repository interfaces
                                                              |
                                          Data layer: Room (SQLCipher) + encrypted file store
                                                              |
                                     Sync layer: encrypted snapshot -> Drive appDataFolder
```

## Planned modules (introduced phase by phase)
| Module | Responsibility |
|---|---|
| `app` | Entry point, navigation, DI wiring |
| `core-security` | Key management, encryption, app lock, recovery code |
| `core-data` | Database, file vault, migrations |
| `core-sync` | Backup/restore, Drive client, scheduling |
| `core-ui` | Theme, shared components |
| `feature-finance` / `-personal` / `-work` / `-career` | Feature screens and logic |
| `core-notify` | Reminders, alarms, notification channels |
| `core-ai` | Provider-agnostic AI layer (local default) |

Rules: features depend on `core-*`, never on each other; the UI never touches the database directly.

## Offline-first
The local database is the source of truth. Network features (Drive sync, optional cloud AI) are optional and never block normal use.
