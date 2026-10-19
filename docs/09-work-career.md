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
job-hunt statistics, learning reminders.

Migration: versions 9 and 10 were never built, so the chain is `8 -> 11`.

## Workspace (schema v12)

The fifth Career section lists the apps and websites you use for work. Each belongs to a company or to "Personal".

* **Launching**: tapping an item opens the app (or the site in the browser) and SenTrack counts the launch and when;
  the list is ordered by use. SenTrack only knows opens made from its own list, which is the "usage tracking" it can do
  honestly without the intrusive usage-access permission. The app grace period (`beginExternalFlow`) stops the vault
  locking while you are away on purpose.
* **Choosing an app**: the picker lists apps with a launcher icon, using a `<queries>` entry in the manifest instead of the
  Play-restricted `QUERY_ALL_PACKAGES` permission.
* **Notes** carry hints like which login to use. Never store passwords there.
* **Checklists**: per company, a *Joining* and a *Leaving* list, seeded from a suggested template you can edit. The
  leaving page also lists the company's workspace items so you can archive them in one go.

Migration: versions 9 to 11 were never built, so the chain is `8 -> 12`.
