package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.copilot.Zones
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.engine.RidePlatform
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.UncertaintyTolerance
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun f(v: Double?) = v?.let { Fmt.num(it, 2) } ?: ""

@Composable
fun SettingsScreen() {
    val g = DriverAiApp.graph
    val scope = rememberCoroutineScope()
    val saved by g.settings.collectAsState()
    var loaded by remember { mutableStateOf(false) }
    var minFare by remember { mutableStateOf("") }
    var minKm by remember { mutableStateOf("") }
    var minHour by remember { mutableStateOf("") }
    var costKm by remember { mutableStateOf("") }
    var fuelL by remember { mutableStateOf("") }
    var fuelP by remember { mutableStateOf("") }
    var defPkm by remember { mutableStateOf("") }
    var defPmin by remember { mutableStateOf("") }
    var ret by remember { mutableStateOf("0") }
    var margin by remember { mutableStateOf("") }
    var minPassengerRating by remember { mutableStateOf("") }
    var dailyTarget by remember { mutableStateOf("") }
    var maxPkm by remember { mutableStateOf("") }
    var maxPmin by remember { mutableStateOf("") }
    var sportsKey by remember { mutableStateOf("") }
    var tol by remember { mutableStateOf(UncertaintyTolerance.NORMAL) }
    var msg by remember { mutableStateOf<String?>(null) }
    var homeMenu by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (!loaded) {
            val s = g.settingsRepo.current()
            minFare = f(s.minFare); minKm = f(s.minGrossPerKm); minHour = f(s.minGrossPerHour); costKm = f(s.costPerKm)
            fuelL = f(s.fuelLitersPer100Km); fuelP = f(s.fuelPricePerLiter); defPkm = f(s.defaultPickupKm); defPmin = f(s.defaultPickupMin)
            ret = Fmt.num(s.emptyReturnFraction * 100, 0); margin = f(s.minMarginPct); minPassengerRating = f(s.minPassengerRating); dailyTarget = f(s.dailyTargetArs); tol = s.tolerance
            maxPkm = f(s.maxPickupKm); maxPmin = f(s.maxPickupMin); sportsKey = s.sportsApiKey.orEmpty()
            loaded = true
        }
    }

    @Composable
    fun num(label: String, v: String, tag: String, set: (String) -> Unit) = OutlinedTextField(v, set, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag(tag))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        ScreenTitle("TU CONFIGURACIÓN", "Ajustes", "Personalizá cuándo una oferta encaja con tus mínimos")
        Text("Dejá vacío lo que no quieras usar. DriverAI no trae tarifas ni costos precargados.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Section("Umbrales (ingreso bruto)") {
            num("Tarifa mínima (\$)", minFare, "set_minfare") { minFare = it }
            num("Mínimo \$ por km", minKm, "set_minkm") { minKm = it }
            num("Mínimo \$ por hora", minHour, "set_minhour") { minHour = it }
            num("Margen mínimo (%)", margin, "set_margin") { margin = it }
        }
        Section("Preferencias del pasajero") {
            num("Calificación mínima (1–5)", minPassengerRating, "set_min_passenger_rating") { minPassengerRating = it }
            Text("Se compara solo si DriverAI lee una calificación explícita del pasajero. Si falta o es ambigua, el resultado será REVISAR; no se completa por inferencia.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Section("Objetivo del día") {
            num("Objetivo de ingresos registrados (ARS)", dailyTarget, "set_daily_target") { dailyTarget = it }
            Text("Opcional. El progreso se compara solo con ingresos que cargues en Billetera; no es una predicción ni una medida de ganancia neta.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Section("Costos variables") {
            num("Costo propio por km (\$: desgaste, mantenimiento)", costKm, "set_costkm") { costKm = it }
            num("Consumo (litros cada 100 km)", fuelL, "set_fuell") { fuelL = it }
            num("Precio combustible (\$/litro)", fuelP, "set_fuelp") { fuelP = it }
            saved.fuelPriceUpdatedAt?.let { Text("Precio cargado el ${DateFormat.getDateInstance().format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
        }
        Section("Supuestos") {
            num("Km a recogida si no se lee", defPkm, "set_defpkm") { defPkm = it }
            num("Min a recogida si no se lee", defPmin, "set_defpmin") { defPmin = it }
            num("Retorno vacío (% del viaje, 0–100)", ret, "set_return") { ret = it }
            Text("Tolerancia a la incertidumbre de lectura:")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UncertaintyTolerance.entries.forEach { t -> FilterChip(selected = tol == t, onClick = { tol = t }, label = { Text("${t.label} (${(t.minConfidence * 100).toInt()} %)") }) }
            }
        }
        Section("Recogida") {
            num("Máximo km hasta el pasajero", maxPkm, "set_max_pickup_km") { maxPkm = it }
            num("Máximo minutos hasta el pasajero", maxPmin, "set_max_pickup_min") { maxPmin = it }
            Text("Si la oferta muestra una recogida más lejana, el resultado será NO CONVIENE aunque la tarifa sea buena.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Section("Apps que DriverAI lee") {
            RidePlatform.READABLE.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.label + if (p.verified) "" else " (beta)", Modifier.weight(1f))
                    Switch(saved.platformEnabled(p), { on ->
                        scope.launch {
                            g.settingsRepo.update { st ->
                                val next = if (on) (st.enabledPlatforms + p.name).distinct() else st.enabledPlatforms - p.name
                                st.copy(enabledPlatforms = next)
                            }
                        }
                    }, modifier = Modifier.testTag("platform_${p.name.lowercase()}"))
                }
            }
            Text("Solo lectura del texto visible. «beta» = diseño de pantalla aún sin muestras reales confirmadas; si algo no se lee bien, usá Oferta manual.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Section("Radar de demanda") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar demanda en el destino de cada oferta", Modifier.weight(1f))
                Switch(saved.showDestinationDemand, { v -> scope.launch { g.settingsRepo.update { it.copy(showDestinationDemand = v) } } })
            }
            OutlinedTextField(sportsKey, { sportsKey = it.trim().take(64) }, label = { Text("Clave TheSportsDB (opcional)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("set_sports_key"))
            Text("Con tu clave el radar trae más partidos del fútbol argentino. Se guarda solo en este teléfono.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Section("Voz y privacidad") {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Voz activada", Modifier.weight(1f)); Switch(saved.voiceEnabled, { v -> scope.launch { g.settingsRepo.update { it.copy(voiceEnabled = v) } } }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Decir el veredicto de cada oferta", Modifier.weight(1f)); Switch(saved.speakVerdicts, { v -> scope.launch { g.settingsRepo.update { it.copy(speakVerdicts = v) } } }) }
            Text("Volumen de voz: ${(saved.voiceVolume * 100).toInt()} %")
            Slider(saved.voiceVolume, { v -> scope.launch { g.settingsRepo.update { it.copy(voiceVolume = v) } } })
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Guardar historial (solo números)", Modifier.weight(1f)); Switch(saved.historyEnabled, { v -> scope.launch { g.settingsRepo.update { it.copy(historyEnabled = v) } } }) }
            Column {
                OutlinedButton(onClick = { homeMenu = true }) { Text("Zona de casa: ${saved.homeZone ?: "sin definir"}") }
                DropdownMenu(homeMenu, { homeMenu = false }) {
                    Zones.ALL.forEach { z -> DropdownMenuItem(text = { Text(z) }, onClick = { homeMenu = false; scope.launch { g.settingsRepo.update { it.copy(homeZone = z) } } }) }
                }
            }
        }
        Button(onClick = {
            val errors = mutableListOf<String>()
            fun p(label: String, s: String, max: Double = 1e9): Double? {
                if (s.isBlank()) return null
                val v = parseUserNumber(s)
                if (v == null || !v.isFinite() || v < 0 || v > max) { errors += label; return null }
                return v
            }
            val retPct = p("Retorno vacío", ret, 100.0) ?: 0.0
            val newFuelP = p("Precio combustible", fuelP)
            val parsedMinPassengerRating = p("Calificación mínima del pasajero", minPassengerRating, 5.0)
            val parsedDailyTarget = p("Objetivo diario", dailyTarget, 100_000_000.0)
            if (parsedMinPassengerRating != null && parsedMinPassengerRating < 1.0) errors += "Calificación mínima del pasajero"
            if (parsedDailyTarget != null && parsedDailyTarget <= 0.0) errors += "Objetivo diario"
            val upd: (DriverSettings) -> DriverSettings = { s -> s.copy(
                minFare = p("Tarifa mínima", minFare), minGrossPerKm = p("Mínimo por km", minKm), minGrossPerHour = p("Mínimo por hora", minHour),
                costPerKm = p("Costo por km", costKm), fuelLitersPer100Km = p("Consumo", fuelL, 100.0), fuelPricePerLiter = newFuelP,
                fuelPriceUpdatedAt = if (newFuelP == null) null else if (newFuelP != s.fuelPricePerLiter || s.fuelPriceUpdatedAt == null) System.currentTimeMillis() else s.fuelPriceUpdatedAt,
                defaultPickupKm = p("Km recogida", defPkm), defaultPickupMin = p("Min recogida", defPmin),
                emptyReturnFraction = retPct / 100.0, minMarginPct = p("Margen", margin, 100.0), tolerance = tol,
                minPassengerRating = parsedMinPassengerRating?.takeIf { it >= 1.0 },
                dailyTargetArs = parsedDailyTarget?.takeIf { it > 0.0 },
                maxPickupKm = p("Máximo km a recogida", maxPkm, 200.0)?.takeIf { it > 0.0 },
                maxPickupMin = p("Máximo min a recogida", maxPmin, 240.0)?.takeIf { it > 0.0 },
                sportsApiKey = sportsKey.takeIf { it.matches(Regex("^[A-Za-z0-9]{1,64}$")) },
            ) }
            // Se valida primero sin guardar
            upd(saved)
            if (errors.isNotEmpty()) msg = "Valores inválidos: ${errors.distinct().joinToString()}"
            else { scope.launch { g.settingsRepo.update(upd) }; msg = "Guardado." }
        }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("btn_save")) { Text("Guardar ajustes") }
        msg?.let { Text(it, modifier = Modifier.testTag("settings_msg")) }
    }
}
