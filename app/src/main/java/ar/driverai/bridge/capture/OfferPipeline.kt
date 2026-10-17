package ar.driverai.bridge.capture

import ar.driverai.bridge.AppGraph
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.engine.Evaluation
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.OfferData
import ar.driverai.bridge.engine.OfferEvaluator
import ar.driverai.bridge.engine.OfferTextParser
import ar.driverai.bridge.engine.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PipelineStatus(val label: String) {
    INACTIVO("Inactivo"), LEYENDO("Leyendo oferta"), CALCULANDO("Calculando"), RESULTADO("Resultado listo"),
    SIN_OFERTA("Sin oferta en pantalla"), ERROR("Error de lectura"), PERMISO_PENDIENTE("Permiso pendiente"),
}

data class EvalRecord(val evaluation: Evaluation, val at: Long, val source: DataSource, val notes: List<String>)

/**
 * Orquesta: texto visible → parser → motor → tarjeta flotante / historial / voz.
 * Evita duplicados (misma oferta en ventana de 90 s) y nunca interactúa con la app de origen.
 */
class OfferPipeline(private val g: AppGraph, private val clock: () -> Long = System::currentTimeMillis) {
    private val _status = MutableStateFlow(PipelineStatus.INACTIVO)
    val status: StateFlow<PipelineStatus> = _status.asStateFlow()
    private val _last = MutableStateFlow<EvalRecord?>(null)
    val last: StateFlow<EvalRecord?> = _last.asStateFlow()

    /** Solo en memoria y solo si el usuario activa el diagnóstico. Nunca se persiste. */
    val diagnosticsEnabled = MutableStateFlow(false)
    private val _lastFragments = MutableStateFlow<List<String>>(emptyList())
    val lastFragments: StateFlow<List<String>> = _lastFragments.asStateFlow()
    @Volatile var scans = 0L; private set
    @Volatile var offersDetected = 0L; private set

    private var lastSignature: String? = null
    private var lastSignatureAt = 0L

    fun setStatus(s: PipelineStatus) { _status.value = s }

    /** @return true si se reconoció una oferta (nueva o repetida). */
    fun onScreenText(fragments: List<String>, source: DataSource): Boolean {
        scans++
        if (diagnosticsEnabled.value) _lastFragments.value = fragments.take(200)
        _status.value = PipelineStatus.LEYENDO
        val parsed = OfferTextParser.parse(fragments, source)
        if (!parsed.isLikelyOffer) {
            _status.value = PipelineStatus.SIN_OFERTA
            return false
        }
        val o = parsed.offer
        val sig = listOf(o.fare.value, o.tripKm.value, o.tripMin.value, o.pickupKm.value, o.pickupMin.value).joinToString("|")
        val now = clock()
        if (sig == lastSignature && now - lastSignatureAt < DEDUPE_MS) {
            _status.value = PipelineStatus.RESULTADO
            return true
        }
        lastSignature = sig; lastSignatureAt = now
        offersDetected++
        _status.value = PipelineStatus.CALCULANDO
        val eval = OfferEvaluator.evaluate(o, g.settings.value, now)
        publish(EvalRecord(eval, now, source, parsed.notes), showOverlay = true)
        return true
    }

    fun onOfferGone() {
        if (_status.value == PipelineStatus.RESULTADO) _status.value = PipelineStatus.SIN_OFERTA
        g.overlay.scheduleHide(OFFER_GONE_HIDE_MS)
    }

    fun evaluateManual(offer: OfferData, showOverlay: Boolean = false): EvalRecord {
        val now = clock()
        val rec = EvalRecord(OfferEvaluator.evaluate(offer, g.settings.value, now), now, DataSource.MANUAL, emptyList())
        publish(rec, showOverlay)
        return rec
    }

    private fun publish(rec: EvalRecord, showOverlay: Boolean) {
        _last.value = rec
        _status.value = PipelineStatus.RESULTADO
        val s = g.settings.value
        if (showOverlay) g.overlay.showEvaluation(rec, s.overlayCompact)
        if (s.historyEnabled) g.scope.launch { g.historyRepo.add(rec.evaluation, rec.at, rec.source) }
        if (s.voiceEnabled && s.speakVerdicts && rec.source != DataSource.MANUAL) g.tts.speak(shortSpeech(rec.evaluation), s.voiceVolume)
    }

    companion object {
        const val DEDUPE_MS = 90_000L
        const val OFFER_GONE_HIDE_MS = 8_000L

        fun shortSpeech(e: Evaluation): String = when (e.verdict) {
            Verdict.CONVIENE -> "Conviene." + (e.metrics.grossPerKm?.let { " ${Fmt.ars(it).drop(1)} pesos por kilómetro." } ?: "")
            Verdict.NO_CONVIENE -> "No conviene."
            Verdict.REVISAR -> "Revisar la oferta."
        }
    }
}
