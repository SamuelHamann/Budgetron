package com.example.budgetron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.budgetron.ui.theme.BudgetronTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class StatsActivity : ComponentActivity() {
    private lateinit var database: BudgetDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = BudgetDatabase(this)
        enableEdgeToEdge()
        setContent {
            BudgetronTheme {
                StatsScreen(
                    database = database,
                    onBackClick = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    database: BudgetDatabase,
    onBackClick: () -> Unit
) {
    val allExpenses = remember { database.getAllExpenses() }
    val allEarnings = remember { database.getAllEarnings() }
    val allMonthlyData = remember { database.getAllMonthlyData() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Global Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // General Stats Section
            item {
                GeneralStatsSection(
                    expenses = allExpenses,
                    earnings = allEarnings,
                    monthlyData = allMonthlyData
                )
            }

            // Category Breakdown Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                CategoryBreakdownSection(expenses = allExpenses)
            }

            // Monthly Comparison Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                MonthlyComparisonSection(
                    expenses = allExpenses,
                    earnings = allEarnings,
                    monthlyData = allMonthlyData
                )
            }

            // Yearly Comparison Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                YearlyComparisonSection(
                    expenses = allExpenses,
                    earnings = allEarnings,
                    monthlyData = allMonthlyData
                )
            }

            // Add bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun GeneralStatsSection(
    expenses: List<Expense>,
    earnings: List<Earning>,
    monthlyData: List<MonthlyData>
) {
    // Separate budget expenses from earnings-paid expenses
    val budgetExpenses = expenses.filter { !it.paidFromEarnings }
    val earningsPaidExpenses = expenses.filter { it.paidFromEarnings }

    val totalSpentFromBudget = budgetExpenses.sumOf { it.amount }
    val totalSpentFromEarnings = earningsPaidExpenses.sumOf { it.amount }
    val totalSpent = totalSpentFromBudget + totalSpentFromEarnings // Total of all expenses

    val totalEarned = earnings.sumOf { it.amount }
    val netEarnings = totalEarned - totalSpentFromEarnings

    val uniqueMonths = expenses.map {
        YearMonth.of(it.date.year, it.date.month)
    }.distinct().size

    val avgPerMonth = if (uniqueMonths > 0) totalSpent / uniqueMonths else 0.0

    // Group expenses by month (only budget expenses for spending history)
    val expensesByMonth = budgetExpenses.groupBy {
        YearMonth.of(it.date.year, it.date.month)
    }.mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    // Group earnings by month (net of expenses paid from earnings)
    val earningsByMonth = earnings.groupBy {
        YearMonth.of(it.date.year, it.date.month)
    }.mapValues { (month, monthEarnings) ->
        val earnedAmount = monthEarnings.sumOf { it.amount }
        val spentFromEarnings = expenses.filter {
            it.paidFromEarnings && YearMonth.of(it.date.year, it.date.month) == month
        }.sumOf { it.amount }
        earnedAmount - spentFromEarnings
    }

    val mostExpensiveMonth = expensesByMonth.maxByOrNull { it.value }
    val leastExpensiveMonth = expensesByMonth.minByOrNull { it.value }

    // Category with most spending
    val categorySpending = expenses.groupBy { it.category }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
    val topCategory = categorySpending.maxByOrNull { it.value }

    // Average expense amount
    val avgExpenseAmount = if (expenses.isNotEmpty()) totalSpent / expenses.size else 0.0

    // Get current month
    val currentMonth = YearMonth.now().toString()

    // Total leftover budget from past months only (excluding current month)
    val totalPastLeftover = monthlyData
        .filter { it.month != currentMonth }
        .sumOf { it.leftoverBudget }

    // Total leftover budget from all months with expenses
    val monthsWithExpenses = expenses.map {
        YearMonth.of(it.date.year, it.date.month).toString()
    }.distinct()
    val totalLeftover = monthlyData
        .filter { it.month in monthsWithExpenses }
        .sumOf { it.leftoverBudget }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "General Statistics",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            StatRow(label = "Total Spent (All Time)", value = "$${String.format(Locale.US, "%.2f", totalSpent)}")
            StatRow(label = "Total Earned (All Time)", value = "$${String.format(Locale.US, "%.2f", totalEarned)}")
            StatRow(label = "Net Earnings", value = "$${String.format(Locale.US, "%.2f", netEarnings)}")
            StatRow(label = "Total Expenses", value = "${expenses.size}")
            StatRow(label = "Months Tracked", value = "$uniqueMonths")
            StatRow(label = "Average per Month", value = "$${String.format(Locale.US, "%.2f", avgPerMonth)}")
            StatRow(label = "Average Expense", value = "$${String.format(Locale.US, "%.2f", avgExpenseAmount)}")

            topCategory?.let { (category, amount) ->
                StatRow(
                    label = "Top Category",
                    value = "$category ($${String.format(Locale.US, "%.2f", amount)})"
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Past months leftover budget display (excluding current month)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Past Month's Leftover",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$${String.format(Locale.US, "%.2f", totalPastLeftover)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (totalPastLeftover >= 0) Color.Green else Color.Red
                )
            }

            // Total leftover budget display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Leftover Budget",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$${String.format(Locale.US, "%.2f", totalLeftover)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (totalLeftover >= 0) Color.Green else Color.Red
                )
            }

            mostExpensiveMonth?.let { (month, amount) ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Most Expensive Month",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}: $${String.format(Locale.US, "%.2f", amount)}",
                    fontSize = 16.sp,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }

            leastExpensiveMonth?.let { (month, amount) ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Least Expensive Month",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}: $${String.format(Locale.US, "%.2f", amount)}",
                    fontSize = 16.sp,
                    color = Color.Green,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun CategoryBreakdownSection(expenses: List<Expense>) {
    val totalSpent = expenses.sumOf { it.amount }

    val categoryTotals = expenses.groupBy { it.category }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Global Category Breakdown",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (categoryTotals.isEmpty()) {
                Text(
                    text = "No expenses recorded yet",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                categoryTotals.forEach { (category, amount) ->
                    val percentage = if (totalSpent > 0) (amount / totalSpent) * 100 else 0.0
                    CategoryStatItem(
                        category = category,
                        amount = amount,
                        percentage = percentage
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun CategoryStatItem(
    category: String,
    amount: Double,
    percentage: Double
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${"%.1f".format(percentage)}%",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$${String.format(Locale.US, "%.2f", amount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {}

            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (percentage / 100).toFloat())
                    .height(6.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Red,
                    shape = MaterialTheme.shapes.small
                ) {}
            }
        }
    }
}

@Composable
fun MonthlyComparisonSection(
    expenses: List<Expense>,
    earnings: List<Earning>,
    monthlyData: List<MonthlyData>
) {
    var isExpanded by remember { mutableStateOf(true) }

    // Only expenses paid from budget (not from earnings)
    val expensesByMonth = expenses.filter { !it.paidFromEarnings }.groupBy {
        YearMonth.of(it.date.year, it.date.month)
    }.mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    // Net earnings per month (earned - spent from earnings)
    val earningsByMonth = earnings.groupBy {
        YearMonth.of(it.date.year, it.date.month)
    }.mapValues { (month, monthEarnings) ->
        val earnedAmount = monthEarnings.sumOf { it.amount }
        val spentFromEarnings = expenses.filter {
            it.paidFromEarnings && YearMonth.of(it.date.year, it.date.month) == month
        }.sumOf { it.amount }
        earnedAmount - spentFromEarnings
    }

    // Get all months that have either expenses or earnings
    val allMonths = (expensesByMonth.keys + earningsByMonth.keys).distinct().sortedDescending()
    val sortedMonths = allMonths

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Spending History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 16.sp
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                if (sortedMonths.isEmpty()) {
                    Text(
                        text = "No monthly data available yet",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sortedMonths.forEach { month ->
                        val amount = expensesByMonth[month] ?: 0.0
                        val earned = earningsByMonth[month] ?: 0.0
                        val monthData = monthlyData.find { it.month == month.toString() }

                        MonthStatItem(
                            month = month,
                            spent = amount,
                            earned = earned,
                            leftover = monthData?.leftoverBudget
                        )

                        if (month != sortedMonths.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearlyComparisonSection(
    expenses: List<Expense>,
    earnings: List<Earning>,
    monthlyData: List<MonthlyData>
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Only expenses paid from budget (not from earnings)
    val expensesByYear = expenses.filter { !it.paidFromEarnings }.groupBy { it.date.year }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    // Group earnings by year (net of expenses paid from earnings)
    val earningsByYear = earnings.groupBy { it.date.year }
        .mapValues { (year, yearEarnings) ->
            val earnedAmount = yearEarnings.sumOf { it.amount }
            val spentFromEarnings = expenses.filter {
                it.paidFromEarnings && it.date.year == year
            }.sumOf { it.amount }
            earnedAmount - spentFromEarnings
        }

    // Get all years that have either expenses or earnings
    val allYears = (expensesByYear.keys + earningsByYear.keys).distinct().sortedDescending()
    val sortedYears = allYears

    // Calculate leftover budget per year
    val leftoverByYear = monthlyData.groupBy {
        it.month.substring(0, 4).toIntOrNull() ?: 0
    }.mapValues { (_, data) -> data.sumOf { it.leftoverBudget } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { isExpanded = !isExpanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yearly Spending History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 16.sp
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                if (sortedYears.isEmpty()) {
                    Text(
                        text = "No yearly data available yet",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sortedYears.forEach { year ->
                        val amount = expensesByYear[year] ?: 0.0
                        val earned = earningsByYear[year] ?: 0.0
                        val leftover = leftoverByYear[year]

                        YearStatItem(
                            year = year,
                            spent = amount,
                            earned = earned,
                            leftover = leftover
                        )

                        if (year != sortedYears.last()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearStatItem(
    year: Int,
    spent: Double,
    earned: Double,
    leftover: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = year.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            leftover?.let {
                Text(
                    text = "Budget: $${String.format(Locale.US, "%.2f", it)}",
                    fontSize = 12.sp,
                    color = if (it >= 0) Color.Green else Color.Red
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$${String.format(Locale.US, "%.2f", spent)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )
            if (earned > 0) {
                Text(
                    text = "+$${String.format(Locale.US, "%.2f", earned)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun MonthStatItem(
    month: YearMonth,
    spent: Double,
    earned: Double,
    leftover: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            leftover?.let {
                Text(
                    text = "Budget: $${String.format(Locale.US, "%.2f", it)}",
                    fontSize = 12.sp,
                    color = if (it >= 0) Color.Green else Color.Red
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$${String.format(Locale.US, "%.2f", spent)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )
            if (earned > 0) {
                Text(
                    text = "+$${String.format(Locale.US, "%.2f", earned)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

