package com.example.budgetron

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.budgetron.ui.theme.BudgetronTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Expense(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val category: String,
    val date: LocalDate = LocalDate.now(),
    val paidFromEarnings: Boolean = false
)

class MainActivity : ComponentActivity() {
    private lateinit var database: BudgetDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = BudgetDatabase(this)
        enableEdgeToEdge()
        setContent {
            BudgetronTheme {
                BudgetApp(database)
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
fun BudgetApp(database: BudgetDatabase) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Use a state to trigger recalculation when returning from other activities
    var refreshTrigger by remember { mutableStateOf(0) }

    // Listen for lifecycle events to refresh data when returning to this activity
    DisposableEffect(context) {
        val lifecycleOwner = context as? ComponentActivity
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Trigger recalculation when activity resumes
                refreshTrigger++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    // Current viewing month
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }


    // Load or initialize monthly data
    val monthKey = currentMonth.toString()
    val currentYearMonth = YearMonth.now()

    var monthlyData by remember(currentMonth, refreshTrigger) {
        mutableStateOf(
            if (currentMonth >= currentYearMonth) {
                // For current and future months, ALWAYS recalculate from fixed expenses
                // (ignore any previously saved allocation)
                val calculatedAllocation = database.calculateDailyAllocation(currentMonth.lengthOfMonth())
                val existingData = database.getMonthlyData(monthKey)
                MonthlyData(monthKey, calculatedAllocation, existingData?.leftoverBudget ?: 0.0)
            } else {
                // For past months, use saved data or 0 if no data exists
                database.getMonthlyData(monthKey) ?: MonthlyData(monthKey, 0.0, 0.0)
            }
        )
    }

    var dailyAllocation by remember(currentMonth, refreshTrigger) {
        mutableStateOf(monthlyData.dailyAllocation)
    }

    // Load expenses for current month from database
    var expenses by remember(currentMonth) {
        mutableStateOf(database.getExpensesForMonth(monthKey))
    }

    // Load earnings for current month from database
    var earnings by remember(currentMonth) {
        mutableStateOf(database.getEarningsForMonth(monthKey))
    }

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showChoiceDialog by remember { mutableStateOf(false) }
    var showAddEarningDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var earningToEdit by remember { mutableStateOf<Earning?>(null) }
    var isAnalysisMode by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Calculate current budget
    val today = LocalDate.now()
    val isCurrentMonth = currentMonth == currentYearMonth
    val isFutureMonth = currentMonth > currentYearMonth

    val dayOfMonth = when {
        isFutureMonth -> 0  // Future months always at day 0
        isCurrentMonth -> today.dayOfMonth  // Current month uses today's day
        else -> currentMonth.lengthOfMonth()  // Past months use full month
    }

    val allowedBudget = dayOfMonth * dailyAllocation
    val totalSpent = expenses.filter { !it.paidFromEarnings }.sumOf { it.amount }
    val totalSpentFromEarnings = expenses.filter { it.paidFromEarnings }.sumOf { it.amount }
    val totalEarnings = earnings.sumOf { it.amount }
    val remainingEarnings = totalEarnings - totalSpentFromEarnings
    val currentBudget = allowedBudget - totalSpent

    // Save monthly data when month changes or when navigating away from current month
    LaunchedEffect(currentBudget, currentMonth) {
        if (dailyAllocation > 0) {
            database.saveMonthlyData(monthKey, dailyAllocation, currentBudget)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Budgetron",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = { Text("Budget") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        label = { Text("Stats") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            val intent = Intent(context, StatsActivity::class.java)
                            context.startActivity(intent)
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Fixed Expenses") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            val intent = Intent(context, FixedExpensesActivity::class.java)
                            context.startActivity(intent)
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Share, contentDescription = null) },
                        label = { Text("Export to CSV") },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            showExportDialog = true
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = {
                                currentMonth = currentMonth.minusMonths(1)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                                )
                                Text(
                                    text = "$${"%.2f".format(dailyAllocation)}/day",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = {
                                currentMonth = currentMonth.plusMonths(1)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showChoiceDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Item")
                }
            }
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Budget Display
            BudgetDisplay(
                currentBudget = currentBudget,
                allowedBudget = allowedBudget,
                totalSpent = totalSpent,
                totalEarnings = totalEarnings,
                remainingEarnings = remainingEarnings,
                dayOfMonth = dayOfMonth
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isAnalysisMode) {
                // Category Analysis View
                CategoryAnalysis(expenses = expenses)
            } else {
                // Expenses List
                Text(
                    text = "Expenses & Earnings This Month",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(0.dp, 0.dp, 0.dp, 35.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)

                ) {
                    // Combine expenses and earnings
                    val combinedItems = expenses.map { Triple("expense", it.date, it as Any) } +
                            earnings.map { Triple("earning", it.date, it as Any) }

                    val sortedItems = combinedItems.sortedByDescending { it.second }

                    items(sortedItems) { (type, _, item) ->
                        when (type) {
                            "expense" -> {
                                val expense = item as Expense
                                ExpenseItem(
                                    expense = expense,
                                    onEdit = { expenseToEdit = expense },
                                    onDelete = {
                                        database.deleteExpense(expense.id)
                                        expenses = database.getExpensesForMonth(monthKey)
                                    }
                                )
                            }
                            "earning" -> {
                                val earning = item as Earning
                                EarningItem(
                                    earning = earning,
                                    onEdit = { earningToEdit = earning },
                                    onDelete = {
                                        database.deleteEarning(earning.id)
                                        earnings = database.getEarningsForMonth(monthKey)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChoiceDialog) {
            ChoiceDialog(
                onDismiss = { showChoiceDialog = false },
                onExpenseSelected = {
                    showChoiceDialog = false
                    showAddExpenseDialog = true
                },
                onEarningSelected = {
                    showChoiceDialog = false
                    showAddEarningDialog = true
                }
            )
        }

        if (showAddExpenseDialog) {
            AddExpenseDialog(
                onDismiss = { showAddExpenseDialog = false },
                onAddExpense = { expense ->
                    database.addExpense(expense)
                    expenses = database.getExpensesForMonth(monthKey)
                    showAddExpenseDialog = false
                }
            )
        }

        if (showAddEarningDialog) {
            AddEarningDialog(
                onDismiss = { showAddEarningDialog = false },
                onAddEarning = { earning ->
                    database.addEarning(earning)
                    earnings = database.getEarningsForMonth(monthKey)
                    showAddEarningDialog = false
                }
            )
        }

        expenseToEdit?.let { expense ->
            EditExpenseDialog(
                expense = expense,
                onDismiss = { expenseToEdit = null },
                onSave = { updatedExpense ->
                    database.updateExpense(updatedExpense)
                    expenses = database.getExpensesForMonth(monthKey)
                    expenseToEdit = null
                }
            )
        }

        earningToEdit?.let { earning ->
            EditEarningDialog(
                earning = earning,
                onDismiss = { earningToEdit = null },
                onSave = { updatedEarning ->
                    database.updateEarning(updatedEarning)
                    earnings = database.getEarningsForMonth(monthKey)
                    earningToEdit = null
                }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                isAnalysisMode = isAnalysisMode,
                onDismiss = { showSettingsDialog = false },
                onToggleAnalysisMode = {
                    isAnalysisMode = it
                }
            )
        }


        if (showExportDialog) {
            ExportConfirmationDialog(
                onDismiss = { showExportDialog = false },
                onConfirm = {
                    exportExpensesToCSV(context, database)
                    showExportDialog = false
                }
            )
        }
    }
}

@Composable
fun CategoryAnalysis(expenses: List<Expense>) {
    val totalSpent = expenses.sumOf { it.amount }

    // Group expenses by category
    val expensesByCategory = expenses.groupBy { it.category }

    // Create sorted list with totals
    val categoryData = expensesByCategory.map { (category, categoryExpenses) ->
        Triple(category, categoryExpenses.sumOf { it.amount }, categoryExpenses)
    }.sortedByDescending { it.second }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Category Analysis",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (categoryData.isEmpty()) {
            Text(
                text = "No expenses this month",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(0.dp, 0.dp, 0.dp, 35.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryData) { (category, amount, categoryExpenses) ->
                    CategoryItem(
                        category = category,
                        amount = amount,
                        percentage = if (totalSpent > 0) (amount / totalSpent) * 100 else 0.0,
                        expenses = categoryExpenses
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryItem(
    category: String,
    amount: Double,
    percentage: Double,
    expenses: List<Expense>
) {
    var isExpanded by remember { mutableStateOf(false) }

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
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = category,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "(${expenses.size})",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${"%.1f".format(percentage)}% of total",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "$${String.format(Locale.US, "%.2f", amount)}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar showing percentage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                // Background bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(0.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {}
                }

                // Filled bar (from left to right)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (percentage / 100).toFloat())
                        .height(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Red,
                        shape = MaterialTheme.shapes.small
                    ) {}
                }
            }

            // Expanded expense list
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                expenses.sortedByDescending { it.date }.forEach { expense ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = expense.date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", expense.amount)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetDisplay(
    currentBudget: Double,
    allowedBudget: Double,
    totalSpent: Double,
    totalEarnings: Double,
    remainingEarnings: Double,
    dayOfMonth: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Current Budget",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = "$${String.format(Locale.US, "%.2f", currentBudget)}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentBudget >= 0) Color.Green else Color.Red
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Allowed", fontSize = 12.sp)
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", allowedBudget)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Day $dayOfMonth", fontSize = 10.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Spent", fontSize = 12.sp)
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", totalSpent)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Earnings", fontSize = 12.sp)
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", remainingEarnings)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(text = "of $${String.format(Locale.US, "%.2f", totalEarnings)}", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = expense.category,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = expense.date.format(DateTimeFormatter.ofPattern("MMM dd")),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (expense.paidFromEarnings) {
                        Text(
                            text = "• Paid from earnings",
                            fontSize = 10.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$${String.format(Locale.US, "%.2f", expense.amount)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Expense",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Expense",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onAddExpense: (Expense) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Leisure") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var paidFromEarnings by remember { mutableStateOf(false) }

    val categories = listOf("Leisure", "Groceries", "Eating Out", "Transportation", "Shopping", "Bills", "Other")
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: ${selectedDate.format(dateFormatter)}")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pay from earnings",
                        fontSize = 14.sp
                    )
                    Checkbox(
                        checked = paidFromEarnings,
                        onCheckedChange = { paidFromEarnings = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0) {
                        onAddExpense(
                            Expense(
                                name = name,
                                amount = amountDouble,
                                category = category,
                                date = selectedDate,
                                paidFromEarnings = paidFromEarnings
                            )
                        )
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                selectedDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var name by remember { mutableStateOf(expense.name) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }
    var category by remember { mutableStateOf(expense.category) }
    var selectedDate by remember { mutableStateOf(expense.date) }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var paidFromEarnings by remember { mutableStateOf(expense.paidFromEarnings) }

    val categories = listOf("Leisure", "Groceries", "Eating Out", "Transportation", "Shopping", "Bills", "Other")
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: ${selectedDate.format(dateFormatter)}")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pay from earnings",
                        fontSize = 14.sp
                    )
                    Checkbox(
                        checked = paidFromEarnings,
                        onCheckedChange = { paidFromEarnings = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0) {
                        onSave(
                            expense.copy(
                                name = name,
                                amount = amountDouble,
                                category = category,
                                date = selectedDate,
                                paidFromEarnings = paidFromEarnings
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                selectedDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun SettingsDialog(
    isAnalysisMode: Boolean,
    onDismiss: () -> Unit,
    onToggleAnalysisMode: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Analysis Mode")
                    Switch(
                        checked = isAnalysisMode,
                        onCheckedChange = { onToggleAnalysisMode(it) }
                    )
                }
                Text(
                    text = "View expenses grouped by category with percentages.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toEpochDay() * 86400000
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / 86400000)
                        onDateSelected(date)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun ExportConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Expenses") },
        text = {
            Text("Export all expenses to a CSV file? The file will be saved to your Downloads folder.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChoiceDialog(
    onDismiss: () -> Unit,
    onExpenseSelected: () -> Unit,
    onEarningSelected: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExpenseSelected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Add Expense")
                }
                Button(
                    onClick = onEarningSelected,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Add Earning")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EarningItem(
    earning: Earning,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFC8E6C9)  // Less bright green
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = earning.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1B5E20)  // Darker text for better contrast
                )
                Text(
                    text = "Earning",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = earning.date.format(DateTimeFormatter.ofPattern("MMM dd")),
                    fontSize = 10.sp,
                    color = Color(0xFF2E7D32)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "+$${String.format(Locale.US, "%.2f", earning.amount)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20)  // Darker green for better contrast
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Earning",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Earning",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEarningDialog(
    onDismiss: () -> Unit,
    onAddEarning: (Earning) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Earning") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: ${selectedDate.format(dateFormatter)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0) {
                        onAddEarning(
                            Earning(
                                name = name,
                                amount = amountDouble,
                                date = selectedDate
                            )
                        )
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                selectedDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEarningDialog(
    earning: Earning,
    onDismiss: () -> Unit,
    onSave: (Earning) -> Unit
) {
    var name by remember { mutableStateOf(earning.name) }
    var amount by remember { mutableStateOf(earning.amount.toString()) }
    var selectedDate by remember { mutableStateOf(earning.date) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Earning") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Date: ${selectedDate.format(dateFormatter)}")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull()
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0) {
                        onSave(
                            earning.copy(
                                name = name,
                                amount = amountDouble,
                                date = selectedDate
                            )
                        )
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = { date ->
                selectedDate = date
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

fun exportExpensesToCSV(context: Context, database: BudgetDatabase) {
    try {
        val expenses = database.getAllExpenses()

        if (expenses.isEmpty()) {
            Toast.makeText(context, "No expenses to export", Toast.LENGTH_SHORT).show()
            return
        }

        // Create CSV content
        val csvHeader = "ID,Name,Amount,Category,Date\n"
        val csvContent = StringBuilder(csvHeader)

        expenses.forEach { expense ->
            csvContent.append("\"${expense.id}\",")
            csvContent.append("\"${expense.name}\",")
            csvContent.append("${expense.amount},")
            csvContent.append("\"${expense.category}\",")
            csvContent.append("\"${expense.date}\"\n")
        }

        // Save to Downloads folder
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val timestamp = System.currentTimeMillis()
        val fileName = "budgetron_expenses_$timestamp.csv"
        val file = File(downloadsDir, fileName)

        FileOutputStream(file).use { outputStream ->
            outputStream.write(csvContent.toString().toByteArray())
        }

        Toast.makeText(
            context,
            "Exported ${expenses.size} expenses to Downloads/$fileName",
            Toast.LENGTH_LONG
        ).show()

    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Error exporting: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
        e.printStackTrace()
    }
}

// Preview removed - requires database context
// @Preview(showBackground = true)
// @Composable
// fun BudgetAppPreview() {
//     BudgetronTheme {
//         BudgetApp(database)
//     }
// }

