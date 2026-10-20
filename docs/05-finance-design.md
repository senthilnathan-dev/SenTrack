# Finance Design (Phase 3)

## Concepts
- **Account** = any place money sits: bank, cash, wallet, PF, or one specific investment (each mutual fund, stock, gold holding, FD, RD).
- **Entry (transaction)** types: INCOME, EXPENSE, TRANSFER (spendable to spendable), INVEST (spendable or outside source to an investment), WITHDRAW (investment to spendable).
- **Investments**: principal invested = opening + INVEST; each WITHDRAW records the amount received and how much invested principal it returns; realised gain or loss = received - principal returned, per investment. No live prices, so investments are valued at cost.
- **PF** and similar contributions that never pass through a tracked bank account use INVEST with source "outside my accounts".
- **Split money**: one salary can be shared across several investments in one go (each row becomes an INVEST or TRANSFER entry).
- **Net worth** = spendable balances + principal still invested (at cost); archived accounts excluded. Trend = net worth at each month end, rebuilt from entries.

## Dashboard (landing page)
Net worth + 6-month trend; month selector with income, spent, invested, left-after-spending and savings rate (each vs previous month); top spending categories; 6-month cash flow chart; investment allocation by kind with realised gains (all time and financial year); financial-year (April to March) totals for tax time.

## Data rules
- Money is `Long` paise; dates are epoch days; ids are UUID strings (built-in categories have fixed ids)
- Accounts and categories are archived, never deleted; entries can be edited or deleted with confirmation
- All Finance tables live in the encrypted database (created in schema v2, tax tables in v3, repeating entries in v13; see [15-data-model](15-data-model.md))

## Modules
- `core-data/finance`: entities, DAO, `FinanceRepository`
- `feature-finance`: `FinanceCalculator` + `FinanceInsights` (pure, unit-tested), `Money`, charts, screens
- `core-ui`: shared UI kit

## Plan tab (done)
Budgets: monthly limit per expense category with progress (green, amber from 80%, red when over), unbudgeted-spend hint, dashboard card. Goals: target amount and optional date, linked to any accounts (bank, cash, wallet, FD, RD, gold...), progress from their balances/principal, monthly amount needed to reach the date, archive/restore.

## Tax filing vault (done)
Plan tab -> Tax filings (archive a year to hide it; archived years live behind an "Archived tax years" button and can be restored): one record per financial year (status, filing date, acknowledgement number, tax paid, refund, note) with encrypted documents (Form 16, AIS/26AS, ITR, acknowledgement, computation, proofs, notices). Import via the system file picker straight into the encrypted file vault; names and details live in the encrypted database. In-app viewer (PDF pages, zoomable images) with a short-lived decrypted cache copy deleted on close and on lock; explicit "save a readable copy" export; delete needs typing DELETE. Documents are included in encrypted Drive backups (schema v3).


## Repeating entries (schema v13)

`fin_recurring` holds rules for things that repeat monthly: salary, rent, a SIP, an EMI (types Income, Expense, Invest,
Transfer, with the same account rules as an ordinary entry). The day of the month can be 1 to 30 or "Last day of the month"; in a month too short for it the entry falls on that month's last day (a 30th becomes 28 or 29 February). `RecurringMath` clamps each month.

**Nothing changes a balance by itself.** When a due date arrives (`RecurringMath.pendingDates`) the rule appears in a
**Due now** card on the Dashboard with *Add entry* and *Skip*. Adding creates an ordinary transaction dated on the due
day; both choices move the rule's `lastDoneEpochDay` forward. Only the oldest unanswered date per rule is shown, so a
missed month is never answered out of order, and at most the latest twelve are tracked.

Each rule can have a monthly 9 am reminder (reminder id `recurring-<id>`, a `ScheduleSpec.Monthly` for days 1 to 28 or `ScheduleSpec.MonthlyClamped` for 29 to 31), which works while the
app is locked. Rules can be paused or given an end date. Manage them in Finance -> Plan -> Repeating entries.
Unit-tested in `RecurringMathTest`.

## AI in Finance
Two sparkle actions use the shared assistant ([11-ai](11-ai.md)): "Suggest a category" in Add entry (from the note text; the answer is applied only if it matches one of your own categories), and "Explain this month" on the Dashboard month card. The second sends the model only the month's own figures as text (income, spent, invested, savings rate, top categories); every number is computed by SenTrack and the answer is checked for numbers that were not given.

## Not built
Optional current value per investment, overspending alerts, bank or SMS import (deliberately out of scope).
