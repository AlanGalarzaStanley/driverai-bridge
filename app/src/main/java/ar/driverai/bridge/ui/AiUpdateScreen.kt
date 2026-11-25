package ar.driverai.bridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.intel.AiReport
import ar.driverai.bridge.intel.Cities
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla "Actualizar con IA". El conductor elige su IA (ChatGPT, Gemini, Perplexity, Copilot…),
 * DriverAI solo arma el pedido y lee la respuesta que el conductor pega o comparte.
 */
@Composable
fun AiUpdateScreen(sharedText: String?, onSharedConsumed: () -> Unit, onBack: () -> Unit) {
    val g = DriverAiApp.graph
    val ctx = LocalContext.current
    val settings by g.settings.collectAsState()
    val cache by g.radar.cache.collectAsState()
    val city = Cities.byId(settings.cityId)
    val now = remember { System.currentTimeMillis() }
    val prompt = remember(city.id, now) { AiReport.prompt(city, now) }
    var answer by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AiReport.Result?>(null) }
    var copied by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val hm = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-AR")) }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            answer = sharedText.take(AiReport.MAX_INPUT_CHARS)
            preview = AiReport.parse(answer, city, System.currentTimeMillis())
            onSharedConsumed()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("ai_back")) { Text("Volver") }
            Text("Actualizar con IA", style = MaterialTheme.typography.titleLarge, color = AccentMint)
        }
        SectionCaption("Tu IA de confianza busca en la web los eventos, paros y alertas de hoy. Vos pegás la respuesta y el radar se actualiza. DriverAI no envía tu ubicación ni tus datos.")

        Section("1 · Pedile el informe a tu IA") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Pedido DriverAI", prompt))
                        copied = true
                    },
                    modifier = Modifier.weight(1f).height(50.dp).testTag("ai_copy"),
                ) { Text(if (copied) "Copiado" else "Copiar pedido") }
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, prompt)
                        runCatching { ctx.startActivity(Intent.createChooser(send, "Abrir en tu IA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.weight(1f).height(50.dp).testTag("ai_share"),
                ) { Text("Enviar a mi IA") }
            }
            SectionCaption("Funciona mejor con una IA que busque en la web (Perplexity, ChatGPT con búsqueda, Gemini).")
            TextButton(onClick = { showPrompt = !showPrompt }) { Text(if (showPrompt) "Ocultar pedido" else "Ver el pedido") }
            if (showPrompt) Text(prompt, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.testTag("ai_prompt"))
        }

        Section("2 · Pegá la respuesta") {
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it.take(AiReport.MAX_INPUT_CHARS); preview = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("ai_answer"),
                label = { Text("Respuesta de la IA") },
                minLines = 4,
                maxLines = 10,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                OutlinedButton(
                    onClick = {
                        val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                        val txt = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                        if (txt.isNotBlank() && txt != prompt) { answer = txt.take(AiReport.MAX_INPUT_CHARS); preview = AiReport.parse(answer, city, System.currentTimeMillis()) }
                        else message = "El portapapeles no tiene la respuesta. Copiala en tu IA y volvé a tocar."
                    },
                    modifier = Modifier.weight(1f).testTag("ai_paste"),
                ) { Text("Pegar") }
                Button(
                    enabled = answer.isNotBlank(),
                    onClick = { preview = AiReport.parse(answer, city, System.currentTimeMillis()); message = null },
                    modifier = Modifier.weight(1f).testTag("ai_check"),
                ) { Text("Revisar") }
            }
            SectionCaption("Atajo: en la app de tu IA tocá Compartir → DriverAI y la respuesta llega sola.")
            message?.let { Text(it, color = Amber, style = MaterialTheme.typography.bodySmall) }
        }

        preview?.let { r ->
            Section("3 · Confirmá qué se carga", Modifier.fillMaxWidth().testTag("ai_preview")) {
                if (!r.ok) {
                    Text(r.error!!, color = Amber, modifier = Modifier.testTag("ai_error"))
                    return@Section
                }
                if (r.signals.isEmpty()) Text("La respuesta no trae señales que se puedan usar.", color = Amber)
                r.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                r.signals.forEach { s ->
                    val zone = city.zone(s.zoneId)?.name ?: "Toda la ciudad"
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${hm.format(Date(s.startAt))} · ${s.title}", fontWeight = FontWeight.SemiBold)
                        SectionCaption("${s.kind.label} · $zone · ${s.sourceName.removePrefix("IA · ")}${if (s.sourceUrl == null) " · sin enlace" else ""}")
                    }
                }
                if (r.rejected.isNotEmpty()) {
                    Text("Descartadas (${r.rejected.size}):", style = MaterialTheme.typography.labelLarge, color = Amber, modifier = Modifier.padding(top = 6.dp))
                    r.rejected.take(8).forEach { SectionCaption("• ${it.title}: ${it.reason}") }
                }
                SectionCaption("Se marcan como «Informe IA (verificar)» y pesan menos que una fuente pública. Reemplazan el informe anterior.")
                Button(
                    enabled = r.signals.isNotEmpty(),
                    onClick = {
                        g.radar.importAi(r)
                        message = "Listo: ${r.signals.size} señales cargadas al radar."
                        preview = null
                        answer = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("ai_import"),
                ) { Text("Cargar ${r.signals.size} al radar") }
            }
        }

        Section("Informe actual") {
            val at = cache.aiImportedAt
            if (at == null) {
                SectionCaption("Todavía no cargaste un informe hoy.")
            } else {
                val stale = System.currentTimeMillis() - at > AiReport.STALE_AFTER_MS
                StatusPill(if (stale) "INFORME VIEJO" else "INFORME DE LAS ${hm.format(Date(at))}", if (stale) Amber else AccentMint)
                Text("${cache.aiSignals.size} señales activas", modifier = Modifier.padding(top = 6.dp).testTag("ai_current_count"))
                cache.aiSummary?.let { SectionCaption(it) }
                TextButton(onClick = { g.radar.clearAi() }, modifier = Modifier.testTag("ai_clear")) { Text("Borrar informe") }
            }
        }
    }
}
