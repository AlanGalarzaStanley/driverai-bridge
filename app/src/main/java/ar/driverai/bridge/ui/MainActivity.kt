package ar.driverai.bridge.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var tab by mutableStateOf("inicio")
    private var prefill by mutableStateOf(false)
    /** Texto compartido desde otra app (respuesta de una IA). Solo se muestra; se carga si el conductor confirma. */
    private var sharedText by mutableStateOf<String?>(null)
    private var aiReturn by mutableStateOf("inicio")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { DriverTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) { sharedText = text.take(ar.driverai.bridge.intel.AiReport.MAX_INPUT_CHARS); tab = "ia" }
            return
        }
        i?.getStringExtra(EXTRA_TAB)?.let { tab = it }
        prefill = i?.getBooleanExtra(EXTRA_PREFILL, false) == true
    }

    @Composable
    private fun Root() {
        BackHandler(enabled = tab == "copiloto" || tab == "evaluar") { tab = "inicio" }
        BackHandler(enabled = tab == "ia") { tab = aiReturn }
        BackHandler(enabled = tab in setOf("ajustes", "diag", "historial", "vehiculo", "informes")) { tab = "mas" }
        val tabs = listOf(
            Triple("inicio", "Inicio", Icons.Filled.Home),
            Triple("radar", "Radar", Icons.Filled.Place),
            Triple("billetera", "Billetera", Icons.Filled.List),
            Triple("mas", "Más", Icons.Filled.Settings),
        )
        val selectedTab = when (tab) {
            "ajustes", "diag", "historial", "vehiculo", "informes" -> "mas"
            "evaluar", "copiloto" -> "inicio"
            "ia" -> aiReturn
            else -> tab
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0xFF0C1911),
                    0.32f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.background,
                ),
            ),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    if (tab != "copiloto" && tab != "ia") {
                        NavigationBar(containerColor = Color(0xF20D1410), tonalElevation = 0.dp) {
                            tabs.forEach { (id, label, icon) ->
                                NavigationBarItem(
                                    selected = selectedTab == id,
                                    onClick = { tab = id },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label, maxLines = 1, softWrap = false, fontSize = 10.sp) },
                                    modifier = Modifier.testTag("tab_$id"),
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DriverPalette.mint,
                                        selectedTextColor = DriverPalette.mint,
                                        indicatorColor = DriverPalette.mint.copy(alpha = 0.12f),
                                        unselectedIconColor = DriverPalette.textMuted,
                                        unselectedTextColor = DriverPalette.textMuted,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(pad)) {
                    when (tab) {
                        "inicio" -> HomeScreen(this@MainActivity) { destination ->
                            if (destination == "evaluar") prefill = true
                            if (destination == "ia") aiReturn = "inicio"
                            tab = destination
                        }
                        "evaluar" -> EvaluateScreen(prefill) { prefill = false }
                        "billetera" -> WalletScreen()
                        "vehiculo" -> VehicleScreen()
                        "mas" -> ToolsScreen { destination -> tab = destination }
                        "copiloto" -> CopilotScreen(onOpenAi = { aiReturn = "inicio"; tab = "ia" }) { tab = "inicio" }
                        "ia" -> AiUpdateScreen(sharedText, onSharedConsumed = { sharedText = null }) { tab = aiReturn }
                        "ajustes" -> SettingsScreen()
                        "historial" -> HistoryScreen()
                        "diag" -> DiagnosticsScreen(this@MainActivity)
                        "radar" -> RadarScreen(onOpenAi = { aiReturn = "radar"; tab = "ia" })
                        "informes" -> InsightsScreen()
                        else -> HomeScreen(this@MainActivity)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_PREFILL = "prefill"
    }
}
