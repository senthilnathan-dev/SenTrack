# SenTrack: Project Decisions

Personal tracker for finance, personal life, work and career. Single user, Android only. Last reviewed for **v0.5.0**.

## Decisions (as built)
- Stack: Kotlin + Jetpack Compose (Material 3), clean layering with shared `core-*` modules and `feature-*` modules, Room on
  SQLCipher. **Dependency injection is manual** (`AppContainer`), not Hilt; **ViewModels** are used where state outlives a
  screen (vault, backup), other screens keep state in Compose. Navigation is a small back stack per tab, not the Navigation library.
- Package ID: `in.senthilnathan_dev.sentrack` (immutable after publishing)
- Target: Android 16 (API 36) only; test device Samsung A34 5G over Wi-Fi debugging
- Offline-first. Optional network features: Google Drive `appDataFolder` backup (manual and scheduled), cloud AI with the user's
  own key, and a one-off AI model download
- Sync model: single active device, restore from an encrypted snapshot (no multi-device merge)
- Currency INR; manual entry only (no live prices, no SMS or bank reading, no spreadsheet import)
- Tax filings: documents stored encrypted and not parsed
- Notes: formatted text stored as lightweight Markdown, with attachments, folders and tags
- Punch location captured only when the user taps, only with permission, never in the background
- Workspace: apps and sites chosen by hand; usage is counted only for opens made from inside SenTrack (no usage-access permission)
- Recovery: a recovery code held by the user; the key never leaves the device unencrypted
- **Deleting**: everything is archive-first. Permanent deletion needs the word DELETE typed (the company name for a company)
  and also erases attachments and reminders. *Not built from the original plan:* biometric or passphrase re-authentication
  and a 24-hour cooling-off before hard delete.
- AI: off by default. Two optional engines: an on-device open model (set up with one tap, tested on the phone before use) and
  cloud providers (Gemini, OpenAI, Claude) with the user's own key. Cloud requests show the exact text and need confirmation.
  *Differs from the original plan:* the on-device model is optional rather than the default, because it must be downloaded
  and tested per phone.
- UI: light, dark or system theme; English only

## Phase status
| Phase | Scope | Status |
|---|---|---|
| 0 | Setup | Done |
| 1 | Security core (vault, lock, biometric, file vault) | Done |
| 2 | Drive backup and schedules | Done |
| 3 | Finance (accounts, dashboard, budgets, goals, tax vault, repeating entries) | Done |
| 4 | Personal (notes, habits, plans, moments, timeline) | Done |
| 5 | Work (companies, attendance, leave, tasks, records) | Done (first version) |
| 6 | Career (learning, certificates, job hunt, resumes, workspace) | Done (first version) |
| 7 | Reminders engine | Done |
| 8 | AI (cloud, on-device, assist in every module) | Done |
| 9 | About, privacy policy, terms, user guide, changelog | Done |
| 10 | Hardening | In progress (see [12-security-review](12-security-review.md)) |
| 11 | Release | 0.5.0 to internal testing, then 1.0.0 public, then 1.x ([13-release-checklist](13-release-checklist.md)) |

## Known gaps, candidates for 1.x
Company holidays and leave carry-forward, attendance export, habit milestones on the Timeline, home-screen widgets, a
failure notification for scheduled backups, keeping the AI model resident longer, AI questions over the user's own data,
generated open-source licence notices, biometric re-authentication for permanent deletion.

## Tool locations (all on D:)
- Android Studio: `D:\Android\Studio`
- Android SDK: `D:\Android\Sdk`
- Gradle cache: `D:\Android\.gradle` (env `GRADLE_USER_HOME`)
- Project: `D:\Projects\SenTrack`
