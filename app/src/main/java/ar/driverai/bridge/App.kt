package ar.driverai.bridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ar.driverai.bridge.intel.PeakAlerts
import ar.driverai.bridge.ui.MainActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import android.os.Build
import androidx.datastore.preferences.preferencesDataStore
import ar.driverai.bridge.backend.DriverAiApi
import ar.driverai.bridge.backend.LocalWeatherRepository
import ar.driverai.bridge.backend.StrategicRepository
import ar.driverai.bridge.backend.ShiftBriefingRepository
import ar.driverai.bridge.capture.OfferPipeline
import ar.driverai.bridge.data.HistoryRepository
import ar.driverai.bridge.data.MoneyLedgerRepository
import ar.driverai.bridge.data.SettingsRepository
import ar.driverai.bridge.data.VehicleRecordsRepository
import ar.driverai.bridge.engine.DriverSettings
import ar.driverai.bridge.intel.IntelRepository
import ar.driverai.bridge.intel.RadarController
import ar.driverai.bridge.overlay.OverlayManager
import ar.driverai.bridge.voice.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

val Context.driverStore by preferencesDataStore(name = "driverai")

class DriverAiApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        graph = AppGraph(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_CAPTURE, "Lectura de pantalla", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_RESULT, "Resultado de ofertas", NotificationManager.IMPORTANCE_DEFAULT))
            nm.createNotificationChannel(NotificationChannel(CH_RADAR, "Avisos de picos del radar", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    companion object {
        const val CH_CAPTURE = "capture"
        const val CH_RESULT = "result"
        const val CH_RADAR = "radar"
        lateinit var instance: DriverAiApp
            private set
        val graph: AppGraph get() = instance.graph
    }
}

class AppGraph(val app: DriverAiApp) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settingsRepo = SettingsRepository(app.driverStore)
    val historyRepo = HistoryRepository(app.driverStore)
    val walletRepo = MoneyLedgerRepository(app.driverStore)
    val vehicleRepo = VehicleRecordsRepository(app.driverStore)
    val api = DriverAiApi(BuildConfig.BACKEND_BASE_URL)
    val strategic = StrategicRepository(api)
    val weather = LocalWeatherRepository()
    val shiftBriefing = ShiftBriefingRepository(app.driverStore)
    val settings: StateFlow<DriverSettings> = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, DriverSettings())
    val tts: TtsManager by lazy { TtsManager(app) }
    val intelRepo = IntelRepository(app.driverStore)
    /** Fixtures deportivos: clave de prueba solo en build privada; en release requiere clave propia. */
    val radar = RadarController(
        scope = scope,
        repo = intelRepo,
        settings = settings,
        history = historyRepo.entries,
        weather = weather.state,
        sportsAllowed = { s -> BuildConfig.DEBUG || !s.sportsApiKey.isNullOrBlank() },
    )
    val overlay = OverlayManager(app)
    val pipeline = OfferPipeline(this)

    private val sentPeaks = mutableSetOf<String>()

    init {
        // Aviso de picos: se calcula en el teléfono con el pronóstico local; no envía nada a ningún servidor.
        scope.launch {
            combine(radar.forecast, settings, historyRepo.shiftStart) { f, s, shift -> Triple(f, s, shift) }.collect { (f, s, shift) ->
                if (f == null || !s.peakAlerts) return@collect
                val now = System.currentTimeMillis()
                val alert = PeakAlerts.due(f, now, sentPeaks) ?: return@collect
                sentPeaks += alert.key
                notifyPeak(alert.text(now))
                if (s.voiceEnabled && shift != null) tts.speak(alert.text(now), s.voiceVolume)
            }
        }
    }

    private fun notifyPeak(text: String) {
        val nm = NotificationManagerCompat.from(app)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            app, 31,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "radar").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, DriverAiApp.CH_RADAR)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("DriverAI · Radar").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(45 * 60_000L).build()
        runCatching { (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(31, n) }
    }
}
