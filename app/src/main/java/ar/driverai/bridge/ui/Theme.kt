package ar.driverai.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.driverai.bridge.engine.Verdict

/** Colores semánticos compartidos: preferir estos tokens antes que colores ad hoc. */
object DriverPalette {
    val background = Color(0xFF0C1115)
    val surface = Color(0xFF141B20)
    val surfaceRaised = Color(0xFF1B252A)
    val surfaceHighlight = Color(0xFF202D32)
    val outline = Color(0xFF344148)
    val text = Color(0xFFF2F4F3)
    val textMuted = Color(0xFF9AA8AC)
    val gold = Color(0xFFE7B95A)
    val mint = Color(0xFF8BD4BC)
    val blue = Color(0xFF91AFC0)
    val success = Color(0xFF75D2A5)
    val warning = Color(0xFFF0BE68)
    val danger = Color(0xFFFF7C7C)
}

object DriverSpace {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
}

object DriverRadius {
    val control: Dp = 12.dp
    val card: Dp = 20.dp
    val hero: Dp = 24.dp
}

// Alias para mantener compatibilidad visual mientras se actualizan las pantallas por etapas.
val Green = DriverPalette.success
val Red = DriverPalette.danger
val Amber = DriverPalette.warning
val AccentMint = DriverPalette.gold
val AccentBlue = DriverPalette.blue
val TextMuted = DriverPalette.textMuted

fun verdictColor(v: Verdict) = when (v) {
    Verdict.CONVIENE -> DriverPalette.success
    Verdict.NO_CONVIENE -> DriverPalette.danger
    Verdict.REVISAR -> DriverPalette.warning
}

@Composable
fun DriverTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = DriverPalette.gold,
        onPrimary = Color(0xFF211A0B),
        secondary = DriverPalette.mint,
        tertiary = DriverPalette.blue,
        error = DriverPalette.danger,
        background = DriverPalette.background,
        surface = DriverPalette.surface,
        surfaceVariant = DriverPalette.surfaceRaised,
        onBackground = DriverPalette.text,
        onSurface = DriverPalette.text,
        onSurfaceVariant = Color(0xFFD0D9D9),
        outline = DriverPalette.outline,
    )
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(DriverRadius.control),
            small = RoundedCornerShape(DriverRadius.control),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(DriverRadius.card),
            extraLarge = RoundedCornerShape(DriverRadius.hero),
        ),
        typography = base.copy(
            bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.35).sp),
            titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            headlineMedium = TextStyle(fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.45).sp),
        ),
        content = content,
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.padding(vertical = 5.dp),
        shape = RoundedCornerShape(DriverRadius.card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = DriverSpace.md, vertical = DriverSpace.md)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, caption: String? = null, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
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
