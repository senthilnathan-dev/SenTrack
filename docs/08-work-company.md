# Work: company (first slice, schema v9)

The Work tab has three sections: **Today | Leave | Companies**.

* **Companies** hang everything off an employer so job changes never mix records. "I have left this company" records
  the last day and tucks the company into "Companies I have left" (archive-not-delete). Permanent deletion needs the
  company name typed and erases its attendance and leave.
* **Today**: Punch in / Punch out (several punches a day are allowed). Location is read once, at the tap, through
  `LocationManager.getCurrentLocation` (no background tracking, no extra library) and only if the user allowed it; a
  "no" or no fix never blocks the punch. Coordinates stay in the encrypted database.
* **Leave**: each company has its own leave year (January or April start) and its own leave types with yearly quotas.
  Working days are Monday to Friday; a half day counts 0.5; leave straddling two leave years is split by year
  (`LeaveMath`, unit-tested). A month calendar shades leave days.

Also in this module: **Tasks** (open/done, priority, due date, completed list), **Records** (appreciation with encrypted proof attachments, and permissions with free-text categories that remember what you used), and a **Today at a glance** summary of open, due and overdue tasks. Not yet built: company holidays, leave carry-forward, attendance reports/export, appreciation reminders for appraisal time.
Schema (version 10, auto-migrated from 8): `company`, `leave_type`, `leave_entry`, `attendance`, `work_task`, `appreciation`, `work_permission`, `work_attachment`.
