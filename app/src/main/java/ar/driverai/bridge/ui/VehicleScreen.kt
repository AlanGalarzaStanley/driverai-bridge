package ar.driverai.bridge.ui

import android.app.DatePickerDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.data.MaintenanceRecord
import ar.driverai.bridge.data.OdometerEntry
import ar.driverai.bridge.data.OdometerSaveResult
import ar.driverai.bridge.data.VehicleRecordsRepository
import ar.driverai.bridge.engine.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VehicleScreen() {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val odometer by g.vehicleRepo.odometerEntries.collectAsState(initial = emptyList())
    val records by g.vehicleRepo.maintenanceRecords.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val todayEntry = remember(odometer, today) { VehicleRecordsRepository.entryForDay(odometer, today) }
    val currentKm = remember(odometer) { VehicleRecordsRepository.latestOdometerKm(odometer) }

    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var odoMessage by remember { mutableStateOf<String?>(null) }
    var showMaintenanceDialog by remember { mutableStateOf(false) }
    var maintenanceTitle by remember { mutableStateOf("") }
    var dueKmText by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var maintenanceError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<MaintenanceRecord?>(null) }

    LaunchedEffect(todayEntry, currentKm) {
        if (todayEntry != null) {
            startText = Fmt.num(todayEntry.startKm, 1)
            endText = Fmt.num(todayEntry.endKm, 1)
        } else if (startText.isBlank()) {
            startText = currentKm?.let { Fmt.num(it, 1) } ?: ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("Tu vehículo", "Kilómetros y service", "Carga manual del odómetro · sin ubicación en segundo plano")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Odómetro actual", currentKm?.let { "${Fmt.num(it, 0)} km" } ?: "—", if (currentKm == null) "Registrá la primera lectura" else "Última lectura cargada", Modifier.weight(1f), AccentMint)
            MetricCard("Recorrido de hoy", todayEntry?.let { "${Fmt.num(it.drivenKm, 1)} km" } ?: "—", if (todayEntry == null) "Todavía no registrado" else "Inicio a fin", Modifier.weight(1f), AccentBlue)
        }
        Spacer(Modifier.height(10.dp))

        Section("Registrar odómetro de hoy") {
            Text("Ingresá la lectura del tablero al empezar y al terminar. El kilometraje del día es la diferencia; no se estima desde viajes ni GPS.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(startText, { startText = it; odoMessage = null }, label = { Text("Inicio km") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("odo_start"))
                OutlinedTextField(endText, { endText = it; odoMessage = null }, label = { Text("Final km") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("odo_end"))
            }
            odoMessage?.let { Text(it, color = if (it.startsWith("Guardado")) Green else Red, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = {
                val start = parseUserNumber(startText)
                val end = parseUserNumber(endText)
                if (start == null || end == null) {
                    odoMessage = "Completá ambas lecturas del odómetro."
                } else {
                    scope.launch {
                        when (val result = g.vehicleRepo.saveOdometer(OdometerEntry(today.toString(), start, end, System.currentTimeMillis()))) {
                            OdometerSaveResult.Saved -> odoMessage = "Guardado en este teléfono."
                            is OdometerSaveResult.Invalid -> odoMessage = result.message
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth().testTag("odo_save")) { Text(if (todayEntry == null) "Guardar kilómetros de hoy" else "Actualizar registro de hoy") }
        }

        Section("Service y vencimientos") {
            Text("Agregá el kilometraje o la fecha que te indique tu taller, VTV, GNC o seguro. No precargamos intervalos genéricos.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                maintenanceTitle = ""; dueKmText = ""; dueDate = null; maintenanceError = null; showMaintenanceDialog = true
            }, modifier = Modifier.fillMaxWidth().testTag("maintenance_add")) { Text("+ Agregar recordatorio") }
            if (records.isEmpty()) {
                Text("No hay services cargados. Tus próximos vencimientos aparecerán acá.", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 8.dp))
            } else {
                records.sortedWith(compareBy<MaintenanceRecord> { it.completedAt != null }.thenBy { it.dueDate ?: "9999-12-31" }.thenBy { it.dueOdometerKm ?: Double.MAX_VALUE })
                    .forEach { record ->
                        MaintenanceCard(record, currentKm, today, onDone = {
                            scope.launch { g.vehicleRepo.markMaintenanceDone(record.id, currentKm) }
                        }, onDelete = { deleteTarget = record })
                    }
            }
        }
    }

    if (showMaintenanceDialog) {
        AlertDialog(
            onDismissRequest = { showMaintenanceDialog = false },
            title = { Text("Nuevo recordatorio") },
            text = {
                Column {
                    OutlinedTextField(maintenanceTitle, { maintenanceTitle = it.take(40); maintenanceError = null }, label = { Text("Tarea (ej. aceite, VTV, GNC)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("maintenance_title"))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(dueKmText, { dueKmText = it; maintenanceError = null }, label = { Text("Vence a los km (opcional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("maintenance_due_km"))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val seed = dueDate ?: today
                            DatePickerDialog(context, { _, year, month, day -> dueDate = LocalDate.of(year, month + 1, day); maintenanceError = null }, seed.year, seed.monthValue - 1, seed.dayOfMonth).show()
                        }, modifier = Modifier.weight(1f)) { Text(dueDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Elegir fecha") }
                        if (dueDate != null) TextButton(onClick = { dueDate = null }) { Text("Quitar") }
                    }
                    maintenanceError?.let { Text(it, color = Red, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val km = if (dueKmText.isBlank()) null else parseUserNumber(dueKmText)
                    if (dueKmText.isNotBlank() && km == null) {
                        maintenanceError = "Ingresá un kilometraje válido."
                    } else {
                        scope.launch {
                            val error = g.vehicleRepo.addMaintenance(maintenanceTitle, km, dueDate?.toString())
                            if (error == null) showMaintenanceDialog = false else maintenanceError = error
                        }
                    }
                }, modifier = Modifier.testTag("maintenance_save")) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showMaintenanceDialog = false }) { Text("Cancelar") } },
        )
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar recordatorio") },
            text = { Text("¿Querés eliminar ‘${record.title}’ del historial local de mantenimiento?") },
            confirmButton = { Button(onClick = { scope.launch { g.vehicleRepo.deleteMaintenance(record.id); deleteTarget = null } }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MaintenanceCard(record: MaintenanceRecord, currentKm: Double?, today: LocalDate, onDone: () -> Unit, onDelete: () -> Unit) {
    val done = record.completedAt != null
    val overdue = VehicleRecordsRepository.maintenanceIsOverdue(record, currentKm, today)
    val color = when { done -> TextMuted; overdue -> Red; else -> AccentMint }
    Section(record.title) {
        if (record.dueOdometerKm != null) {
            val remaining = currentKm?.let { record.dueOdometerKm - it }
            Text(if (remaining == null) "Vence a ${Fmt.num(record.dueOdometerKm, 0)} km · cargá el odómetro para ver cuánto falta"
                else if (remaining <= 0) "Vencido por kilometraje · ${Fmt.num(kotlin.math.abs(remaining), 0)} km"
                else "Vence a ${Fmt.num(record.dueOdometerKm, 0)} km · faltan ${Fmt.num(remaining, 0)} km", color = if (remaining != null && remaining <= 0) Red else TextMuted)
        }
        record.dueDate?.let { date ->
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
            Text("Fecha límite: ${parsed?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: date}", color = if (overdue && !done) Red else TextMuted)
        }
        when {
            done -> {
                val completedDay = record.completedAt?.let { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it), java.time.ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }
                Text("Realizado${completedDay?.let { " el $it" } ?: ""}${record.completedAtKm?.let { " · ${Fmt.num(it, 0)} km" } ?: ""}", color = color)
            }
            overdue -> Text("VENCIDO", color = Red, style = MaterialTheme.typography.labelLarge)
            else -> Text("Pendiente", color = color, style = MaterialTheme.typography.labelLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!done) OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Marcar realizado") }
            TextButton(onClick = onDelete) { Text("Eliminar") }
        }
    }
}
