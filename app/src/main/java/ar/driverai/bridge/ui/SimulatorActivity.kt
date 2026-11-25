package ar.driverai.bridge.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Simulador de una tarjeta de oferta (SOLO builds debug). Permite probar de punta a punta el
 * servicio de accesibilidad, el parser y la tarjeta flotante sin depender de Uber.
 * Muestra claramente que NO es Uber; sus resultados no son prueba de compatibilidad con Uber.
 */
class SimulatorActivity : Activity() {
    private var idx = 0
    private lateinit var box: LinearLayout

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        idx = intent.getIntExtra(EXTRA_SCENARIO, 0).coerceIn(0, SCENARIOS.lastIndex)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 11, 9))
            setPadding(dp(22), dp(40), dp(22), dp(24))
        }
        root.addView(TextView(this).apply {
            text = "SIMULADOR DriverAI — no es Uber"
            setTextColor(Color.rgb(98, 230, 164))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "SIMULADOR DriverAI — no es Uber"
        })
        root.addView(TextView(this).apply {
            text = "Escenario de prueba"
            setTextColor(Color.WHITE)
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(18))
        })
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(19, 29, 23))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.rgb(41, 58, 47))
            }
        }
        root.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(Button(this).apply {
            text = "Siguiente escenario"
            setOnClickListener { idx = (idx + 1) % SCENARIOS.size; render() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        setContentView(root)
        render()
    }

    private fun render() {
        box.removeAllViews()
        SCENARIOS[idx].forEachIndexed { i, text ->
            box.addView(TextView(this).apply {
                this.text = text
                setTextColor(if (i == 1) Color.rgb(98, 230, 164) else Color.WHITE)
                textSize = if (i == 1) 32f else 17f
                if (i == 1) typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    override fun onResume() { super.onResume(); isVisible = true }
    override fun onPause() { isVisible = false; super.onPause() }

    companion object {
        const val EXTRA_SCENARIO = "scenario"
        @Volatile var isVisible = false
        val SCENARIOS = listOf(
            listOf("UberX", "$ 8.540", "A 4 min (1,2 km)", "Viaje de 18 min (9,3 km)", "Av. Ejemplo 123", "Toca para aceptar"),
            listOf("UberX", "$ 2.100", "A 9 min (3,5 km)", "Viaje de 7 min (2,1 km)", "Toca para aceptar"),
            listOf("Comfort", "$ 12.300", "Solicitud nueva", "Toca para aceptar"),
            listOf("Sin oferta", "Estás en línea", "Buscando viajes"),
        )
    }
}
