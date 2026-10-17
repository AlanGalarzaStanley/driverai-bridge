package ar.driverai.bridge.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        idx = intent.getIntExtra(EXTRA_SCENARIO, 0).coerceIn(0, SCENARIOS.lastIndex)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(40, 120, 40, 40) }
        root.addView(TextView(this).apply { text = "SIMULADOR DriverAI — no es Uber"; setTextColor(Color.YELLOW); textSize = 16f })
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, 200, 0, 0) }
        root.addView(box)
        root.addView(Button(this).apply { text = "Siguiente escenario"; setOnClickListener { idx = (idx + 1) % SCENARIOS.size; render() } })
        setContentView(root)
        render()
    }

    private fun render() {
        box.removeAllViews()
        SCENARIOS[idx].forEachIndexed { i, s ->
            box.addView(TextView(this).apply { text = s; setTextColor(Color.WHITE); textSize = if (i == 1) 34f else 18f })
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
