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

Not yet built in this module: permission/late-arrival categories, appreciation records, tasks and the daily summary,
company holidays, carry-forward of unused leave, attendance reports/export.

Schema: `company`, `leave_type`, `leave_entry`, `attendance` (version 9, auto-migrated from 8).
