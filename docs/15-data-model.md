# Data model and migrations

One encrypted Room database (`SenTrackDatabase`, SQLCipher) holds everything. Entities live in `core-data` grouped by feature.
Money is `Long` paise, dates are epoch days, timestamps are epoch milliseconds, ids are UUID strings.

## Schema versions
Current version: **13**. Exported schemas are committed in `core-data/schemas/in.senthilnathan_dev.sentrack.core.data.SenTrackDatabase/`.

| Version | What was added | Exported file |
|---|---|---|
| 1 | `meta` (key/value settings) | `1.json` |
| 2 | Finance: accounts, categories, transactions, budgets, goals | `2.json` |
| 3 | Tax filings and tax documents | `3.json` |
| 4 | Tax-year archive flag | none (never shipped alone; folded into 3 to 5) |
| 5 | Reminders | `5.json` |
| 6 | Notes: folders, notes, tags, tag links, attachments | `6.json` |
| 7 | Habits and habit logs | none (never shipped alone; folded into 6 to 8) |
| 8 | Plans and steps, achievements, important dates, personal attachments | `8.json` |
| 9 to 11 | Work and Career pieces developed step by step | none (never built on their own; folded into 8 to 12) |
| 12 | Work (company, leave, attendance, tasks, appreciation, permissions, work attachments) and Career (learning, certifications, job applications, interviews, resumes, career attachments, workspace items, checklists) | `12.json` |
| 13 | Repeating finance entries (`fin_recurring`) | `13.json` |

**Migration chain** (all Room auto-migrations): `1 -> 2 -> 3 -> 5 -> 6 -> 8 -> 12 -> 13`. A version number that was never built on a
phone has no schema file, so the chain skips it. This is safe only because those versions never shipped; from 0.5.0 on, every
released version must keep its schema file and every step must be tested.

## Tables by area
| Area | Tables | Package |
|---|---|---|
| Settings | `meta` | `core-data` |
| Finance | `fin_account`, `fin_category`, `fin_transaction`, `fin_budget`, `fin_goal`, `fin_goal_account`, `fin_tax_filing`, `fin_tax_document`, `fin_recurring` | `core-data/finance` |
| Reminders | `reminder` | `core-data/reminders` |
| Notes | `note`, `note_folder`, `note_tag`, `note_tag_link`, `note_attachment` | `core-data/personal` |
| Habits | `habit`, `habit_log` | `core-data/personal` |
| Plans and moments | `plan_item`, `plan_step`, `achievement`, `important_date`, `personal_attachment` | `core-data/personal` |
| Work | `company`, `leave_type`, `leave_entry`, `attendance`, `work_task`, `appreciation`, `work_permission`, `work_attachment` | `core-data/work` |
| Career | `learning_item`, `certification`, `job_application`, `interview`, `resume`, `career_attachment`, `workspace_item`, `checklist_item` | `core-data/career` |

Attachment tables hold only metadata (name, type, size, vault file id); the file itself is in the encrypted file vault.

## The `meta` table
Key/value rows used by `AiSettingsStore`: `ai.enabled`, `ai.provider`, `ai.model.<PROVIDER>`, `ai.key.<PROVIDER>` (the API key,
encrypted with the rest of the database), `ai.style`, `ai.local.enabled`, `ai.local.model`, `ai.local.verdict`,
`ai.local.summary`. New settings that are private belong here; settings needed before unlock (auto-lock, backup schedule) live in
app-private preferences instead (see [02-security-model](02-security-model.md)).

## Rules for changing the schema
1. Change the entity, bump `version` in `SenTrackDatabase`, add an `AutoMigration(from, to)` (or a hand-written migration when
   Room cannot infer it).
2. Build once: Room writes the new `N.json` to `core-data/schemas`. **Commit it.** The next migration needs it.
3. Add or extend a test in `core-data/src/androidTest/.../MigrationTest.kt` (create the old schema, insert a row, migrate, check
   the row and the new tables) and run `./gradlew :core-data:connectedDebugAndroidTest` on a phone.
4. Never edit or delete a released schema file. Never reuse a version number.
5. Update this document and the matching feature doc, and `docs/legal/privacy.txt` if the new data changes what is stored.
