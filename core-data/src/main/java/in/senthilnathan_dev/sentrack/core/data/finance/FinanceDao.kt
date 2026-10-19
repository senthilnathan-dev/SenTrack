package `in`.senthilnathan_dev.sentrack.core.data.finance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    // ---- Accounts
    @Query("SELECT * FROM fin_account ORDER BY archived, sortOrder, name COLLATE NOCASE")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM fin_account WHERE id = :id")
    suspend fun account(id: String): AccountEntity?

    @Upsert
    suspend fun upsertAccount(account: AccountEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM fin_account")
    suspend fun maxAccountOrder(): Int

    // ---- Categories
    @Query("SELECT * FROM fin_category ORDER BY type, archived, sortOrder, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM fin_category")
    suspend fun categoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    // ---- Transactions
    @Query("SELECT * FROM fin_transaction ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM fin_transaction WHERE id = :id")
    suspend fun transaction(id: String): TransactionEntity?

    @Upsert
    suspend fun upsertTransaction(tx: TransactionEntity)

    @Query("DELETE FROM fin_transaction WHERE id = :id")
    suspend fun deleteTransaction(id: String)

    // ---- Budgets
    @Query("SELECT * FROM fin_budget")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM fin_budget WHERE categoryId = :categoryId")
    suspend fun deleteBudget(categoryId: String)

    // ---- Goals
    @Query("SELECT * FROM fin_goal ORDER BY archived, createdAtMillis")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM fin_goal_account")
    fun observeGoalAccounts(): Flow<List<GoalAccountEntity>>

    @Upsert
    suspend fun upsertGoal(goal: GoalEntity)

    @Query("DELETE FROM fin_goal_account WHERE goalId = :goalId")
    suspend fun clearGoalAccounts(goalId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalAccounts(links: List<GoalAccountEntity>)
}
