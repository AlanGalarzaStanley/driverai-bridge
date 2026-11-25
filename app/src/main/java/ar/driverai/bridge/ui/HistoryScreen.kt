package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import ar.driverai.bridge.data.HistoryEntry
import ar.driverai.bridge.data.JournalResult
import ar.driverai.bridge.data.MoneyAmountParser
import ar.driverai.bridge.data.TripStatus
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.Verdict
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val history by g.historyRepo.entries.collectAsState(initial = emptyList())
    val df = remember { SimpleDateFormat("dd/MM · HH:mm", Locale("es", "AR")) }
    var completionTarget by remember { mutableStateOf<HistoryEntry?>(null) }
    var paymentTarget by remember { mutableStateOf<HistoryEntry?>(null) }
    var undoPaymentTarget by remember { mutableStateOf<HistoryEntry?>(null) }
    var actualKmText by remember { mutableStateOf("") }
    var actualMinutesText by remember { mutableStateOf("") }
    var paymentText by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("REGISTRO", "Actividad de ofertas", "De la lectura al viaje y al cobro")
        Section("Tu decisión · datos separados") {
            Text("Una oferta observada no significa que la hayas aceptado. Completar un viaje y registrar lo que cobraste son pasos distintos; los importes reales se guardan en Billetera. No se envía información a Uber ni al servidor.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        OutlinedButton(onClick = { scope.launch { g.historyRepo.clear() } }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("Borrar historial de ofertas") }
        if (history.isEmpty()) {
            Section("Todavía no hay actividad") {
                Text("Sin evaluaciones guardadas.", style = MaterialTheme.typography.bodyMedium)
                Text("Las ofertas aparecerán cuando se evalúen con la lectura o desde el modo manual.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(history, key = { it.id }) { entry ->
                    val verdict = runCatching { Verdict.valueOf(entry.verdict) }.getOrDefault(Verdict.REVISAR)
                    Section(df.format(Date(entry.at)), modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            StatusPill(verdict.label, verdictColor(verdict))
                            Text(entry.source.name.lowercase().replaceFirstChar { it.titlecase(Locale("es", "AR")) }, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        Text(tripStatusLabel(entry.tripStatus), style = MaterialTheme.typography.labelLarge, color = tripStatusColor(entry.tripStatus), modifier = Modifier.padding(top = 7.dp))
                        Text(entry.fare?.let { Fmt.ars(it) } ?: "Tarifa ofrecida no identificada", style = MaterialTheme.typography.headlineSmall)
                        Text("Oferta estimada · ${entry.totalKm?.let { Fmt.num(it) } ?: "—"} km · ${entry.totalMin?.let { Fmt.num(it, 0) } ?: "—"} min", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                        Text(
                            "Bruto estimado: ${entry.grossPerKm?.let { Fmt.ars(it) + "/km" } ?: "—"} · ${entry.grossPerHour?.let { Fmt.ars(it) + "/h" } ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = verdictColor(verdict),
                            fontWeight = FontWeight.Medium,
                        )
                        if (entry.actualDistanceKm != null || entry.actualDurationMin != null) {
                            Text("Datos reales que anotaste: ${entry.actualDistanceKm?.let { Fmt.num(it, 1) + " km" } ?: "km sin cargar"} · ${entry.actualDurationMin?.let { Fmt.num(it, 0) + " min" } ?: "tiempo sin cargar"}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        if (entry.tripStatus == TripStatus.PAID) {
                            Text("Ingreso cobrado y vinculado: ${entry.actualIncomeArs?.let(Fmt::ars) ?: "revisar importe"}. No es ganancia neta.", style = MaterialTheme.typography.bodySmall, color = Green, modifier = Modifier.padding(top = 4.dp))
                        }
                        TripActions(
                            entry = entry,
                            onDecision = { accepted -> scope.launch { g.historyRepo.recordOfferDecision(entry.id, accepted) } },
                            onComplete = {
                                actualKmText = entry.actualDistanceKm?.let(::formatInput) ?: ""
                                actualMinutesText = entry.actualDurationMin?.let { it.toInt().toString() } ?: ""
                                dialogError = null
                                completionTarget = entry
                            },
                            onCancel = { scope.launch { g.historyRepo.cancelTrip(entry.id) } },
                            onRestore = { scope.launch { g.historyRepo.restoreObserved(entry.id) } },
                            onPayment = {
                                paymentText = entry.actualIncomeArs?.let(::formatInput) ?: ""
                                dialogError = null
                                paymentTarget = entry
                            },
                            onUndoPayment = { undoPaymentTarget = entry },
                        )
                    }
                }
            }
        }
    }

    completionTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { completionTarget = null },
            title = { Text("Registrar viaje completado") },
            text = {
                Column {
                    Text("La oferta sigue separada del resultado real. Estos datos son opcionales y quedan solo en tu teléfono.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedTextField(actualKmText, { actualKmText = it; dialogError = null }, label = { Text("Kilómetros reales (opcional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("trip_actual_km"))
                    OutlinedTextField(actualMinutesText, { actualMinutesText = it; dialogError = null }, label = { Text("Minutos reales (opcional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("trip_actual_minutes"))
                    dialogError?.let { Text(it, color = Red, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val km = parseDecimal(actualKmText)
                    val minutes = actualMinutesText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    val invalidKm = actualKmText.isNotBlank() && (km == null || km <= 0.0 || km > 2_000.0)
                    val invalidMinutes = actualMinutesText.isNotBlank() && (minutes == null || !minutes.isFinite() || minutes <= 0.0 || minutes > 48 * 60)
                    when {
                        invalidKm -> dialogError = "Los kilómetros deben ser un número mayor que cero y no superar 2.000."
                        invalidMinutes -> dialogError = "Los minutos deben ser un número entero entre 1 y 2.880."
                        minutes != null && minutes % 1.0 != 0.0 -> dialogError = "Ingresá los minutos como un número entero."
                        else -> scope.launch {
                            when (g.historyRepo.completeTrip(entry.id, km, minutes)) {
                                JournalResult.UPDATED -> completionTarget = null
                                JournalResult.INVALID_TRANSITION -> dialogError = "El estado cambió. Actualizá el historial antes de continuar."
                                JournalResult.INVALID_AMOUNT -> dialogError = "Revisá los valores reales ingresados."
                                JournalResult.NOT_FOUND -> dialogError = "La oferta ya no está en el historial."
                            }
                        }
                    }
                }, modifier = Modifier.testTag("trip_save_completed")) { Text("Guardar viaje") }
            },
            dismissButton = { TextButton(onClick = { completionTarget = null }) { Text("Cancelar") } },
        )
    }

    paymentTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { paymentTarget = null },
            title = { Text(if (entry.tripStatus == TripStatus.PAID) "Corregir cobro" else "Registrar cobro real") },
            text = {
                Column {
                    Text("Ingresá lo efectivamente recibido por este viaje. Se vinculará a Billetera y se podrá corregir.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    OutlinedTextField(paymentText, { paymentText = it; dialogError = null }, label = { Text("Importe cobrado en ARS") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("trip_actual_income"))
                    dialogError?.let { Text(it, color = Red, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = MoneyAmountParser.parse(paymentText)
                    if (amount == null) dialogError = "Ingresá un importe válido mayor que cero (por ejemplo 12.500 o 12.500,50)."
                    else scope.launch {
                        when (g.historyRepo.recordTripPayment(entry.id, amount)) {
                            JournalResult.UPDATED -> paymentTarget = null
                            JournalResult.INVALID_AMOUNT -> dialogError = "El importe no es válido."
                            JournalResult.INVALID_TRANSITION -> dialogError = "Primero registrá el viaje como completado."
                            JournalResult.NOT_FOUND -> dialogError = "La oferta ya no está en el historial."
                        }
                    }
                }, modifier = Modifier.testTag("trip_save_income")) { Text("Guardar cobro") }
            },
            dismissButton = { TextButton(onClick = { paymentTarget = null }) { Text("Cancelar") } },
        )
    }

    undoPaymentTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { undoPaymentTarget = null },
            title = { Text("Deshacer cobro vinculado") },
            text = { Text("Se quitará ${entry.actualIncomeArs?.let(Fmt::ars) ?: "el cobro"} de Billetera y el viaje volverá a completado. Los demás movimientos no cambian.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        g.historyRepo.removeTripPayment(entry.id)
                        undoPaymentTarget = null
                    }
                }) { Text("Deshacer cobro") }
            },
            dismissButton = { TextButton(onClick = { undoPaymentTarget = null }) { Text("Conservar") } },
        )
    }
}

@Composable
private fun TripActions(
    entry: HistoryEntry,
    onDecision: (Boolean) -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
    onPayment: () -> Unit,
    onUndoPayment: () -> Unit,
) {
    when (entry.tripStatus) {
        TripStatus.OBSERVED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
            OutlinedButton(onClick = { onDecision(true) }, modifier = Modifier.weight(1f)) { Text("La acepté") }
            TextButton(onClick = { onDecision(false) }, modifier = Modifier.weight(1f)) { Text("No la acepté") }
        }
        TripStatus.ACCEPTED -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onComplete, modifier = Modifier.weight(1f)) { Text("Viaje completado") }
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Se canceló") }
            }
            TextButton(onClick = onRestore, modifier = Modifier.align(Alignment.Start)) { Text("Corregir estado") }
        }
        TripStatus.REJECTED, TripStatus.CANCELLED -> TextButton(onClick = onRestore, modifier = Modifier.padding(top = 3.dp)) { Text("Corregir estado") }
        TripStatus.COMPLETED -> OutlinedButton(onClick = onPayment, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) { Text("Registrar cobro real") }
        TripStatus.PAID -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
            OutlinedButton(onClick = onPayment, modifier = Modifier.weight(1f)) { Text("Corregir cobro") }
            TextButton(onClick = onUndoPayment, modifier = Modifier.weight(1f)) { Text("Deshacer cobro") }
        }
    }
}

private fun tripStatusLabel(status: TripStatus): String = when (status) {
    TripStatus.OBSERVED -> "Oferta observada · sin decisión registrada"
    TripStatus.ACCEPTED -> "Aceptada por vos · viaje pendiente"
    TripStatus.REJECTED -> "No aceptada por vos"
    TripStatus.CANCELLED -> "Viaje cancelado"
    TripStatus.COMPLETED -> "Viaje completado · cobro pendiente de confirmar"
    TripStatus.PAID -> "Cobro registrado por vos"
}

private fun tripStatusColor(status: TripStatus): Color = when (status) {
    TripStatus.OBSERVED -> AccentBlue
    TripStatus.ACCEPTED, TripStatus.COMPLETED -> AccentMint
    TripStatus.REJECTED -> Red
    TripStatus.CANCELLED -> Amber
    TripStatus.PAID -> Green
}

private fun parseDecimal(raw: String): Double? = raw.trim().replace(',', '.').takeIf { Regex("^\\d+(?:\\.\\d+)?$").matches(it) }?.toDoubleOrNull()

private fun formatInput(value: Double): String = value.toString().removeSuffix(".0").replace('.', ',')
