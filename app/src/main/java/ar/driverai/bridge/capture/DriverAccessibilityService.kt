package ar.driverai.bridge.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
 *
 * En teléfonos viejos Uber a menudo no expone todo el texto al árbol de accesibilidad.
 * Si hay indicios de oferta y la captura de pantalla ya está autorizada, se dispara OCR
 * automático una vez (sin tocar el botón) para completar la lectura.
 */
class DriverAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastScanAt = 0L
    private var offerOnScreen = false
    private var pendingEventPackage: String? = null
    private var lastTargetPackage: String? = null
    private var retryCount = 0
    private var scanPending = false
    private var lastOcrAssistAt = 0L
    /** Texto efímero del evento: nunca se persiste ni se envía fuera del teléfono. */
    private val pendingEventTexts = linkedSetOf<String>()
    private val scanRunnable = Runnable { scanPending = false; scan() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            packageNames = watchedPackages(this@DriverAccessibilityService).toTypedArray()
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 120
            flags = flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        DriverAiApp.graph.pipeline.setStatus(PipelineStatus.SIN_OFERTA)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in activePackages(this) || (pkg == packageName && !SimulatorActivity.isVisible)) {
            pendingEventTexts.clear()
            pendingEventPackage = null
            lastTargetPackage = null
            retryCount = 0
            handler.removeCallbacks(scanRunnable)
            scanPending = false
            if (offerOnScreen) DriverAiApp.graph.pipeline.onOfferGone()
            offerOnScreen = false
            return
        }
        if (lastTargetPackage != null && lastTargetPackage != pkg) pendingEventTexts.clear()
        if (!scanPending) retryCount = 0
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) pendingEventTexts.clear()
        pendingEventPackage = pkg
        lastTargetPackage = pkg
        event.text.orEmpty().forEach { addEventText(it.toString()) }
        event.contentDescription?.toString()?.let(::addEventText)
        val urgentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        val minGap = if (urgentChange || offerOnScreen) MIN_INTERVAL_MS else IDLE_INTERVAL_MS
        val since = System.currentTimeMillis() - lastScanAt
        scheduleScan(if (since > minGap) DEBOUNCE_MS else minGap - since)
    }

    private fun scan() {
        lastScanAt = System.currentTimeMillis()
        val eventTexts = pendingEventTexts.toList()
        pendingEventTexts.clear()
        val eventPackage = pendingEventPackage ?: lastTargetPackage
        pendingEventPackage = null
        if (eventPackage == null || eventPackage !in activePackages(this)) {
            finishScan(found = false, offerHints = false)
            return
        }
        val root = rootInActiveWindow
        val rootPackage = root?.packageName?.toString()
        val treeTexts = buildList {
            if (root != null && rootPackage == eventPackage) addAll(collectTexts(root, requireVisible = true))
            // En algunos equipos viejos isVisibleToUser es falso aunque el texto esté en pantalla.
            if (isEmpty() && root != null && rootPackage == eventPackage) {
                addAll(collectTexts(root, requireVisible = false))
            }
            addAll(collectFromOtherWindows(eventPackage))
        }.distinct()
        val texts = AccessibleTextSnapshot.select(eventPackage, rootPackage, eventTexts, treeTexts)
        val found = texts.isNotEmpty() && DriverAiApp.graph.pipeline.onScreenText(
            texts,
            DataSource.ACCESSIBILITY,
            RidePlatform.fromPackage(eventPackage),
        )
        val hints = !found && looksLikeOfferHint(texts.ifEmpty { eventTexts })
        finishScan(found = found, offerHints = hints)
    }

    private fun finishScan(found: Boolean, offerHints: Boolean) {
        if (found) {
            offerOnScreen = true
            retryCount = 0
            return
        }
        if (lastTargetPackage != null && retryCount < MAX_RETRIES) {
            retryCount++
            scheduleScan(RESCAN_DELAY_MS)
            return
        }
        if (offerHints) {
            maybeOcrAssist()
            // No marcamos offerOnScreen: el OCR confirmará o no.
            return
        }
        offerOnScreen = false
        DriverAiApp.graph.pipeline.onOfferGone()
    }

    /**
     * Solo si el conductor ya autorizó la captura de pantalla (botón LEER OFERTA visible).
     * No pide permisos nuevos ni captura en bucle: una vez cada [OCR_ASSIST_COOLDOWN_MS].
     */
    private fun maybeOcrAssist() {
        val now = System.currentTimeMillis()
        if (now - lastOcrAssistAt < OCR_ASSIST_COOLDOWN_MS) return
        val capture = ScreenCaptureService.instance ?: return
        lastOcrAssistAt = now
        // Ocultar overlays un instante para no contaminar el OCR.
        DriverAiApp.graph.overlay.hideCard()
        handler.postDelayed({
            capture.captureOnce()
        }, 120)
    }

    private fun looksLikeOfferHint(texts: List<String>): Boolean {
        if (texts.isEmpty()) return false
        val joined = texts.joinToString(" ")
        val hasMoney = Regex("""(?i)(?:\d[\d.,]*\s*ARS|ARS\s*\d|\$\s*\d)""").containsMatchIn(joined)
        val hasCta = Regex(
            """(?i)(?:me interesa|aceptar|accept|toca para aceptar|tap to accept|solicitud|nuevo viaje|offer)""",
        ).containsMatchIn(joined)
        val hasTimeDist = Regex("""(?i)\d{1,3}\s*min""").containsMatchIn(joined) &&
            Regex("""(?i)\d+(?:[.,]\d+)?\s*km""").containsMatchIn(joined)
        return (hasMoney && (hasCta || hasTimeDist)) || (hasCta && hasTimeDist)
    }

    private fun collectFromOtherWindows(eventPackage: String): List<String> {
        if (Build.VERSION.SDK_INT < 21) return emptyList()
        val out = ArrayList<String>()
        val windows = try { windows } catch (_: Exception) { null } ?: return emptyList()
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = try { w.root } catch (_: Exception) { null } ?: continue
            val pkg = root.packageName?.toString()
            if (pkg != eventPackage) continue
            out += collectTexts(root, requireVisible = false)
        }
        return out
    }

    private fun scheduleScan(delayMs: Long) {
        handler.removeCallbacks(scanRunnable)
        scanPending = true
        handler.postDelayed(scanRunnable, delayMs.coerceAtLeast(0L))
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
        scanPending = false
        if (instance === this) instance = null
        DriverAiApp.graph.overlay.hideAll()
        super.onDestroy()
    }

    companion object {
        const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"
        private const val DEBOUNCE_MS = 220L
        private const val MIN_INTERVAL_MS = 250L
        private const val IDLE_INTERVAL_MS = 700L
        private const val RESCAN_DELAY_MS = 180L
        private const val MAX_RETRIES = 4
        private const val MAX_NODES = 600
        private const val MAX_EVENT_TEXTS = 100
        private const val MAX_EVENT_TEXT_CHARS = 400
        private const val OCR_ASSIST_COOLDOWN_MS = 4_000L
        @Volatile var instance: DriverAccessibilityService? = null
            private set

        fun watchedPackages(ctx: Context): Set<String> {
            val apps = RidePlatform.READABLE.mapNotNull { it.packageName }.toSet()
            return if (BuildConfig.SIMULATOR_WATCH) apps + ctx.packageName else apps
        }

        fun activePackages(ctx: Context): Set<String> {
            val s = DriverAiApp.graph.settings.value
            val apps = RidePlatform.READABLE.filter { s.platformEnabled(it) }.mapNotNull { it.packageName }.toSet()
            return if (BuildConfig.SIMULATOR_WATCH) apps + ctx.packageName else apps
        }

        fun collectTexts(root: AccessibilityNodeInfo, requireVisible: Boolean = true): List<String> {
            val out = ArrayList<String>()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < MAX_NODES) {
                val n = queue.removeFirst()
                visited++
                if (!requireVisible || n.isVisibleToUser) {
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
