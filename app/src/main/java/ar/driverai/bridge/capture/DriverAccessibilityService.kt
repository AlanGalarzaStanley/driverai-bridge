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
import ar.driverai.bridge.engine.RidePlatform
import ar.driverai.bridge.ui.SimulatorActivity

/**
 * Lee SOLO el texto visible de las apps de conductor habilitadas (Uber, DiDi, Cabify, inDrive, Bolt)
 * y del simulador interno en debug. Cada app puede desactivarse en Ajustes.
 * No realiza gestos, no toca botones, no acepta ni rechaza viajes: la configuración XML
 * no declara canPerformGestures y el código nunca llama a performAction.
 */
class DriverAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastScanAt = 0L
    private var offerOnScreen = false
    private var pendingEventPackage: String? = null
    /** Texto efímero del evento: nunca se persiste ni se envía fuera del teléfono. */
    private val pendingEventTexts = linkedSetOf<String>()
    private val scanRunnable = Runnable { scan() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            packageNames = watchedPackages(this@DriverAccessibilityService).toTypedArray()
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 250
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        DriverAiApp.graph.pipeline.setStatus(PipelineStatus.SIN_OFERTA)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in activePackages(this) || (pkg == packageName && !SimulatorActivity.isVisible)) {
            pendingEventTexts.clear()
            pendingEventPackage = null
            handler.removeCallbacks(scanRunnable)
            if (offerOnScreen) DriverAiApp.graph.pipeline.onOfferGone()
            offerOnScreen = false
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) pendingEventTexts.clear()
        pendingEventPackage = pkg
        event.text.orEmpty().forEach { addEventText(it.toString()) }
        event.contentDescription?.toString()?.let(::addEventText)
        handler.removeCallbacks(scanRunnable)
        // Cambio de ventana (aparece una tarjeta) → rápido. Cambios de contenido (mapa animado)
        // sin oferta reciente → se espacian los escaneos para ahorrar batería.
        val urgentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        val minGap = if (urgentChange || offerOnScreen) MIN_INTERVAL_MS else IDLE_INTERVAL_MS
        val since = System.currentTimeMillis() - lastScanAt
        handler.postDelayed(scanRunnable, if (since > minGap) DEBOUNCE_MS else minGap - since)
    }

    private fun scan() {
        lastScanAt = System.currentTimeMillis()
        val eventTexts = pendingEventTexts.toList()
        val eventPackage = pendingEventPackage
        pendingEventTexts.clear()
        pendingEventPackage = null
        val root = rootInActiveWindow
        val rootPackage = root?.packageName?.toString()
        if (rootPackage != null && rootPackage != eventPackage) {
            offerOnScreen = false
            DriverAiApp.graph.pipeline.onOfferGone()
            return
        }
        if (rootPackage != eventPackage || root == null) {
            if (eventTexts.isEmpty()) {
                offerOnScreen = false
                DriverAiApp.graph.pipeline.onOfferGone()
                return
            }
            val found = DriverAiApp.graph.pipeline.onScreenText(eventTexts, DataSource.ACCESSIBILITY, RidePlatform.fromPackage(eventPackage))
            offerOnScreen = found
            if (!found) DriverAiApp.graph.pipeline.onOfferGone()
            return
        }
        val pipeline = DriverAiApp.graph.pipeline
        val texts = (collectTexts(root) + eventTexts).distinct()
        val found = pipeline.onScreenText(texts, DataSource.ACCESSIBILITY, RidePlatform.fromPackage(rootPackage))
        offerOnScreen = found
        if (!found) pipeline.onOfferGone()
    }

    override fun onInterrupt() {}

    private fun addEventText(text: String) {
        val safeText = text.trim().take(MAX_EVENT_TEXT_CHARS)
        if (safeText.isBlank()) return
        if (pendingEventTexts.size >= MAX_EVENT_TEXTS) pendingEventTexts.remove(pendingEventTexts.first())
        pendingEventTexts += safeText
    }

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
        private const val MAX_EVENT_TEXTS = 80
        private const val MAX_EVENT_TEXT_CHARS = 300
        @Volatile var instance: DriverAccessibilityService? = null
            private set

        /** Paquetes declarados al sistema (todas las apps compatibles). */
        fun watchedPackages(ctx: Context): Set<String> {
            val apps = RidePlatform.READABLE.mapNotNull { it.packageName }.toSet()
            return if (BuildConfig.SIMULATOR_WATCH) apps + ctx.packageName else apps
        }

        /** Paquetes que el conductor dejó habilitados en Ajustes. */
        fun activePackages(ctx: Context): Set<String> {
            val s = DriverAiApp.graph.settings.value
            val apps = RidePlatform.READABLE.filter { s.platformEnabled(it) }.mapNotNull { it.packageName }.toSet()
            return if (BuildConfig.SIMULATOR_WATCH) apps + ctx.packageName else apps
        }

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
