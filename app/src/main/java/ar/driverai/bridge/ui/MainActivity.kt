package ar.driverai.bridge.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

class MainActivity : ComponentActivity() {
    private var tab by mutableStateOf("inicio")
    private var prefill by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { DriverTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        i?.getStringExtra(EXTRA_TAB)?.let { tab = it }
        prefill = i?.getBooleanExtra(EXTRA_PREFILL, false) == true
    }

    @Composable
    private fun Root() {
        val tabs = listOf(
            Triple("inicio", "Inicio", Icons.Filled.Home),
            Triple("evaluar", "Evaluar", Icons.Filled.Check),
            Triple("ajustes", "Ajustes", Icons.Filled.Settings),
            Triple("historial", "Historial", Icons.Filled.List),
            Triple("diag", "Diagnóstico", Icons.Filled.Build),
        )
        Scaffold(bottomBar = {
            NavigationBar {
                tabs.forEach { (id, label, icon) ->
                    NavigationBarItem(selected = tab == id, onClick = { tab = id }, icon = { Icon(icon, null) },
                        label = { Text(label) }, modifier = Modifier.testTag("tab_$id"))
                }
            }
        }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    "inicio" -> HomeScreen(this@MainActivity)
                    "evaluar" -> EvaluateScreen(prefill) { prefill = false }
                    "ajustes" -> SettingsScreen()
                    "historial" -> HistoryScreen()
                    else -> DiagnosticsScreen(this@MainActivity)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_PREFILL = "prefill"
    }
}
