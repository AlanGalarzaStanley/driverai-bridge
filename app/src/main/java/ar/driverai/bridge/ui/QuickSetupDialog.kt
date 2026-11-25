package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.QuickThresholds
import kotlinx.coroutines.launch

internal fun parseNum(s: String): Double? {
    val t = s.trim().replace("$", "").replace(" ", "")
    if (t.isBlank()) return null
    // "1.250,5" → 1250.5 ; "1250.5" → 1250.5 ; "1.250" → 1250
    val normalized = when {
        t.contains(',') -> t.replace(".", "").replace(',', '.')
        t.count { it == '.' } == 1 && t.substringAfter('.').length == 3 -> t.replace(".", "")
        t.count { it == '.' } > 1 -> t.replace(".", "")
        else -> t
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Primeros pasos: tus mínimos en 30 segundos a partir de tus propios costos. */
@Composable
fun QuickSetupDialog(onDismiss: () -> Unit) {
    val g = DriverAiApp.graph
    val s by g.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var price by remember { mutableStateOf(s.fuelPricePerLiter?.let { Fmt.num(it, 0) } ?: "") }
    var consumption by remember { mutableStateOf(s.fuelLitersPer100Km?.let { Fmt.num(it, 1) } ?: "") }
    var perHour by remember { mutableStateOf(s.minGrossPerHour?.let { Fmt.num(it, 0) } ?: "") }
    var perKmEdited by remember { mutableStateOf<String?>(null) }
    var pickupMin by remember { mutableStateOf(s.maxPickupMin?.let { Fmt.num(it, 0) } ?: "") }
    val suggestion = QuickThresholds.suggest(parseNum(price), parseNum(consumption), parseNum(perHour))
    val perKmText = perKmEdited ?: suggestion?.minGrossPerKm?.let { Fmt.num(it, 0) } ?: s.minGrossPerKm?.let { Fmt.num(it, 0) } ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tus mínimos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Con estos datos DriverAI te dice si una oferta te conviene. Usá tus valores reales; podés cambiarlos cuando quieras.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                OutlinedTextField(price, { price = it; perKmEdited = null }, label = { Text("Precio del combustible por litro o m³") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("qs_price"))
                OutlinedTextField(consumption, { consumption = it; perKmEdited = null }, label = { Text("Consumo cada 100 km (litros o m³)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("qs_consumption"))
                suggestion?.let {
                    Text("Combustible: ${Fmt.ars(it.fuelCostPerKm)} por km. Mínimo sugerido: ${Fmt.ars(it.minGrossPerKm)}/km (3× el combustible, para cubrir desgaste y service).",
                        style = MaterialTheme.typography.bodySmall, color = AccentMint, modifier = Modifier.padding(vertical = 4.dp))
                }
                OutlinedTextField(perKmText, { perKmEdited = it }, label = { Text("Mínimo por km") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("qs_perkm"))
                OutlinedTextField(perHour, { perHour = it }, label = { Text("Mínimo por hora que querés hacer") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("qs_perhour"))
                OutlinedTextField(pickupMin, { pickupMin = it }, label = { Text("Máximo de minutos hasta el pasajero (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = parseNum(perKmText) != null || parseNum(perHour) != null,
                onClick = {
                    scope.launch {
                        g.settingsRepo.update {
                            it.copy(
                                fuelPricePerLiter = parseNum(price)?.takeIf { v -> v > 0 } ?: it.fuelPricePerLiter,
                                fuelLitersPer100Km = parseNum(consumption)?.takeIf { v -> v in 0.1..60.0 } ?: it.fuelLitersPer100Km,
                                fuelPriceUpdatedAt = if (parseNum(price) != null) System.currentTimeMillis() else it.fuelPriceUpdatedAt,
                                minGrossPerKm = parseNum(perKmText)?.takeIf { v -> v > 0 } ?: it.minGrossPerKm,
                                minGrossPerHour = parseNum(perHour)?.takeIf { v -> v > 0 } ?: it.minGrossPerHour,
                                maxPickupMin = parseNum(pickupMin)?.takeIf { v -> v > 0 } ?: it.maxPickupMin,
                            )
                        }
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("qs_save"),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
