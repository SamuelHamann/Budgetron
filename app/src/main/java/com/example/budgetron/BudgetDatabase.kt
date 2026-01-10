package com.example.budgetron

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BudgetDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "budgetron.db"
        private const val DATABASE_VERSION = 2

        // Settings table
        private const val TABLE_SETTINGS = "settings"
        private const val COLUMN_ID = "id"
        private const val COLUMN_DAILY_ALLOCATION = "daily_allocation"

        // Expenses table
        private const val TABLE_EXPENSES = "expenses"
        private const val COLUMN_EXPENSE_ID = "id"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_AMOUNT = "amount"
        private const val COLUMN_CATEGORY = "category"
        private const val COLUMN_DATE = "date"

        // Monthly data table
        private const val TABLE_MONTHLY_DATA = "monthly_data"
        private const val COLUMN_MONTH = "month" // Format: YYYY-MM
        private const val COLUMN_MONTHLY_ALLOCATION = "daily_allocation"
        private const val COLUMN_LEFTOVER = "leftover_budget"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create settings table
        val createSettingsTable = """
            CREATE TABLE $TABLE_SETTINGS (
                $COLUMN_ID INTEGER PRIMARY KEY,
                $COLUMN_DAILY_ALLOCATION REAL NOT NULL
            )
        """.trimIndent()
        db.execSQL(createSettingsTable)

        // Insert default daily allocation
        val values = ContentValues().apply {
            put(COLUMN_ID, 1)
            put(COLUMN_DAILY_ALLOCATION, 50.0)
        }
        db.insert(TABLE_SETTINGS, null, values)

        // Create expenses table
        val createExpensesTable = """
            CREATE TABLE $TABLE_EXPENSES (
                $COLUMN_EXPENSE_ID TEXT PRIMARY KEY,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_AMOUNT REAL NOT NULL,
                $COLUMN_CATEGORY TEXT NOT NULL,
                $COLUMN_DATE TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createExpensesTable)

        // Create monthly data table
        val createMonthlyDataTable = """
            CREATE TABLE $TABLE_MONTHLY_DATA (
                $COLUMN_MONTH TEXT PRIMARY KEY,
                $COLUMN_MONTHLY_ALLOCATION REAL NOT NULL,
                $COLUMN_LEFTOVER REAL NOT NULL
            )
        """.trimIndent()
        db.execSQL(createMonthlyDataTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Create monthly data table
            val createMonthlyDataTable = """
                CREATE TABLE $TABLE_MONTHLY_DATA (
                    $COLUMN_MONTH TEXT PRIMARY KEY,
                    $COLUMN_MONTHLY_ALLOCATION REAL NOT NULL,
                    $COLUMN_LEFTOVER REAL NOT NULL
                )
            """.trimIndent()
            db.execSQL(createMonthlyDataTable)
        }
    }

    // Get daily allocation
    fun getDailyAllocation(): Double {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SETTINGS,
            arrayOf(COLUMN_DAILY_ALLOCATION),
            "$COLUMN_ID = ?",
            arrayOf("1"),
            null,
            null,
            null
        )

        var allocation = 50.0
        if (cursor.moveToFirst()) {
            allocation = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_DAILY_ALLOCATION))
        }
        cursor.close()
        return allocation
    }

    // Update daily allocation
    fun updateDailyAllocation(allocation: Double): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DAILY_ALLOCATION, allocation)
        }

        val rowsAffected = db.update(
            TABLE_SETTINGS,
            values,
            "$COLUMN_ID = ?",
            arrayOf("1")
        )
        return rowsAffected > 0
    }

    // Add expense
    fun addExpense(expense: Expense): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_EXPENSE_ID, expense.id)
            put(COLUMN_NAME, expense.name)
            put(COLUMN_AMOUNT, expense.amount)
            put(COLUMN_CATEGORY, expense.category)
            put(COLUMN_DATE, expense.date.toString())
        }

        val result = db.insert(TABLE_EXPENSES, null, values)
        return result != -1L
    }

    // Update expense
    fun updateExpense(expense: Expense): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, expense.name)
            put(COLUMN_AMOUNT, expense.amount)
            put(COLUMN_CATEGORY, expense.category)
            put(COLUMN_DATE, expense.date.toString())
        }

        val rowsAffected = db.update(
            TABLE_EXPENSES,
            values,
            "$COLUMN_EXPENSE_ID = ?",
            arrayOf(expense.id)
        )
        return rowsAffected > 0
    }

    // Get all expenses
    fun getAllExpenses(): List<Expense> {
        val expenses = mutableListOf<Expense>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_EXPENSES,
            arrayOf(COLUMN_EXPENSE_ID, COLUMN_NAME, COLUMN_AMOUNT, COLUMN_CATEGORY, COLUMN_DATE),
            null,
            null,
            null,
            null,
            "$COLUMN_DATE DESC"
        )

        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
            val amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT))
            val category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY))
            val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE))
            val date = java.time.LocalDate.parse(dateStr)

            expenses.add(Expense(id, name, amount, category, date))
        }
        cursor.close()
        return expenses
    }

    // Delete expense
    fun deleteExpense(expenseId: String): Boolean {
        val db = writableDatabase
        val rowsDeleted = db.delete(
            TABLE_EXPENSES,
            "$COLUMN_EXPENSE_ID = ?",
            arrayOf(expenseId)
        )
        return rowsDeleted > 0
    }

    // Get monthly data for a specific month
    fun getMonthlyData(yearMonth: String): MonthlyData? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MONTHLY_DATA,
            arrayOf(COLUMN_MONTH, COLUMN_MONTHLY_ALLOCATION, COLUMN_LEFTOVER),
            "$COLUMN_MONTH = ?",
            arrayOf(yearMonth),
            null,
            null,
            null
        )

        var monthlyData: MonthlyData? = null
        if (cursor.moveToFirst()) {
            val month = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MONTH))
            val allocation = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_MONTHLY_ALLOCATION))
            val leftover = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_LEFTOVER))
            monthlyData = MonthlyData(month, allocation, leftover)
        }
        cursor.close()
        return monthlyData
    }

    // Save or update monthly data
    fun saveMonthlyData(yearMonth: String, dailyAllocation: Double, leftoverBudget: Double): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MONTH, yearMonth)
            put(COLUMN_MONTHLY_ALLOCATION, dailyAllocation)
            put(COLUMN_LEFTOVER, leftoverBudget)
        }

        val result = db.insertWithOnConflict(
            TABLE_MONTHLY_DATA,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return result != -1L
    }

    // Get expenses for a specific month
    fun getExpensesForMonth(yearMonth: String): List<Expense> {
        val expenses = mutableListOf<Expense>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_EXPENSES,
            arrayOf(COLUMN_EXPENSE_ID, COLUMN_NAME, COLUMN_AMOUNT, COLUMN_CATEGORY, COLUMN_DATE),
            "$COLUMN_DATE LIKE ?",
            arrayOf("$yearMonth%"),
            null,
            null,
            "$COLUMN_DATE DESC"
        )

        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXPENSE_ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
            val amount = cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AMOUNT))
            val category = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY))
            val dateStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE))
            val date = java.time.LocalDate.parse(dateStr)

            expenses.add(Expense(id, name, amount, category, date))
        }
        cursor.close()
        return expenses
    }
}

data class MonthlyData(
    val month: String,
    val dailyAllocation: Double,
    val leftoverBudget: Double
)

