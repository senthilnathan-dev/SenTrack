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

## Still to build
Budgets and goals (data model exists), tax document vault, recurring entries (monthly PF/SIP) and reminders (Phase 7), optional current value per investment.
