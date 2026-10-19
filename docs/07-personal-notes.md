# Personal: Notes (Phase 4, step 1)

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

## Coming next in Personal
Habits, plans / goals / wishlist / bucket list, achievements, important dates (using the reminders engine), and the unified timeline.
