package ar.driverai.bridge.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.R
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lectura por captura de pantalla (MediaProjection) con consentimiento explícito por sesión.
 * - Captura UN cuadro solo cuando el conductor toca "Leer oferta" (no hay captura continua).
 * - El cuadro se procesa con OCR local y se descarta (bitmap.recycle); nunca se guarda ni se envía.
 * - La sesión se cierra sola tras 10 minutos sin uso, o desde la notificación.
 */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private val idleStop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }

        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(), type)

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = try { mpm.getMediaProjection(code, data) } catch (e: Exception) { null }
        if (p == null) { stopSelf(); return START_NOT_STICKY }
        projection = p
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { handler.post { stopSelf() } }
        }, handler)

        val metrics = resources.displayMetrics
        val scale = minOf(1f, 1080f / metrics.widthPixels)
        width = (metrics.widthPixels * scale).toInt()
        height = (metrics.heightPixels * scale).toInt()
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        display = p.createVirtualDisplay("driverai-ocr", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)

        instance = this
        _running.value = true
        DriverAiApp.graph.overlay.showReadButton()
        resetIdle()
        return START_NOT_STICKY
    }

    private fun resetIdle() { handler.removeCallbacks(idleStop); handler.postDelayed(idleStop, IDLE_MS) }

    /** Captura un único cuadro, lo procesa localmente y lo descarta. */
    fun captureOnce() {
        resetIdle()
        val g = DriverAiApp.graph
        g.pipeline.setStatus(PipelineStatus.LEYENDO)
        val image = reader?.acquireLatestImage()
        if (image == null) { g.pipeline.setStatus(PipelineStatus.ERROR); g.overlay.showMessage("No se pudo capturar la pantalla. Probá de nuevo."); return }
        val bitmap: Bitmap
        try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * width
            val padded = Bitmap.createBitmap(width + rowPadding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            bitmap = Bitmap.createBitmap(padded, 0, 0, width, height)
            if (bitmap !== padded) padded.recycle()
        } finally { image.close() }
        g.scope.launch {
            try {
                val lines = OcrEngine.recognizeLines(bitmap)
                val found = g.pipeline.onScreenText(lines, DataSource.OCR)
                if (!found) {
                    val message = if (lines.isEmpty()) {
                        "No se reconoció texto en la captura. Asegurate de que la oferta esté visible e intentá de nuevo."
                    } else {
                        "Se leyó texto, pero no se reconoció una oferta. Dejá visibles la tarifa, el detalle del viaje y «Me interesa» o «Aceptar»."
                    }
                    g.overlay.showMessage(message)
                }
            } catch (e: Exception) {
                g.pipeline.setStatus(PipelineStatus.ERROR)
                g.overlay.showMessage("Error de OCR. Probá de nuevo.")
            } finally { bitmap.recycle() }
        }
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, DriverAiApp.CH_CAPTURE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("DriverAI: lectura por captura activa")
            .setContentText("Solo captura cuando tocás \"Leer oferta\". Nada se guarda.")
            .setContentIntent(open)
            .addAction(0, "Detener", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleStop)
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
        if (instance === this) instance = null
        _running.value = false
        DriverAiApp.graph.overlay.hideReadButton()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "ar.driverai.bridge.STOP_CAPTURE"
        private const val NOTIF_ID = 42
        private const val IDLE_MS = 10 * 60 * 1000L
        @Volatile var instance: ScreenCaptureService? = null
            private set
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(ctx: Context, code: Int, data: Intent) {
            val i = Intent(ctx, ScreenCaptureService::class.java).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, ScreenCaptureService::class.java).setAction(ACTION_STOP)) }
    }
}
