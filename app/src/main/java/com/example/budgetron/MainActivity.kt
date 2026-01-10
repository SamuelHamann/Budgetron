package com.example.budgetron

import android.os.Bundle
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.budgetron.ui.theme.BudgetronTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Expense(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val amount: Double,
    val category: String,
    val date: LocalDate = LocalDate.now()
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
    // Current viewing month
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    // Load daily allocation from database (default for new current month)
    val defaultDailyAllocation = database.getDailyAllocation()

    // Load or initialize monthly data
    val monthKey = currentMonth.toString()
    var monthlyData by remember(currentMonth) {
        mutableStateOf(
            database.getMonthlyData(monthKey) ?: run {
                // New months start with 0, except current month uses default
                val initialAllocation = if (currentMonth == YearMonth.now()) {
                    defaultDailyAllocation
                } else {
                    0.0
                }
                MonthlyData(monthKey, initialAllocation, 0.0)
            }
        )
    }

    var dailyAllocation by remember(currentMonth) {
        mutableStateOf(monthlyData.dailyAllocation)
    }

    // Load expenses for current month from database
    var expenses by remember(currentMonth) {
        mutableStateOf(database.getExpensesForMonth(monthKey))
    }

    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showMonthAllocationDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }

    // Calculate current budget
    val today = LocalDate.now()
    val currentYearMonth = YearMonth.now()
    val isCurrentMonth = currentMonth == currentYearMonth
    val isFutureMonth = currentMonth > currentYearMonth

    val dayOfMonth = when {
        isFutureMonth -> 0  // Future months always at day 0
        isCurrentMonth -> today.dayOfMonth  // Current month uses today's day
        else -> currentMonth.lengthOfMonth()  // Past months use full month
    }

    val allowedBudget = dayOfMonth * dailyAllocation
    val totalSpent = expenses.sumOf { it.amount }
    val currentBudget = allowedBudget - totalSpent

    // Save monthly data when month changes or when navigating away from current month
    LaunchedEffect(currentBudget, currentMonth) {
        if (dailyAllocation > 0) {
            database.saveMonthlyData(monthKey, dailyAllocation, currentBudget)
        }
    }

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
                            TextButton(
                                onClick = { showMonthAllocationDialog = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "$${"%.2f".format(dailyAllocation)}/day",
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(onClick = {
                            currentMonth = currentMonth.plusMonths(1)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                        }
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
            FloatingActionButton(onClick = { showAddExpenseDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
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
                dayOfMonth = dayOfMonth
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Expenses List
            Text(
                text = "Expenses This Month",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(0.dp, 35.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)

            ) {
                items(expenses.sortedByDescending { it.date }) { expense ->
                    ExpenseItem(
                        expense = expense,
                        onEdit = { expenseToEdit = expense },
                        onDelete = {
                            database.deleteExpense(expense.id)
                            expenses = database.getExpensesForMonth(monthKey)
                        }
                    )
                }
            }
        }
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

    if (showSettingsDialog) {
        SettingsDialog(
            currentAllocation = dailyAllocation,
            onDismiss = { showSettingsDialog = false },
            onSave = { newAllocation ->
                database.updateDailyAllocation(newAllocation)
                dailyAllocation = newAllocation
                showSettingsDialog = false
            }
        )
    }

    if (showMonthAllocationDialog) {
        MonthAllocationDialog(
            month = currentMonth,
            currentAllocation = dailyAllocation,
            onDismiss = { showMonthAllocationDialog = false },
            onSave = { newAllocation ->
                dailyAllocation = newAllocation
                database.saveMonthlyData(monthKey, newAllocation, currentBudget)
                showMonthAllocationDialog = false
            }
        )
    }
}

@Composable
fun BudgetDisplay(
    currentBudget: Double,
    allowedBudget: Double,
    totalSpent: Double,
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
                        fontWeight = FontWeight.Bold
                    )
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
                Text(
                    text = expense.date.format(DateTimeFormatter.ofPattern("MMM dd")),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

@Composable
fun SettingsDialog(
    currentAllocation: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var allocation by remember { mutableStateOf(currentAllocation.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Settings") },
        text = {
            Column {
                Text(
                    text = "Set the default daily allocation for new current months. Use the month editor to change specific months.",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = allocation,
                    onValueChange = { allocation = it },
                    label = { Text("Daily Allocation") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val allocationDouble = allocation.toDoubleOrNull()
                    if (allocationDouble != null && allocationDouble > 0) {
                        onSave(allocationDouble)
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
}

@Composable
fun MonthAllocationDialog(
    month: YearMonth,
    currentAllocation: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var allocation by remember { mutableStateOf(currentAllocation.toString()) }
    val monthName = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Allocation for $monthName") },
        text = {
            Column {
                Text(
                    text = "Set the daily allocation for this month.",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = allocation,
                    onValueChange = { allocation = it },
                    label = { Text("Daily Allocation") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val allocationDouble = allocation.toDoubleOrNull()
                    if (allocationDouble != null && allocationDouble >= 0) {
                        onSave(allocationDouble)
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

// Preview removed - requires database context
// @Preview(showBackground = true)
// @Composable
// fun BudgetAppPreview() {
//     BudgetronTheme {
//         BudgetApp(database)
//     }
// }

