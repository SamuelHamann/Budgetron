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
    monthlyData: List<MonthlyData>
) {
    val totalSpent = expenses.sumOf { it.amount }
    val uniqueMonths = expenses.map {
        YearMonth.of(it.date.year, it.date.month)
    }.distinct().size

    val avgPerMonth = if (uniqueMonths > 0) totalSpent / uniqueMonths else 0.0

    // Group expenses by month
    val expensesByMonth = expenses.groupBy {
        YearMonth.of(it.date.year, it.date.month)
    }.mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    val mostExpensiveMonth = expensesByMonth.maxByOrNull { it.value }
    val leastExpensiveMonth = expensesByMonth.minByOrNull { it.value }

    // Category with most spending
    val categorySpending = expenses.groupBy { it.category }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }
    val topCategory = categorySpending.maxByOrNull { it.value }

    // Average expense amount
    val avgExpenseAmount = if (expenses.isNotEmpty()) totalSpent / expenses.size else 0.0

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
    monthlyData: List<MonthlyData>
) {
    var isExpanded by remember { mutableStateOf(true) }

    val expensesByMonth = expenses.groupBy {
        YearMonth.of(it.date.year, it.date.month)
    }.mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    val sortedMonths = expensesByMonth.keys.sortedDescending()

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
                        val monthData = monthlyData.find { it.month == month.toString() }

                        MonthStatItem(
                            month = month,
                            spent = amount,
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
    monthlyData: List<MonthlyData>
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Group expenses by year
    val expensesByYear = expenses.groupBy { it.date.year }
        .mapValues { (_, expenses) -> expenses.sumOf { it.amount } }

    val sortedYears = expensesByYear.keys.sortedDescending()

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
                        val leftover = leftoverByYear[year]

                        YearStatItem(
                            year = year,
                            spent = amount,
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
    leftover: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
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
        Text(
            text = "$${String.format(Locale.US, "%.2f", spent)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red
        )
    }
}

@Composable
fun MonthStatItem(
    month: YearMonth,
    spent: Double,
    leftover: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
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
        Text(
            text = "$${String.format(Locale.US, "%.2f", spent)}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red
        )
    }
}

