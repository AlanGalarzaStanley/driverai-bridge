package ar.driverai.bridge.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import ar.driverai.bridge.BuildConfig
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.engine.DataSource
import ar.driverai.bridge.ui.SimulatorActivity

/**
 * Lee SOLO el texto visible de la app de conductor de Uber (y del simulador interno en debug).
 * No realiza gestos, no toca botones, no acepta ni rechaza viajes: la configuración XML
 * no declara canPerformGestures y el código nunca llama a performAction.
 */
class DriverAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastScanAt = 0L
    private var offerOnScreen = false
    private val scanRunnable = Runnable { scan() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            packageNames = watchedPackages(this@DriverAccessibilityService).toTypedArray()
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 250
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        DriverAiApp.graph.pipeline.setStatus(PipelineStatus.SIN_OFERTA)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in watchedPackages(this)) return
        if (pkg == packageName && !SimulatorActivity.isVisible) return
        handler.removeCallbacks(scanRunnable)
        // Cambio de ventana (aparece una tarjeta) → rápido. Cambios de contenido (mapa animado)
        // sin oferta reciente → se espacian los escaneos para ahorrar batería.
        val stateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val minGap = if (stateChange || offerOnScreen) MIN_INTERVAL_MS else IDLE_INTERVAL_MS
        val since = System.currentTimeMillis() - lastScanAt
        handler.postDelayed(scanRunnable, if (since > minGap) DEBOUNCE_MS else minGap - since)
    }

    private fun scan() {
        lastScanAt = System.currentTimeMillis()
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString()
        val pipeline = DriverAiApp.graph.pipeline
        if (pkg == null || pkg !in watchedPackages(this) || (pkg == packageName && !SimulatorActivity.isVisible)) {
            pipeline.onOfferGone(); return
        }
        val texts = collectTexts(root)
        val found = pipeline.onScreenText(texts, DataSource.ACCESSIBILITY)
        offerOnScreen = found
        if (!found) pipeline.onOfferGone()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        if (instance === this) instance = null
        DriverAiApp.graph.overlay.hideAll()
        super.onDestroy()
    }

    companion object {
        const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val DEBOUNCE_MS = 350L
        private const val MIN_INTERVAL_MS = 300L
        private const val IDLE_INTERVAL_MS = 900L
        private const val MAX_NODES = 400
        @Volatile var instance: DriverAccessibilityService? = null
            private set

        fun watchedPackages(ctx: Context): Set<String> =
            if (BuildConfig.SIMULATOR_WATCH) setOf(UBER_DRIVER_PACKAGE, ctx.packageName) else setOf(UBER_DRIVER_PACKAGE)

        /** Recorrido acotado del árbol (máx. 400 nodos) para no consumir CPU. */
        fun collectTexts(root: AccessibilityNodeInfo): List<String> {
            val out = ArrayList<String>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES) {
                val n = queue.removeFirst()
                visited++
                if (n.isVisibleToUser) {
                    n.text?.toString()?.takeIf { it.isNotBlank() }?.let { out += it }
                    n.contentDescription?.toString()?.takeIf { it.isNotBlank() && it != n.text?.toString() }?.let { out += it }
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            return out
        }

        fun isEnabled(ctx: Context): Boolean {
            val expected = ComponentName(ctx, DriverAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
