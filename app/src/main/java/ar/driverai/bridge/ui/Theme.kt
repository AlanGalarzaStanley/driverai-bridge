package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.driverai.bridge.engine.Verdict

val Green = Color(0xFF43A047)
val Red = Color(0xFFE53935)
val Amber = Color(0xFFFFC107)

fun verdictColor(v: Verdict) = when (v) { Verdict.CONVIENE -> Green; Verdict.NO_CONVIENE -> Red; Verdict.REVISAR -> Amber }

@Composable
fun DriverTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Amber, onPrimary = Color.Black, background = Color(0xFF101317), surface = Color(0xFF161A20),
        onBackground = Color(0xFFF1F3F5), onSurface = Color(0xFFF1F3F5), surfaceVariant = Color(0xFF222831),
    )
    val base = Typography()
    MaterialTheme(colorScheme = colors, typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp),
        titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    ), content = content)
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
