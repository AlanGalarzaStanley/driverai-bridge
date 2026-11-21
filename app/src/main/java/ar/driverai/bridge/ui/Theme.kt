package ar.driverai.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

val Green = Color(0xFF62DDAA)
val Red = Color(0xFFFF6B76)
val Amber = Color(0xFFFFC857)
val AccentMint = Color(0xFF77F2C4)
val AccentBlue = Color(0xFF8DA9FF)
val TextMuted = Color(0xFFAAB7C4)

fun verdictColor(v: Verdict) = when (v) { Verdict.CONVIENE -> Green; Verdict.NO_CONVIENE -> Red; Verdict.REVISAR -> Amber }

@Composable
fun DriverTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = AccentMint,
        onPrimary = Color(0xFF07110F),
        secondary = AccentBlue,
        tertiary = Amber,
        background = Color(0xFF0A1017),
        surface = Color(0xFF111A23),
        surfaceVariant = Color(0xFF192632),
        onBackground = Color(0xFFF4F7F8),
        onSurface = Color(0xFFF4F7F8),
        onSurfaceVariant = Color(0xFFD2DCE3),
        outline = Color(0xFF3B4C5B),
    )
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        typography = base.copy(
            bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.35).sp),
            titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        ),
        content = content,
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.padding(vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, caption: String? = null, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
    }
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = AccentMint, letterSpacing = 1.4.sp)
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
    }
}
