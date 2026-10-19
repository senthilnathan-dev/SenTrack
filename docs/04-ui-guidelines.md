# UI Guidelines

All screens are built from the shared kit in `app/.../ui/components/Components.kt`. Add new shared pieces there; do not style per screen.

| Piece | Use |
|---|---|
| `AppScreen(title, onBack)` | Screens inside the unlocked app: top bar with back arrow over a scrolling column |
| `CenteredScreen` | Sign-in style pages (setup, lock, recover): centred, readable width |
| `Section(title, description)` | A card grouping related settings; one topic per section |
| `SwitchRow`, `ChoiceRow`, `NavRow` | Settings rows: title left, optional explanation below, control right |
| `Message(text, kind)` | Errors (red), confirmations (primary), hints (secondary); renders nothing for null |
| `PrimaryButton` / `SecondaryButton` | Full-width actions; the primary shows a spinner while busy |
| `SecretField` | Passphrase input (keep the text in `remember`, never `rememberSaveable`) |

Rules
- Spacing: 16 dp between sections, 12 dp inside a section, 16 dp screen margins
- Plain language: never show technical syntax as the main way to choose something (schedules use "Every day at 9:30 PM"; cron is an Advanced option)
- Every error shown to the user says what happened and what to do next
- Light, dark and system themes must both look right
