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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import java.util.Locale

class FixedExpensesActivity : ComponentActivity() {
    private lateinit var database: BudgetDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = BudgetDatabase(this)
        enableEdgeToEdge()
        setContent {
            BudgetronTheme {
                FixedExpensesScreen(
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
fun FixedExpensesScreen(
    database: BudgetDatabase,
    onBackClick: () -> Unit
) {
    var monthlyNetIncome by remember { mutableStateOf(database.getMonthlyNetIncome()) }
    var fixedExpenses by remember { mutableStateOf(database.getAllFixedExpenses()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditIncomeDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<FixedExpense?>(null) }

    val totalFixedExpenses = fixedExpenses.sumOf { it.amount }
    val availableBudget = monthlyNetIncome - totalFixedExpenses

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Fixed Expenses") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Fixed Expense")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Monthly Net Income Card
            item {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monthly Net Income",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showEditIncomeDialog = true }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Income",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", monthlyNetIncome)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Budget Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Budget Summary",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        BudgetSummaryRow("Total Fixed Expenses", totalFixedExpenses, Color.Red)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        BudgetSummaryRow(
                            "Available Monthly Budget",
                            availableBudget,
                            if (availableBudget >= 0) Color.Green else Color.Red
                        )

                        if (availableBudget > 0) {
                            Text(
                                text = "This will be divided by the days in each month to calculate daily allocations.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            // Fixed Expenses List Header
            item {
                Text(
                    text = "Fixed Expenses (${fixedExpenses.size})",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Fixed Expenses List
            if (fixedExpenses.isEmpty()) {
                item {
                    Text(
                        text = "No fixed expenses yet. Tap + to add one.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(fixedExpenses) { expense ->
                    FixedExpenseItem(
                        expense = expense,
                        onEdit = { expenseToEdit = expense },
                        onDelete = {
                            database.deleteFixedExpense(expense.id)
                            fixedExpenses = database.getAllFixedExpenses()
                        }
                    )
                }
            }

            // Bottom padding
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showAddDialog) {
        AddFixedExpenseDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { expense ->
                database.addFixedExpense(expense)
                fixedExpenses = database.getAllFixedExpenses()
                showAddDialog = false
            }
        )
    }

    expenseToEdit?.let { expense ->
        EditFixedExpenseDialog(
            expense = expense,
            onDismiss = { expenseToEdit = null },
            onSave = { updatedExpense ->
                database.updateFixedExpense(updatedExpense)
                fixedExpenses = database.getAllFixedExpenses()
                expenseToEdit = null
            }
        )
    }

    if (showEditIncomeDialog) {
        EditIncomeDialog(
            currentIncome = monthlyNetIncome,
            onDismiss = { showEditIncomeDialog = false },
            onSave = { newIncome ->
                database.updateMonthlyNetIncome(newIncome)
                monthlyNetIncome = newIncome
                showEditIncomeDialog = false
            }
        )
    }
}

@Composable
fun BudgetSummaryRow(label: String, amount: Double, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp
        )
        Text(
            text = "$${String.format(Locale.US, "%.2f", amount)}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun FixedExpenseItem(
    expense: FixedExpense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
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
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
fun AddFixedExpenseDialog(
    onDismiss: () -> Unit,
    onAdd: (FixedExpense) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Fixed Expense") },
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
                    singleLine = true,
                    placeholder = { Text("e.g., Rent, Insurance") }
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monthly Amount") },
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
                    val amountDouble = amount.toDoubleOrNull()
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0) {
                        onAdd(FixedExpense(name = name, amount = amountDouble))
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
}

@Composable
fun EditFixedExpenseDialog(
    expense: FixedExpense,
    onDismiss: () -> Unit,
    onSave: (FixedExpense) -> Unit
) {
    var name by remember { mutableStateOf(expense.name) }
    var amount by remember { mutableStateOf(expense.amount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Fixed Expense") },
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
                    label = { Text("Monthly Amount") },
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
                    val amountDouble = amount.toDoubleOrNull()
                    if (name.isNotBlank() && amountDouble != null && amountDouble > 0) {
                        onSave(expense.copy(name = name, amount = amountDouble))
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
fun EditIncomeDialog(
    currentIncome: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var income by remember { mutableStateOf(currentIncome.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Monthly Net Income") },
        text = {
            Column {
                Text(
                    text = "Enter your monthly net income (after taxes).",
                    modifier = Modifier.padding(bottom = 8.dp),
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = income,
                    onValueChange = { income = it },
                    label = { Text("Monthly Net Income") },
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
                    val incomeDouble = income.toDoubleOrNull()
                    if (incomeDouble != null && incomeDouble >= 0) {
                        onSave(incomeDouble)
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

