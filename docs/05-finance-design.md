# Finance Design (Phase 3)

## Concepts
- **Account** = any place money sits: bank, cash, wallet, PF, or one specific investment (each mutual fund, stock, gold holding, FD, RD).
- **Transaction** types: INCOME, EXPENSE, TRANSFER (liquid to liquid), INVEST (liquid or outside source to an investment), WITHDRAW (investment to liquid).
- **Investments**: each has principal invested (opening + INVEST), principal returned and cash received from WITHDRAWs. Realised gain = received - principal returned, entered per withdrawal for a chosen investment. No live prices, so investments are valued at cost.
- **PF** and similar contributions that never pass through a tracked bank account use INVEST with no source account.
- **Budgets**: monthly limit per expense category. **Goals**: target amount and optional date, progress = sum of linked accounts (any kind).

## Data rules
- Money is `Long` paise; dates are epoch days; ids are UUID strings (built-in categories have fixed ids)
- Accounts are archived, never deleted; transactions can be edited or deleted with confirmation
- All Finance tables live in the encrypted database (schema v2, auto-migrated from v1; schemas exported in `core-data/schemas`)

## Modules
- `core-data/finance`: entities, DAO, `FinanceRepository`
- `feature-finance`: `FinanceCalculator` (pure, unit-tested), `Money`, screens
- `core-ui`: shared UI kit

## Build steps
A) Accounts and shell (done) -> B) transactions -> C) categories and dashboard -> D) budgets and goals
