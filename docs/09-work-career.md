# Work: career (schema v11)

A top-level **Career** tab (separate from the employer-bound Work tab, because a career outlives any one company) with
four sections: **Learning | Certificates | Job hunt | Resumes**.

* **Learning**: courses, books and skills with status (planned, learning, done), a progress slider and an optional
  target date.
* **Certificates**: name, issuer, credential id, earned and optional expiry date, certificate files attached
  (encrypted vault). A reminder (id `cert-<id>`) rings 30 days before expiry at 9 am. Expired or soon-to-expire ones
  show in red.
* **Job hunt**: applications move through Wishlist, Applied, Screening, Interview, Offer, and end as Accepted, Rejected
  or Withdrawn. Each can name the resume version sent and has interview rounds; a reminder (id `interview-<id>`) rings an
  hour before each. A reply rate is shown ("x% have had a reply").
* **Resumes**: each version is an encrypted file with a label and note; the editor lists the applications that used it.
  Deleting a version leaves those applications intact but without a resume link.

Archive-not-delete everywhere (an "Archived (n)" button reveals them); permanent deletion needs the word DELETE typed.

Pure logic (`CareerMath`, `JobStatus`) is unit-tested. Not yet built: the workspace of apps/sites with usage tracking and
onboarding/offboarding checklists, job-hunt statistics, learning reminders.

Migration: versions 9 and 10 were never built, so the chain is `8 -> 11`.
