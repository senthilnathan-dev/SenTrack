package `in`.senthilnathan_dev.sentrack.core.data.finance

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Finance schema. Conventions:
 *  - Money is stored in paise (1 rupee = 100 paise) as Long, never as floating point.
 *  - Dates are stored as epoch-day numbers (LocalDate.toEpochDay), so time zones never shift an entry's date.
 *  - Ids are random UUID strings, so rows can be merged or restored from a backup without clashes.
 *  - Rows are archived, not deleted, wherever history matters.
 */

/** What kind of place the money sits in. Investment kinds track invested principal and realised gain. */
enum class AccountKind(val label: String, val isInvestment: Boolean) {
    BANK("Bank account", false),
    CASH("Cash", false),
    WALLET("Wallet", false),
    PF("Provident fund (PF)", true),
    MUTUAL_FUND("Mutual fund", true),
    STOCK("Stock / shares", true),
    GOLD("Gold (digital or physical)", true),
    FIXED_DEPOSIT("Fixed deposit (FD)", true),
    RECURRING_DEPOSIT("Recurring deposit (RD)", true),
    OTHER_INVESTMENT("Other investment", true),
}

/**
 * Any place money sits: a bank account, a wallet, or one specific investment (for example one mutual fund).
 * For liquid accounts [openingBalancePaise] is the balance when tracking started; for investments it is the
 * principal already invested at that point.
 */
@Entity(tableName = "fin_account")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: AccountKind,
    val openingBalancePaise: Long,
    val note: String?,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAtMillis: Long,
)

enum class CategoryType { INCOME, EXPENSE }

@Entity(tableName = "fin_category", indices = [Index("type")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: CategoryType,
    val archived: Boolean,
    val sortOrder: Int,
)

/**
 * What a transaction does. [accountId] and [toAccountId] meanings by type:
 *  - INCOME:   money arrives in [accountId]; has an income category
 *  - EXPENSE:  money leaves [accountId]; has an expense category
 *  - TRANSFER: moves between two liquid accounts, [accountId] -> [toAccountId]
 *  - INVEST:   [accountId] (liquid, or null for money that never passed through your accounts such as an employer
 *              PF contribution) -> [toAccountId] (the investment); the invested principal grows
 *  - WITHDRAW: [accountId] (the investment) -> [toAccountId] (liquid account, or null if not tracked);
 *              [amountPaise] is what you received and [principalReturnedPaise] how much of your invested money
 *              it returns; gain or loss = received minus principal returned
 */
enum class TxType { INCOME, EXPENSE, TRANSFER, INVEST, WITHDRAW }

@Entity(
    tableName = "fin_transaction",
    indices = [Index("dateEpochDay"), Index("accountId"), Index("toAccountId"), Index("categoryId")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val dateEpochDay: Long,
    val type: TxType,
    val amountPaise: Long,
    val accountId: String?,
    val toAccountId: String?,
    val categoryId: String?,
    val principalReturnedPaise: Long?,
    val note: String?,
    val createdAtMillis: Long,
)

/** Monthly spending limit for one expense category. */
@Entity(tableName = "fin_budget")
data class BudgetEntity(
    @PrimaryKey val categoryId: String,
    val monthlyLimitPaise: Long,
)

/** A savings goal; progress is counted from the accounts linked in [GoalAccountEntity]. */
@Entity(tableName = "fin_goal")
data class GoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val targetPaise: Long,
    val targetDateEpochDay: Long?,
    val archived: Boolean,
    val createdAtMillis: Long,
)

@Entity(tableName = "fin_goal_account", primaryKeys = ["goalId", "accountId"], indices = [Index("accountId")])
data class GoalAccountEntity(
    val goalId: String,
    val accountId: String,
)

// ---- Tax filing vault

enum class TaxStatus(val label: String) {
    NOT_STARTED("Not started"),
    PREPARING("Preparing"),
    FILED("Filed"),
    VERIFIED("E-verified"),
    REFUND_PENDING("Refund pending"),
    COMPLETED("Completed"),
}

enum class TaxDocCategory(val label: String) {
    FORM_16("Form 16"),
    AIS_26AS("AIS / Form 26AS"),
    ITR_FORM("ITR form"),
    ACKNOWLEDGEMENT("Acknowledgement"),
    COMPUTATION("Tax computation"),
    INVESTMENT_PROOF("Investment proof"),
    NOTICE("Notice or letter"),
    OTHER("Other"),
}

/** One financial year's tax filing: where it stands, the key numbers, and a note. Documents attach to it. */
@Entity(tableName = "fin_tax_filing", indices = [Index("financialYearStart")])
data class TaxFilingEntity(
    @PrimaryKey val id: String,
    /** Starting calendar year of the Indian financial year (2026 means FY 2026-27, assessment year 2027-28). */
    val financialYearStart: Int,
    val status: TaxStatus,
    val filedOnEpochDay: Long?,
    val acknowledgementNumber: String?,
    val taxPaidPaise: Long?,
    val refundPaise: Long?,
    val note: String?,
    val createdAtMillis: Long,
    /** Archived years stay stored but are hidden from the list until asked for. */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
)

/**
 * A stored tax document. The file itself lives encrypted in the file vault under [vaultFileId]; this row keeps the
 * display name and details, which are therefore protected by the database encryption too.
 */
@Entity(tableName = "fin_tax_document", indices = [Index("filingId")])
data class TaxDocumentEntity(
    @PrimaryKey val id: String,
    val filingId: String,
    val name: String,
    val category: TaxDocCategory,
    val mimeType: String,
    val sizeBytes: Long,
    val vaultFileId: String,
    val addedAtMillis: Long,
)
