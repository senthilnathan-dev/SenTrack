package `in`.senthilnathan_dev.sentrack.core.data.finance

import `in`.senthilnathan_dev.sentrack.core.data.DatabaseHolder
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The single entry point for Finance data. Screens never touch Room directly.
 *
 * The encrypted database only exists while the vault is unlocked, so every call fetches the current database
 * from [DatabaseHolder]. Call the `observe` functions from composition (they stop when the screen leaves),
 * not from long-lived objects that could outlive an unlock.
 */
class FinanceRepository(private val holder: DatabaseHolder) {
    private fun dao() = holder.get().financeDao()

    fun accounts(): Flow<List<AccountEntity>> = dao().observeAccounts()
    fun categories(): Flow<List<CategoryEntity>> = dao().observeCategories()
    fun transactions(): Flow<List<TransactionEntity>> = dao().observeTransactions()
    fun budgets(): Flow<List<BudgetEntity>> = dao().observeBudgets()
    fun goals(): Flow<List<GoalEntity>> = dao().observeGoals()
    fun goalAccounts(): Flow<List<GoalAccountEntity>> = dao().observeGoalAccounts()

    /** Creates the built-in categories the first time Finance is opened. Safe to call repeatedly. */
    suspend fun ensureDefaults() {
        val dao = dao()
        if (dao.categoryCount() == 0) dao.insertCategories(DefaultCategories.all())
    }

    // ---- Accounts

    suspend fun addAccount(name: String, kind: AccountKind, openingBalancePaise: Long, note: String?): AccountEntity {
        val dao = dao()
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            kind = kind,
            openingBalancePaise = openingBalancePaise,
            note = note?.trim()?.ifEmpty { null },
            archived = false,
            sortOrder = dao.maxAccountOrder() + 1,
            createdAtMillis = System.currentTimeMillis(),
        )
        dao.upsertAccount(account)
        return account
    }

    suspend fun updateAccount(account: AccountEntity) = dao().upsertAccount(account.copy(name = account.name.trim()))

    suspend fun account(id: String): AccountEntity? = dao().account(id)

    /** Archived accounts keep their history but disappear from pickers and totals of active accounts. */
    suspend fun setAccountArchived(id: String, archived: Boolean) {
        val dao = dao()
        dao.account(id)?.let { dao.upsertAccount(it.copy(archived = archived)) }
    }

    // ---- Transactions

    suspend fun saveTransaction(tx: TransactionEntity) = dao().upsertTransaction(tx)

    suspend fun transaction(id: String): TransactionEntity? = dao().transaction(id)

    suspend fun deleteTransaction(id: String) = dao().deleteTransaction(id)

    // ---- Categories, budgets, goals

    suspend fun saveCategory(category: CategoryEntity) = dao().upsertCategory(category)

    suspend fun addCategory(name: String, type: CategoryType): CategoryEntity {
        val category = CategoryEntity(UUID.randomUUID().toString(), name.trim(), type, archived = false, sortOrder = 1000)
        dao().upsertCategory(category)
        return category
    }

    suspend fun setBudget(categoryId: String, monthlyLimitPaise: Long) {
        if (monthlyLimitPaise <= 0) dao().deleteBudget(categoryId) else dao().upsertBudget(BudgetEntity(categoryId, monthlyLimitPaise))
    }

    // ---- Tax filing vault

    fun taxFilings(): Flow<List<TaxFilingEntity>> = dao().observeTaxFilings()
    fun taxDocuments(): Flow<List<TaxDocumentEntity>> = dao().observeTaxDocuments()

    suspend fun saveTaxFiling(filing: TaxFilingEntity) = dao().upsertTaxFiling(filing)

    suspend fun saveTaxDocument(document: TaxDocumentEntity) = dao().upsertTaxDocument(document)

    suspend fun taxDocument(id: String): TaxDocumentEntity? = dao().taxDocument(id)

    /** Removes the document's record. The caller deletes the encrypted file from the vault afterwards. */
    suspend fun deleteTaxDocument(id: String) = dao().deleteTaxDocument(id)

    suspend fun saveGoal(goal: GoalEntity, accountIds: Set<String>) {
        val dao = dao()
        dao.upsertGoal(goal)
        dao.clearGoalAccounts(goal.id)
        dao.insertGoalAccounts(accountIds.map { GoalAccountEntity(goal.id, it) })
    }
}

/** Categories created on first use. Fixed ids keep restores and re-seeding from creating duplicates. */
object DefaultCategories {
    fun all(): List<CategoryEntity> {
        val income = listOf("Salary", "Bonus", "Interest", "Refund", "Other income")
        val expense = listOf(
            "Rent", "Food and groceries", "Eating out", "Utilities and bills", "Transport", "Health", "Insurance",
            "EMI and loans", "Shopping", "Entertainment", "Education", "Travel", "Gifts and donations", "Other",
        )
        return income.mapIndexed { i, n -> CategoryEntity(idFor("income", n), n, CategoryType.INCOME, false, i) } +
            expense.mapIndexed { i, n -> CategoryEntity(idFor("expense", n), n, CategoryType.EXPENSE, false, i) }
    }

    private fun idFor(type: String, name: String) = "builtin-$type-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
