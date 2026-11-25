package ar.driverai.bridge.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.copilot.CopilotContext
import ar.driverai.bridge.copilot.CopilotEngine
import ar.driverai.bridge.copilot.Intent as CopilotIntent
import ar.driverai.bridge.data.ShiftCalculator
import ar.driverai.bridge.data.MoneyLedgerRepository
import ar.driverai.bridge.data.VehicleRecordsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class CopilotMessage(val fromDriver: Boolean, val text: String)

/** Historial de sesión en memoria; no persiste texto libre ni lo envía a servidores. */
private object CopilotConversation {
    val messages = mutableStateListOf<CopilotMessage>()
    fun append(message: CopilotMessage) {
        messages += message
        if (messages.size > 80) messages.removeAt(0)
    }
}

@Composable
fun CopilotScreen(onOpenAi: () -> Unit = {}, onBack: () -> Unit) {
    val g = DriverAiApp.graph
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val settings by g.settings.collectAsState()
    val strategic by g.strategic.state.collectAsState()
    val latest by g.pipeline.last.collectAsState()
    val history by g.historyRepo.entries.collectAsState(initial = emptyList())
    val shiftStart by g.historyRepo.shiftStart.collectAsState(initial = null)
    val moneyEntries by g.walletRepo.entries.collectAsState(initial = emptyList())
    val odometerEntries by g.vehicleRepo.odometerEntries.collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    val today = remember(clock) { LocalDate.now() }
    val moneyToday = remember(moneyEntries, today) { MoneyLedgerRepository.summarize(moneyEntries, today) }
    val odometerToday = remember(odometerEntries, today) { VehicleRecordsRepository.entryForDay(odometerEntries, today) }

    LaunchedEffect(Unit) { while (true) { clock = System.currentTimeMillis(); delay(30_000) } }
    LaunchedEffect(CopilotConversation.messages.size, busy) {
        if (CopilotConversation.messages.isNotEmpty()) listState.animateScrollToItem(CopilotConversation.messages.lastIndex)
    }

    fun ask(query: String) {
        val clean = query.trim().takeIf { it.isNotEmpty() } ?: return
        CopilotConversation.append(CopilotMessage(true, clean))
        input = ""
        focusManager.clearFocus()
        busy = true
        scope.launch {
            try {
                val intent = CopilotEngine.intentOf(clean)
                if (intent == CopilotIntent.DONDE_ESPERAR && settings.currentZone != null) {
                    g.strategic.refreshPosicion(settings.currentZone!!)
                }
                if ((intent == CopilotIntent.EVENTOS || intent == CopilotIntent.CERCA_CASA) && strategic.isStale(System.currentTimeMillis())) {
                    g.strategic.refreshReporte()
                }
                val now = System.currentTimeMillis()
                val shiftStats = ShiftCalculator.compute(history, shiftStart, now)
                val copilotContext = CopilotContext(
                    latest?.evaluation, shiftStats, g.strategic.state.value, settings, now,
                    moneyToday = moneyToday,
                    odometerTodayKm = odometerToday?.drivenKm,
                    radar = g.radar.forecast.value,
                    bestHours = ar.driverai.bridge.insights.Insights.bestHours(history, java.time.ZoneId.systemDefault())
                        .mapNotNull { (h, b) -> b.avgGrossPerHour?.let { Triple(h, it, b.offers) } },
                )
                val answer = CopilotEngine.answer(clean, copilotContext)
                CopilotConversation.append(CopilotMessage(false, answer))
                if (settings.voiceEnabled) g.tts.speak(answer, settings.voiceVolume)
            } catch (_: Exception) {
                CopilotConversation.append(CopilotMessage(false, "No pude completar esa consulta. Podés intentarlo de nuevo o revisar la conexión; la evaluación de una oferta sigue disponible localmente."))
            } finally {
                busy = false
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !recognized.isNullOrBlank()) ask(recognized)
    }

    run {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("copilot_back")) { Text("Inicio") }
                Column(Modifier.weight(1f)) {
                    Text("Copiloto", style = MaterialTheme.typography.titleLarge, color = AccentMint)
                    Text("Contexto de tu jornada", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                TextButton(onClick = onOpenAi, modifier = Modifier.testTag("external_report")) { Text("Datos con IA") }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text("Respuestas locales por reglas", style = MaterialTheme.typography.labelLarge, color = AccentMint, fontWeight = FontWeight.SemiBold)
                    Text("Este chat no invoca un LLM desde Android. Usa la evaluación y el contexto disponible; si falta información, lo indica.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            if (CopilotConversation.messages.isEmpty()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 18.dp)) {
                    Text("¿Qué necesitás decidir en este turno?", style = MaterialTheme.typography.titleMedium)
                    Text("Preguntá por la última oferta, tu meta, la caja anotada o la zona elegida. La lectura y evaluación no aceptan ni rechazan viajes.", style = MaterialTheme.typography.bodyMedium, color = TextMuted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                    listOf("¿Cómo va mi jornada hoy?", "¿Cómo va mi objetivo diario?", "¿Me conviene la última oferta?", "¿Por qué da ese resultado?", "¿Cuánto llevo por hora?", "¿Dónde me conviene esperar?", "¿Hay partidos o paros hoy?").forEach { suggestion ->
                        OutlinedButton(onClick = { ask(suggestion) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) { Text(suggestion) }
                    }
                    Text("Para sumar eventos, paros o vuelos de hoy, tocá «Datos con IA»: tu IA los busca y el radar se actualiza.", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(CopilotConversation.messages) { index, message ->
                        MessageBubble(message, index)
                    }
                    if (busy) item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(10.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
                            Text("Revisando el contexto disponible…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).testTag("copilot_input"),
                    label = { Text("Escribí tu consulta") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { ask(input) }),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { ask(input) }, enabled = !busy && input.isNotBlank(), modifier = Modifier.testTag("copilot_send")) { Text("Enviar") }
                    TextButton(onClick = {
                        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué querés consultar?")
                        try { speechLauncher.launch(speechIntent) } catch (_: ActivityNotFoundException) {
                            CopilotConversation.append(CopilotMessage(false, "El reconocimiento de voz no está disponible en este dispositivo. Escribí tu consulta."))
                        }
                    }, modifier = Modifier.testTag("copilot_voice")) { Text("Voz") }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MessageBubble(message: CopilotMessage, index: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromDriver) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (message.fromDriver) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(if (message.fromDriver) 22.dp else 16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (message.fromDriver) AccentMint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            ),
            modifier = Modifier.fillMaxWidth(if (message.fromDriver) 0.86f else 0.94f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(if (message.fromDriver) "VOS" else "COPILOTO · MOTOR LOCAL", style = MaterialTheme.typography.labelSmall, color = if (message.fromDriver) AccentMint else AccentBlue)
                Text(message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp).testTag(if (message.fromDriver) "copilot_user_$index" else "copilot_answer_$index"))
            }
        }
    }
}
