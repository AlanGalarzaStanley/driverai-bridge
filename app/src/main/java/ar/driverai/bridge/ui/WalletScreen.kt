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
import ar.driverai.bridge.data.PendingTripEstimateSummary
import ar.driverai.bridge.engine.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WalletScreen(onHistory: () -> Unit = {}) {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val entries by g.walletRepo.entries.collectAsState(initial = emptyList())
    val history by g.historyRepo.entries.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val summary = remember(entries, today) { MoneyLedgerRepository.summarize(entries, today) }
    val pendingTrips = remember(history) { PendingTripEstimateSummary.from(history) }
    var editingType by remember { mutableStateOf<MoneyKind?>(null) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MoneyCategory.TRIP_INCOME) }
    var categoryMenu by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<MoneyEntry?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("Control diario", "Billetera", today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"))))
        MetricCard("Balance anotado hoy", Fmt.ars(summary.cashBalanceArs), "Ingresos anotados − gastos anotados · no es ganancia neta", Modifier.fillMaxWidth().testTag("wallet_balance_metric"), if (summary.cashBalanceArs < 0) Red else AccentMint)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Ingresos anotados", Fmt.ars(summary.incomeArs), "Confirmados por vos", Modifier.weight(1f).testTag("wallet_income_metric"), Green)
            MetricCard("Gastos anotados", Fmt.ars(summary.expensesArs), "Registrados hoy", Modifier.weight(1f).testTag("wallet_expense_metric"), Amber)
        }
        Text(
            "Los viajes aceptados aparecen aparte como estimación. Para vincular un cobro con un viaje, confirmalo desde Registro; no cargues el mismo importe dos veces.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        Section("Viajes aceptados · estimación separada", modifier = Modifier.fillMaxWidth().testTag("wallet_pending_trip_estimate")) {
            if (pendingTrips.tripCount == 0) {
                Text("No hay viajes aceptados o completados sin cobro anotado.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            } else {
                Text(
                    pendingTrips.offeredFareArs.takeIf { it > 0.0 }?.let(Fmt::ars) ?: "Tarifa no identificada",
                    style = MaterialTheme.typography.headlineSmall,
                    color = AccentBlue,
                    modifier = Modifier.testTag("wallet_pending_trip_amount"),
                )
                Text("${pendingTrips.tripCount} viaje(s) aceptado(s) o completado(s) sin cobro vinculado.", style = MaterialTheme.typography.bodyMedium)
                pendingTrips.pendingTrips.take(3).forEach { trip ->
                    val status = if (trip.tripStatus == ar.driverai.bridge.data.TripStatus.COMPLETED) {
                        "Completado · falta confirmar el cobro"
                    } else {
                        "Aceptado · falta completar el viaje"
                    }
                    Text("$status · ${trip.fare?.let(Fmt::ars) ?: "sin tarifa"}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                if (pendingTrips.tripCount > 3) Text("Y ${pendingTrips.tripCount - 3} viaje(s) más.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text("Estimación según la tarifa ofrecida: no es dinero recibido y no se suma al balance.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().testTag("wallet_open_history")) {
                    Text("Abrir Registro para confirmar")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { editingType = MoneyKind.INCOME; category = MoneyCategory.TRIP_INCOME; amountText = ""; formError = null }, modifier = Modifier.weight(1f).height(54.dp).testTag("wallet_add_income")) {
                Text("+ Ingreso real")
            }
            OutlinedButton(onClick = { editingType = MoneyKind.EXPENSE; category = MoneyCategory.FUEL; amountText = ""; formError = null }, modifier = Modifier.weight(1f).height(54.dp).testTag("wallet_add_expense")) {
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
                        if (entry.tripHistoryId == null) {
                            TextButton(onClick = { deleteTarget = entry }) { Text("Borrar") }
                        }
                    }
                    if (entry.tripHistoryId != null) {
                        Text("Vinculado al diario. Corregilo desde Actividad de ofertas.", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
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
