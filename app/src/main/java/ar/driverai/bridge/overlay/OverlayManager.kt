package ar.driverai.bridge.overlay

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ar.driverai.bridge.DriverAiApp
import ar.driverai.bridge.R
import ar.driverai.bridge.capture.DriverAccessibilityService
import ar.driverai.bridge.capture.EvalRecord
import ar.driverai.bridge.capture.ScreenCaptureService
import ar.driverai.bridge.engine.Fmt
import ar.driverai.bridge.engine.ReasonKind
import ar.driverai.bridge.engine.Verdict
import ar.driverai.bridge.ui.MainActivity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Tarjeta flotante compacta, movible y cerrable. Prioridad de ventana:
 * 1) TYPE_ACCESSIBILITY_OVERLAY si el servicio de lectura está activo (no requiere permiso extra);
 * 2) TYPE_APPLICATION_OVERLAY si el usuario concedió "mostrar sobre otras apps";
 * 3) notificación como alternativa.
 * La tarjeta no es enfocable: no captura el teclado ni bloquea a la app de Uber.
 */
class OverlayManager(private val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var card: View? = null
    private var cardWm: WindowManager? = null
    private var readBtn: View? = null
    private var readWm: WindowManager? = null
    private val hideRunnable = Runnable { hideCard() }
    private val prefs = app.getSharedPreferences("overlay", Context.MODE_PRIVATE)
    private data class MetricTile(val label: String, val value: String, val color: Int)

    @Volatile var isCardVisible = false; private set
    @Volatile var lastShownText: String? = null; private set

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), app.resources.displayMetrics).toInt()

    private fun windowContext(): Pair<Context, Int>? {
        DriverAccessibilityService.instance?.let { return it to WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY }
        if (Settings.canDrawOverlays(app)) {
            val t = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            return app to t
        }
        return null
    }

    fun canShowOverlay(): Boolean = DriverAccessibilityService.instance != null || Settings.canDrawOverlays(app)

    private fun params(type: Int, key: String, defY: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt("${key}_x", dp(12)); y = prefs.getInt("${key}_y", defY)
    }

    private val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale("es", "AR"))

    fun showEvaluation(rec: EvalRecord, compact: Boolean) = main.post {
        val e = rec.evaluation
        val m = e.metrics
        val settings = DriverAiApp.graph.settings.value
        val amber = 0xFFFFC66D.toInt()
        val color = when (e.verdict) {
            Verdict.CONVIENE -> OverlayMetricColor.GREEN
            Verdict.NO_CONVIENE -> OverlayMetricColor.RED
            Verdict.REVISAR -> amber
        }
        val rawPickupKm = e.offer.pickupKm.value
        val rawPickupMin = e.offer.pickupMin.value
        val pickupKm = rawPickupKm ?: m.pickupKm.takeIf { m.pickupAssumed }
        val pickupMin = rawPickupMin ?: m.pickupMin.takeIf { m.pickupAssumed }
        val metricTiles = listOf(
            MetricTile("BRUTO / HORA", m.grossPerHour?.let { Fmt.ars(it) + "/h" } ?: "—", OverlayMetricColor.forThreshold(m.grossPerHour, settings.minGrossPerHour, higherIsBetter = true)),
            MetricTile("BRUTO / KM", m.grossPerKm?.let { Fmt.ars(it) + "/km" } ?: "—", OverlayMetricColor.forThreshold(m.grossPerKm, settings.minGrossPerKm, higherIsBetter = true)),
            MetricTile("HASTA PASAJERO", pickupKm?.let { (if (rawPickupKm == null) "≈" else "") + Fmt.num(it) + " km" } ?: "—", if (rawPickupKm == null && pickupKm != null) amber else OverlayMetricColor.forThreshold(rawPickupKm, settings.maxPickupKm, higherIsBetter = false)),
            MetricTile("TIEMPO DE RECOGIDA", pickupMin?.let { (if (rawPickupMin == null) "≈" else "") + Fmt.num(it, 0) + " min" } ?: "—", if (rawPickupMin == null && pickupMin != null) amber else OverlayMetricColor.forThreshold(rawPickupMin, settings.maxPickupMin, higherIsBetter = false)),
        )
        val rating = e.offer.passengerRating
        val ratingLine = when {
            rating.value != null -> "Calificación del pasajero: ${Fmt.num(rating.value, 2)}/5 · confianza ${(rating.confidence * 100).toInt()} %"
            settings.minPassengerRating != null -> "Calificación del pasajero: no detectada"
            else -> null
        }
        val primaryReason = when (e.verdict) {
            Verdict.CONVIENE -> null
            Verdict.NO_CONVIENE -> e.reasons.firstOrNull { it.kind == ReasonKind.FALLA }?.text
            Verdict.REVISAR -> e.reasons.firstOrNull { it.kind == ReasonKind.AVISO }?.text
        }
        val alternativeLine = e.fareAlternative?.let { alternative ->
            val assumptions = listOfNotNull(
                alternative.assumedPickupKm?.let { "recogida ${Fmt.num(it)}km estimada" },
                alternative.assumedPickupMin?.let { "recogida ${Fmt.num(it, 0)}min estimados" },
                if (alternative.emptyReturnFraction > 0.0) "retorno vacío ${Fmt.num(alternative.emptyReturnFraction * 100.0, 0)}%" else null,
            ).joinToString(", ")
            "Piso teórico (no oferta futura): ${Fmt.ars(alternative.minimumFare)}" + if (assumptions.isNotEmpty()) " ($assumptions)" else ""
        }
        val src = when (rec.source.name) { "ACCESSIBILITY" -> "leído de pantalla"; "OCR" -> "leído por OCR"; else -> "ingreso manual" }
        val destinationLine = rec.destination?.shortLine { at -> timeFmt.format(java.util.Date(at)) }
        val totalLine = listOfNotNull(
            m.totalKm?.let { "${Fmt.num(it)} km totales" },
            m.totalMin?.let { "${Fmt.num(it, 0)} min estimados" },
        ).joinToString(" · ").takeIf { it.isNotBlank() }
        val profitLine = m.estimatedProfitBeforeFixed?.let { "Ganancia estimada antes de costos fijos e impuestos: ${Fmt.ars(it)}" }
        val grossNote = if (m.variableCost == null) "Bruto: no descuenta combustible ni desgaste." else null
        val details = listOfNotNull(totalLine, profitLine, ratingLine, alternativeLine, grossNote, "Lectura: $src${rec.platform?.let { " · ${it.label}" } ?: ""}.")
        val body = listOfNotNull(primaryReason, destinationLine)
        val title = "${e.verdict.label} · ${m.fare?.let { Fmt.ars(it) } ?: "tarifa —"}"
        render(title, color, body, rec.historyId, compact, metricTiles, details, primaryReason)
    }

    fun showMessage(text: String) = main.post { render("DriverAI", 0xFF62E6A4.toInt(), listOf(text), null, false) }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun render(
        title: String,
        color: Int,
        body: List<String>,
        historyId: String?,
        compact: Boolean,
        metrics: List<MetricTile> = emptyList(),
        details: List<String> = emptyList(),
        emphasizedBody: String? = null,
    ) {
        main.removeCallbacks(hideRunnable)
        hideCard()
        val shownDetails = if (compact) emptyList() else details
        val accessibilitySummary = metrics.map { "${it.label}: ${it.value}" } + shownDetails
        val fallbackBody = body + accessibilitySummary
        lastShownText = (listOf(title) + fallbackBody).joinToString("\n")
        val wc = windowContext()
        if (wc == null) { notifyFallback(title, fallbackBody); return }
        val (ctx, type) = wc
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(0xF20D1410.toInt()); cornerRadius = dp(20).toFloat(); setStroke(dp(2), color) }
            minimumWidth = dp(280)
            contentDescription = "Tarjeta DriverAI"
        }
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(ctx).apply {
            text = title; setTextColor(color); textSize = if (compact) 17f else 19f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        fun smallBtn(label: String, desc: String, onClick: () -> Unit) = Button(ctx).apply {
            text = label; contentDescription = desc; textSize = 16f; minWidth = dp(44); minimumWidth = dp(44); minHeight = dp(40); minimumHeight = dp(40)
            setPadding(dp(4), 0, dp(4), 0); setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { onClick() }
        }
        header.addView(smallBtn(if (compact) "+" else "–", "Cambiar modo compacto") {
            DriverAiApp.graph.scope.launch { DriverAiApp.graph.settingsRepo.update { it.copy(overlayCompact = !compact) } }
            DriverAiApp.graph.pipeline.last.value?.let { showEvaluation(it, !compact) }
        })
        header.addView(smallBtn("×", "Cerrar tarjeta") { hideCard() })
        root.addView(header)
        val visibleMetrics = metrics
        visibleMetrics.chunked(2).forEach { rowMetrics ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(5), 0, 0) }
            rowMetrics.forEachIndexed { index, metric ->
                val tileColor = when (metric.color) {
                    OverlayMetricColor.GREEN -> 0xFF14291F.toInt()
                    OverlayMetricColor.RED -> 0xFF301A1C.toInt()
                    0xFFFFC66D.toInt() -> 0xFF2C2517.toInt()
                    else -> 0xFF19221D.toInt()
                }
                val tile = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(9), dp(7), dp(9), dp(7))
                    background = GradientDrawable().apply { setColor(tileColor); cornerRadius = dp(12).toFloat() }
                    contentDescription = "${metric.label}: ${metric.value}"
                }
                tile.addView(TextView(ctx).apply {
                    text = metric.label; setTextColor(0xFFB6C2BB.toInt()); textSize = 9f; maxLines = 1; letterSpacing = 0.04f
                })
                tile.addView(TextView(ctx).apply {
                    text = metric.value; setTextColor(metric.color); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                })
                row.addView(tile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index == 0 && rowMetrics.size > 1) marginEnd = dp(6)
                })
            }
            root.addView(row)
        }
        body.forEach { line ->
            root.addView(TextView(ctx).apply {
                text = line; setTextColor(if (line == emphasizedBody) color else 0xFFECEFF1.toInt())
                textSize = 13f; maxWidth = dp(300); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(5), 0, 0)
            })
        }
        if (!compact) details.forEach { line ->
            root.addView(TextView(ctx).apply {
                text = line; setTextColor(0xFFB6C2BB.toInt()); textSize = 12f; maxWidth = dp(300); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            })
        }
        if (historyId != null && !compact) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(smallBtn("La tomé", "Marcar viaje como tomado") {
                DriverAiApp.graph.scope.launch { DriverAiApp.graph.historyRepo.markTaken(historyId) }
                hideCard()
            })
            row.addView(smallBtn("Corregir", "Abrir DriverAI para corregir datos") {
                app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(MainActivity.EXTRA_TAB, "evaluar").putExtra(MainActivity.EXTRA_PREFILL, true))
                hideCard()
            })
            root.addView(row)
        }
        val lp = params(type, "card", dp(80))
        attachDrag(root, wm, lp, "card")
        try {
            wm.addView(root, lp)
            card = root; cardWm = wm; isCardVisible = true
            main.postDelayed(hideRunnable, AUTO_HIDE_MS)
        } catch (e: Exception) {
            notifyFallback(title, fallbackBody)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(v: View, wm: WindowManager, lp: WindowManager.LayoutParams, key: String) {
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        v.setOnTouchListener { view, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = ev.rawX; ty = ev.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - tx; val dy = ev.rawY - ty
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    lp.x = sx + dx.toInt(); lp.y = sy + dy.toInt()
                    runCatching { wm.updateViewLayout(view, lp) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) prefs.edit().putInt("${key}_x", lp.x).putInt("${key}_y", lp.y).apply() else view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    fun scheduleHide(delayMs: Long) = main.post {
        if (!isCardVisible) return@post
        main.removeCallbacks(hideRunnable); main.postDelayed(hideRunnable, delayMs)
    }

    fun hideCard() {
        val v = card ?: run { isCardVisible = false; return }
        runCatching { cardWm?.removeView(v) }
        card = null; cardWm = null; isCardVisible = false
    }

    fun showReadButton() = main.post {
        if (readBtn != null) return@post
        val wc = windowContext() ?: return@post
        val (ctx, type) = wc
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val b = Button(ctx).apply {
            text = "Leer oferta"; textSize = 15f; setTextColor(0xFF062013.toInt()); contentDescription = "Leer oferta con captura"
            background = GradientDrawable().apply { setColor(0xFF62E6A4.toInt()); cornerRadius = dp(24).toFloat() }
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                // Se ocultan el botón y la tarjeta durante la captura para que no tapen la oferta
                visibility = View.INVISIBLE
                hideCard()
                main.postDelayed({ ScreenCaptureService.instance?.captureOnce(); visibility = View.VISIBLE }, 150)
            }
        }
        val lp = params(type, "read", dp(400))
        attachDrag(b, wm, lp, "read")
        runCatching { wm.addView(b, lp); readBtn = b; readWm = wm }
    }

    fun hideReadButton() = main.post { readBtn?.let { runCatching { readWm?.removeView(it) } }; readBtn = null; readWm = null }

    fun hideAll() = main.post { hideCard(); readBtn?.let { runCatching { readWm?.removeView(it) } }; readBtn = null; readWm = null }

    private fun notifyFallback(title: String, body: List<String>) {
        val nm = NotificationManagerCompat.from(app)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(app, DriverAiApp.CH_RESULT)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body.firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.joinToString("\n")))
            .setTimeoutAfter(AUTO_HIDE_MS).setOnlyAlertOnce(true).build()
        runCatching { (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(7, n) }
    }

    companion object { const val AUTO_HIDE_MS = 25_000L }
}
