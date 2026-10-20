# UI Guidelines

All screens are built from the shared kit in the `core-ui` module (`Components.kt`, `Pickers.kt`, `AiAssist.kt`). Add new
shared pieces there; do not style per screen.

| Piece | Use |
|---|---|
| `AppScreen(title, onBack, actions, bottomBar, floatingActionButton, largeHeader)` | Screens inside the unlocked app: collapsing large header (or a slim bar with `largeHeader = false`) over a scrolling column |
| `CenteredScreen` | Sign-in style pages (setup, lock, recover): centred, readable width |
| `Section(title, description)` | A card grouping related content; one topic per section |
| `SwitchRow`, `ChoiceRow`, `NavRow` | Settings rows: title left, optional explanation below, control right |
| `Message(text, kind)` | Errors (red), confirmations (primary), hints (secondary); renders nothing for null |
| `PrimaryButton` / `SecondaryButton` | Full-width actions with an optional icon; the primary shows a spinner while busy |
| `ActionBar` | Pins the main action of a form (Save) and its error message at the bottom |
| `IconAction` | A single-icon button with a spoken description, for common actions |
| `SecretField` | Passphrase input (keep the text in `remember`, never `rememberSaveable`) |
| `PickerField`, `DateField`, `TimeField` | Drop-down, calendar and clock pickers (`formatDate`, `formatTime12` for display) |
| `AiAssistButton` | The sparkle that offers AI help; see [11-ai](11-ai.md) |
| `LocalBottomInsetHandled`, `LocalExternalFlowStarter`, `LocalAiAssist` | Composition locals provided by the app shell |

## Rules
- Spacing: 16 dp between sections, 12 dp inside a section, 16 dp screen margins
- Plain language: never show technical syntax as the main way to choose something (schedules use "Every day at 9:30 PM"; cron
  is an Advanced option)
- Every error shown to the user says what happened and what to do next
- Light, dark and system themes must both look right
- Anything that opens a system screen (file picker, sign-in, another app) first calls the external-flow starter so the app does
  not auto-lock on return

## One-handed use (Material 3, One UI style)
- Large collapsing header (`AppScreen`): the title area pushes content down toward the thumb and collapses on scroll
- Navigation lives at the bottom: the module bar (Finance, Personal, Work, Career) and, inside each module, a row of section
  tabs stacked directly above it (Finance: Dashboard, Accounts, Activity, Plan; Personal: Timeline, Notes, Habits, Plans,
  Moments; Work: Today, Tasks, Leave, Records, Companies; Career: Learning, Certificates, Job hunt, Resumes, Workspace)
- Main actions are pinned at the bottom (`ActionBar`: Save and its error message) or float bottom-right (Add entry, New note...)
- Frequent actions use icons (settings, lock, edit, archive, delete, restore, sparkle); rarely used or ambiguous actions keep a
  text label next to the icon
- Archived items are hidden by default and reached through an "Archived" button
- Permanent deletion always asks for the word DELETE
- The module bar steps aside while the keyboard is open so the Save bar sits on the keyboard
- `LocalBottomInsetHandled` tells screens inside the shell not to pad for the system navigation bar twice
