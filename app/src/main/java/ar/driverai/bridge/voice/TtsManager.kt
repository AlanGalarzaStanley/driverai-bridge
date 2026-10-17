package ar.driverai.bridge.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Síntesis de voz en español argentino con alternativas (es-419, es) si el dispositivo no la tiene. */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile var ready = false; private set
    @Volatile var localeUsed: Locale? = null; private set
    @Volatile var initError: String? = null; private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) { initError = "Motor de voz no disponible"; return }
        val candidates = listOf(Locale("es", "AR"), Locale("es", "419"), Locale("es", "US"), Locale("es", "ES"), Locale("es"))
        val chosen = candidates.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        if (chosen == null) { initError = "No hay voz en español instalada"; return }
        tts.language = chosen
        localeUsed = chosen
        ready = true
    }

    fun speak(text: String, volume: Float) {
        if (!ready || text.isBlank()) return
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        tts.speak(text.take(220), TextToSpeech.QUEUE_FLUSH, params, "driverai-${System.nanoTime()}")
    }

    fun stop() { tts.stop() }
    fun shutdown() { tts.shutdown() }
}
