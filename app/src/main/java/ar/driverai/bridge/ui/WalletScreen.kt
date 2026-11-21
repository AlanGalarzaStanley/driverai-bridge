package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.data.MoneyCategory
import ar.driverai.bridge.data.MoneyEntry
import ar.driverai.bridge.data.MoneyKind
import ar.driverai.bridge.data.MoneyLedgerRepository
import ar.driverai.bridge.data.MoneyAmountParser
import ar.driverai.bridge.engine.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WalletScreen() {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val entries by g.walletRepo.entries.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val summary = remember(entries, today) { MoneyLedgerRepository.summarize(entries, today) }
    var editingType by remember { mutableStateOf<MoneyKind?>(null) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MoneyCategory.TRIP_INCOME) }
    var categoryMenu by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<MoneyEntry?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("Control diario", "Billetera", today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Ingresos cargados", Fmt.ars(summary.incomeArs), "Confirmados por vos", Modifier.weight(1f).testTag("wallet_income_metric"), Green)
            MetricCard("Gastos cargados", Fmt.ars(summary.expensesArs), "Registrados hoy", Modifier.weight(1f).testTag("wallet_expense_metric"), Amber)
        }
        Spacer(Modifier.height(10.dp))
        MetricCard("Diferencia de caja", Fmt.ars(summary.cashBalanceArs), "No equivale a ganancia neta", Modifier.fillMaxWidth().testTag("wallet_balance_metric"), if (summary.cashBalanceArs < 0) Red else AccentMint)
        Text(
            "Solo suma movimientos que registrás. No convierte ofertas leídas o marcadas como tomadas en ingresos, ni estima gastos que todavía no cargaste.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { editingType = MoneyKind.INCOME; category = MoneyCategory.TRIP_INCOME; amountText = ""; formError = null }, modifier = Modifier.weight(1f).testTag("wallet_add_income")) {
                Text("+ Ingreso real")
            }
            OutlinedButton(onClick = { editingType = MoneyKind.EXPENSE; category = MoneyCategory.FUEL; amountText = ""; formError = null }, modifier = Modifier.weight(1f).testTag("wallet_add_expense")) {
                Text("+ Gasto")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Movimientos de hoy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
        if (summary.entries.isEmpty()) {
            Section("Todavía no hay movimientos") {
                Text("Cargá lo que efectivamente cobraste y los gastos del día. Los datos quedan en este teléfono.", color = TextMuted)
            }
        } else {
            summary.entries.forEach { entry ->
                Section(entry.category.displayLabel()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(if (entry.kind == MoneyKind.INCOME) "Ingreso confirmado" else "Gasto", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text(DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-AR")).format(java.time.Instant.ofEpochMilli(entry.at).atZone(java.time.ZoneId.systemDefault())), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        Text(
                            (if (entry.kind == MoneyKind.INCOME) "+ " else "− ") + Fmt.ars(entry.amountArs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (entry.kind == MoneyKind.INCOME) Green else Amber,
                        )
                        TextButton(onClick = { deleteTarget = entry }) { Text("Borrar") }
                    }
                }
            }
        }
    }

    editingType?.let { type ->
        val categories = if (type == MoneyKind.INCOME) listOf(MoneyCategory.TRIP_INCOME, MoneyCategory.BONUS, MoneyCategory.OTHER_INCOME)
            else listOf(MoneyCategory.FUEL, MoneyCategory.TOLL, MoneyCategory.PARKING, MoneyCategory.MAINTENANCE, MoneyCategory.OTHER_EXPENSE)
        AlertDialog(
            onDismissRequest = { editingType = null },
            title = { Text(if (type == MoneyKind.INCOME) "Registrar ingreso real" else "Registrar gasto") },
            text = {
                Column {
                    Text("Importe en pesos argentinos", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedTextField(amountText, { amountText = it; formError = null }, label = { Text("Importe ARS") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("wallet_amount"))
                    Spacer(Modifier.height(8.dp))
                    Text("Categoría", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(category.displayLabel()) }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categories.forEach { item ->
                            DropdownMenuItem(text = { Text(item.displayLabel()) }, onClick = { category = item; categoryMenu = false })
                        }
                    }
                    formError?.let { Text(it, color = Red, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = MoneyAmountParser.parse(amountText)
                    if (amount == null || !amount.isFinite() || amount <= 0.0) {
                        formError = "Ingresá un importe válido mayor que cero."
                    } else {
                        scope.launch {
                            runCatching { g.walletRepo.add(type, amount, category) }
                                .onSuccess { editingType = null }
                                .onFailure { formError = it.message ?: "No se pudo guardar el movimiento." }
                        }
                    }
                }, modifier = Modifier.testTag("wallet_save")) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingType = null }) { Text("Cancelar") } },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Borrar movimiento") },
            text = { Text("¿Querés borrar ${entry.category.displayLabel().lowercase()} por ${Fmt.ars(entry.amountArs)}? Esta acción no se puede deshacer.") },
            confirmButton = { Button(onClick = { scope.launch { g.walletRepo.delete(entry.id); deleteTarget = null } }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }
}

private fun MoneyCategory.displayLabel(): String = when (this) {
    MoneyCategory.TRIP_INCOME -> "Viajes"
    MoneyCategory.BONUS -> "Bonificaciones"
    MoneyCategory.OTHER_INCOME -> "Otros ingresos"
    MoneyCategory.FUEL -> "Combustible"
    MoneyCategory.TOLL -> "Peajes"
    MoneyCategory.PARKING -> "Estacionamiento"
    MoneyCategory.MAINTENANCE -> "Mantenimiento"
    MoneyCategory.OTHER_EXPENSE -> "Otros gastos"
}
