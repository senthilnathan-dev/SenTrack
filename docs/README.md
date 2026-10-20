# SenTrack documentation index

Developer documentation, grouped by purpose. The files in `legal/` are different: they are the words users read, shared
by the app and the published web pages.

## Foundations
| Doc | What it covers |
|---|---|
| [00-project-decisions](00-project-decisions.md) | Scope, fixed decisions (and where the build differs from the original plan), phase status |
| [01-architecture](01-architecture.md) | Modules, layers, dependency rules, data flow, state and threading |
| [02-security-model](02-security-model.md) | Threat model, keys, encryption, what is stored outside the encrypted database |
| [15-data-model](15-data-model.md) | Schema versions, tables by feature, migration chain and rules |
| [adr/](adr) | Architecture decision records |

## Cross-cutting features
| Doc | What it covers |
|---|---|
| [03-backup-and-sync](03-backup-and-sync.md) | Encrypted snapshot backup to Google Drive, schedules, restore |
| [04-ui-guidelines](04-ui-guidelines.md) | Shared UI kit, one-handed layout rules, plain-language rules |
| [06-reminders](06-reminders.md) | Reminders engine that works while locked; which features create reminders |
| [11-ai](11-ai.md) | Cloud and on-device AI, answer styles, safety checks, where the sparkle button appears |

## Feature areas
| Doc | What it covers |
|---|---|
| [05-finance-design](05-finance-design.md) | Accounts, entries, investments, dashboard, budgets, goals, tax vault, repeating entries |
| [07-personal-notes](07-personal-notes.md) | Notes and editor, habits, plans, moments, timeline |
| [08-work-company](08-work-company.md) | Companies, punch in/out, leave, tasks, records |
| [09-work-career](09-work-career.md) | Learning, certificates, job hunt, resumes, workspace, checklists |

## Quality, release and legal
| Doc | What it covers |
|---|---|
| [12-security-review](12-security-review.md) | Findings and open items from the hardening pass |
| [13-release-checklist](13-release-checklist.md) | Versioning, signing, release steps, Play Console setup |
| [14-store-listing](14-store-listing.md) | Store text, content and Data safety answers, graphics |
| [10-legal-about](10-legal-about.md) | How privacy policy, terms, user guide and changelog are produced and tested |
| [legal/](legal) | Source text: `privacy.txt`, `terms.txt`, `manual.txt`, `changelog.txt` |
| [site/](site) | Generated web pages (do not edit; run `tools/make-legal-pages.sh`) |

## Keeping docs accurate
- A change that adds a module, table, permission, network service or user-visible feature updates the matching doc in the
  same commit.
- Docs say what is **built**. Plans belong in the phase table of `00-project-decisions.md` or in an issue, not in feature docs.
- Unit tests enforce a few doc rules (version in changelog, generated pages current, privacy policy covers every
  permission and network service). See `10-legal-about.md`.
