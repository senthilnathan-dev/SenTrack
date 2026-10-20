# Work: company (schema v12)

The Work tab has five sections along the bottom: **Today | Tasks | Leave | Records | Companies**. A company picker at the top
of the first four sections appears when more than one company is active.

* **Companies** hang everything off an employer so job changes never mix records. "I have left this company" records the
  last day and tucks the company into "Companies I have left" (archive-not-delete). Permanent deletion needs the company name
  typed and erases its attendance, leave, tasks, permissions, appreciation records and their proof files.
* **Today**: Punch in / Punch out (several punches a day are allowed). Location is read once, at the tap, through
  `LocationManager.getCurrentLocation` (no background tracking, no extra library) and only if the user allowed it; a "no" or no fix
  never blocks the punch. Coordinates stay in the encrypted database. Below it, **Today at a glance** shows open, due and overdue
  tasks, today's permissions and finished tasks, and offers "Plan my day" (AI).
* **Tasks**: open and done, priority, due date, a completed list; AI can break a task into steps or tidy its details.
* **Leave**: each company has its own leave year (January or April start) and its own leave types with yearly quotas. Working days
  are Monday to Friday; a half day counts 0.5; leave straddling two leave years is split by year (`LeaveMath`, unit-tested).
  A month calendar shades leave days.
* **Records**: appreciation (title, from, date, what they said, with encrypted proof attachments) and permissions (free-text
  category that remembers what you used, date, minutes, reason).

## Data
Tables: `company`, `leave_type`, `leave_entry`, `attendance`, `work_task`, `appreciation`, `work_permission`, `work_attachment`
(all created in schema v12; see [15-data-model](15-data-model.md)). Code: `core-data/work` (entities, DAO, `WorkRepository`),
`feature-work` (screens, `LeaveMath`, `TaskMath`, `Locations`).

## Not built
Company holidays, leave carry-forward, Saturday-working calendars, attendance reports and export, appraisal-time reminders.
