# Personal: notes, habits, plans, moments and timeline (Phase 4)

## What it does
- Formatted notes: headings, bold, italic, strikethrough, code, bullet / numbered / check lists, quotes. Written with a toolbar over plain text; the reading view renders it and check boxes can be ticked there
- Folders (flat, renameable, archivable) and tags; search across titles, text, tags and folder names; pinned notes first
- Attachments: photos, videos, PDFs, any file. Encrypted in the file vault; previews for PDF, images (pinch-zoom) and video; "save a readable copy" is the only way a plain copy leaves the app
- Autosave: a moment after you stop typing, when you leave, and before attaching a file. No Save button
- Archive (hidden behind an Archived notes button) instead of delete; permanent delete needs typing DELETE and removes attachments too

## Storage
Schema v6: `note`, `note_folder`, `note_tag`, `note_tag_link`, `note_attachment`. The body is stored as lightweight Markdown text (portable, searchable, backup-friendly).

## Shared pieces added
- `core-attachments`: `AttachmentService` (import, export, delete, temporary decrypted copies) and `AttachmentViewerScreen` (PDF, image, video). Finance tax documents now use the same module
- `core-ui` pickers (`PickerField`, `DateField`, `TimeField`)


## Editor (second iteration)
- Borderless page with a compact header; large title; folder and tag chips under it
- Live formatting while typing: marks fade, bold is bold, headings are large, quotes italic (text unchanged, so cursor positions stay predictable)
- Toolbar docked above the keyboard: undo, redo, bold, italic, strikethrough, heading, bullet, check list, numbered list, quote, attach
- Undo/redo: typing in quick succession is one step; toolbar actions are their own steps (`EditHistory`, tested)
- Lists continue on Enter and end on an empty item (`EditAssist.continueList`, tested)
- Word suggestions from the user's own notes, shown above the toolbar; computed on the phone only (`EditAssist`, tested)
- Standard keyboard options (sentence capitals, autocorrect). Note: some keyboards suppress their own suggestions in windows that block screenshots (`FLAG_SECURE`, which release builds set everywhere); the in-app suggestions cover that

## Habits (step 2)
- Periods: daily (optionally only chosen weekdays), weekly, monthly, quarterly, yearly. Goal: done / not done N times per period, or a number (glasses, pages, km) per period
- Today list with progress bars, one-tap tick, streak flame; step back to fill in a missed day
- Detail: current and best streak, success rate over recent finished periods, periods met, a 12-week calendar (tap a day to change it) or per-period bars
- Streak rules: the period still in progress never breaks a streak; unscheduled days are skipped; tracking starts at the habit's start date (`HabitEngine`, tested)
- Reminders for daily and weekly habits through the shared reminders engine (reminder id `habit-<id>`); archiving pauses it, restoring resumes it
- Schema v7: `habit`, `habit_log` (one row per habit per day)

## Plans and Moments (schema v8)

The Personal tab now has five sections along the bottom: **Timeline | Notes | Habits | Plans | Moments**.

### Plans
One table holds goals, plans, wishlist items and bucket-list items (`plan_item.kind`). Each has a status
(to do / in progress / done), a priority, an optional target date and cost, and an ordered list of **steps**
(`plan_step`). Progress is the share of steps ticked; with no steps it follows the status. A target date can raise a
one-off 9 am reminder (reminder id `item-<id>`). Ticking the circle on a card marks the item done and records the date;
done items feed the timeline.

### Moments
* **Achievements**: title, story, free category (Career, Education...), date, attachments (certificate, photo, mail).
* **Important dates**: birthdays, anniversaries, events. A yearly date repeats and shows "Turns 36" or "5th
  anniversary"; the list is sorted by how soon each comes round (`DateMath.nextOccurrence`, 29 Feb falls on 28 Feb in
  other years). The reminder (id `date-<id>`) fires N days before at a chosen time: yearly dates use a yearly schedule,
  one-offs ring once.

### Shared behaviour
* Archive-not-delete: archived items are hidden behind an "Archived ..." button; permanent deletion needs the word
  DELETE typed and also erases attachments and reminders.
* Attachments (`personal_attachment`, owner kind PLAN or ACHIEVEMENT) use the same encrypted file vault and viewer as
  notes. They can be added once the item has been saved.
* **Timeline** merges notes, achievements, important dates that have arrived and finished plan items, newest first, with
  search over titles. Habit milestones are not on it yet.

### AI in Personal
The sparkle in the note editor offers fix spelling and grammar, make clearer, make shorter, summarize, list action items and suggest a title; it works on the selected text, or the whole note when nothing is selected, and the answer is applied only when the user accepts it (replace the text, or add to the end of the note). Plans offer "Break into steps" (the steps are added to the checklist) and clearer notes; achievements offer "Write it up well", fix and clearer. See [11-ai](11-ai.md).

### Migration note
Database version 7 (habits) was never built on its own, so there is no `7.json`; the chain jumps `6 -> 8`.
