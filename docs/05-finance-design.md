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
- All Finance tables live in the encrypted database (schema v2, auto-migrated from v1; schemas exported in `core-data/schemas`)

## Modules
- `core-data/finance`: entities, DAO, `FinanceRepository`
- `feature-finance`: `FinanceCalculator` + `FinanceInsights` (pure, unit-tested), `Money`, charts, screens
- `core-ui`: shared UI kit

## Plan tab (done)
Budgets: monthly limit per expense category with progress (green, amber from 80%, red when over), unbudgeted-spend hint, dashboard card. Goals: target amount and optional date, linked to any accounts (bank, cash, wallet, FD, RD, gold...), progress from their balances/principal, monthly amount needed to reach the date, archive/restore.

## Tax filing vault (done)
Plan tab -> Tax filings: one record per financial year (status, filing date, acknowledgement number, tax paid, refund, note) with encrypted documents (Form 16, AIS/26AS, ITR, acknowledgement, computation, proofs, notices). Import via the system file picker straight into the encrypted file vault; names and details live in the encrypted database. In-app viewer (PDF pages, zoomable images) with a short-lived decrypted cache copy deleted on close and on lock; explicit "save a readable copy" export; delete needs typing DELETE. Documents are included in encrypted Drive backups (schema v3).

## Still to build
Recurring entries (monthly PF/SIP) and reminders (Phase 7), optional current value per investment, overspending alerts (Phase 7).
