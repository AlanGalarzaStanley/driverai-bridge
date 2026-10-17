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

    fun showEvaluation(rec: EvalRecord, compact: Boolean) = main.post {
        val e = rec.evaluation
        val m = e.metrics
        val color = when (e.verdict) { Verdict.CONVIENE -> 0xFF2E7D32.toInt(); Verdict.NO_CONVIENE -> 0xFFC62828.toInt(); Verdict.REVISAR -> 0xFFF9A825.toInt() }
        val lines = mutableListOf<String>()
        lines += listOfNotNull(m.fare?.let { Fmt.ars(it) }, m.totalKm?.let { Fmt.num(it) + " km tot." }, m.totalMin?.let { Fmt.num(it, 0) + " min tot." }).joinToString(" · ")
        lines += listOfNotNull(m.grossPerKm?.let { Fmt.ars(it) + "/km" }, m.grossPerHour?.let { Fmt.ars(it) + "/h" }).joinToString(" · ").ifEmpty { "Métricas incompletas" } + " (bruto)"
        e.reasons.firstOrNull { it.kind == ReasonKind.FALLA || it.kind == ReasonKind.AVISO }?.let { lines += it.text }
        val src = when (rec.source.name) { "ACCESSIBILITY" -> "leído de pantalla"; "OCR" -> "leído por OCR"; else -> "ingreso manual" }
        render(e.verdict.label, color, if (compact) listOf(lines[1]) else lines + "Fuente: $src. Vos decidís en Uber.", rec.at, compact)
    }

    fun showMessage(text: String) = main.post { render("DriverAI", 0xFF455A64.toInt(), listOf(text), null, false) }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun render(title: String, color: Int, body: List<String>, recordAt: Long?, compact: Boolean) {
        main.removeCallbacks(hideRunnable)
        hideCard()
        lastShownText = (listOf(title) + body).joinToString("\n")
        val wc = windowContext()
        if (wc == null) { notifyFallback(title, body); return }
        val (ctx, type) = wc
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply { setColor(0xF0161A20.toInt()); cornerRadius = dp(16).toFloat(); setStroke(dp(3), color) }
            minimumWidth = dp(220)
            contentDescription = "Tarjeta DriverAI"
        }
        val header = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(ctx).apply {
            text = title; setTextColor(color); textSize = if (compact) 18f else 22f; typeface = Typeface.DEFAULT_BOLD
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
        body.forEach { line ->
            root.addView(TextView(ctx).apply { text = line; setTextColor(0xFFECEFF1.toInt()); textSize = 15f; maxWidth = dp(300) })
        }
        if (recordAt != null && !compact) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(smallBtn("La tomé", "Marcar viaje como tomado") {
                DriverAiApp.graph.scope.launch { DriverAiApp.graph.historyRepo.markTaken(recordAt) }
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
            notifyFallback(title, body)
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
            text = "Leer oferta"; textSize = 15f; setTextColor(Color.BLACK); contentDescription = "Leer oferta con captura"
            background = GradientDrawable().apply { setColor(0xFFFFC107.toInt()); cornerRadius = dp(24).toFloat() }
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
