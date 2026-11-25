package ar.driverai.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.driverai.bridge.engine.Verdict

/** Semántica de color y ritmo visual para las superficies nativas de DriverAI. */
object DriverPalette {
    val background = Color(0xFF070B09)
    val surface = Color(0xFF0D1410)
    val surfaceRaised = Color(0xFF131D17)
    val surfaceHighlight = Color(0xFF19271F)
    val outline = Color(0xFF293A30)
    val text = Color(0xFFF3F8F4)
    val textMuted = Color(0xFF9BAAA1)
    val gold = Color(0xFFFFC66D)
    val mint = Color(0xFF62E6A4)
    val blue = Color(0xFF9BC9E8)
    val success = Color(0xFF62E6A4)
    val warning = Color(0xFFFFC66D)
    val danger = Color(0xFFFF777D)
    val forest = Color(0xFF10241A)
    val onMint = Color(0xFF062013)
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
    val control: Dp = 14.dp
    val card: Dp = 22.dp
    val hero: Dp = 28.dp
}

// Alias semánticos que mantienen compatibilidad con las pantallas y la tarjeta flotante.
val Green = DriverPalette.success
val Red = DriverPalette.danger
val Amber = DriverPalette.warning
val AccentMint = DriverPalette.mint
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
        primary = DriverPalette.mint,
        onPrimary = DriverPalette.onMint,
        secondary = Color(0xFFB3F4D1),
        tertiary = DriverPalette.blue,
        error = DriverPalette.danger,
        background = DriverPalette.background,
        surface = DriverPalette.surface,
        surfaceVariant = DriverPalette.surfaceRaised,
        onBackground = DriverPalette.text,
        onSurface = DriverPalette.text,
        onSurfaceVariant = Color(0xFFD3DED6),
        outline = DriverPalette.outline,
    )
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(DriverRadius.control),
            small = RoundedCornerShape(DriverRadius.control),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(DriverRadius.card),
            extraLarge = RoundedCornerShape(DriverRadius.hero),
        ),
        typography = base.copy(
            bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            labelSmall = base.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.65.sp, fontWeight = FontWeight.Medium),
            labelLarge = base.labelLarge.copy(fontSize = 13.sp, letterSpacing = 0.2.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.45).sp),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
            headlineSmall = TextStyle(fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.45).sp),
            headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        ),
        content = content,
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.padding(vertical = 5.dp),
        shape = RoundedCornerShape(DriverRadius.card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.68f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(horizontal = DriverSpace.md, vertical = DriverSpace.md),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Spacer(Modifier.size(6.dp).clip(CircleShape).background(AccentMint))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    caption: String? = null,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 2)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted) }
        }
    }
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = AccentMint, letterSpacing = 1.45.sp, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = modifier)
}
