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

## One-handed use (Material 3, One UI style)
- Large collapsing header (`AppScreen`): the title area pushes content down toward the thumb and collapses on scroll
- Navigation lives at the bottom: the module bar (Finance, Personal, Work) and, inside Finance, the section tabs (Dashboard, Accounts, Activity) stacked directly above it
- Main actions are pinned at the bottom (`ActionBar`: Save and its error message) or float bottom-right (Add entry)
- Frequent actions use icons (settings, lock, edit, archive, delete, restore); rarely used or ambiguous actions keep a text label next to the icon
- Archived items are hidden by default and reached through an archive button that opens its own page
- `LocalBottomInsetHandled` tells screens inside the shell not to pad for the system navigation bar twice
