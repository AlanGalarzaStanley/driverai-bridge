package ar.driverai.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.driverai.bridge.engine.Verdict

/**
 * Premium dark design system inspired by high-end automotive / fintech apps.
 * Deep blacks, soft glow accents, generous spacing, strong hierarchy.
 */
object DriverColors {
    // Backgrounds — deep, layered
    val Void = Color(0xFF050608)
    val Surface = Color(0xFF0C0E12)
    val Card = Color(0xFF12151C)
    val CardElevated = Color(0xFF181C26)
    val Border = Color(0xFF252A36)
    val BorderSubtle = Color(0xFF1A1E28)

    // Text
    val TextPrimary = Color(0xFFF4F6F9)
    val TextSecondary = Color(0xFF9BA3B2)
    val TextTertiary = Color(0xFF6B7382)

    // Accent — premium emerald / neon green (Automind style)
    val Accent = Color(0xFF00E676)
    val AccentSoft = Color(0xFF00E676).copy(alpha = 0.14f)
    val AccentBorder = Color(0xFF00E676).copy(alpha = 0.35f)

    // Semantic
    val Positive = Color(0xFF00E676)
    val Negative = Color(0xFFFF5252)
    val Warning = Color(0xFFFFB74D)
    val Info = Color(0xFF64B5F6)
}

val Green get() = DriverColors.Positive
val Red get() = DriverColors.Negative
val Amber get() = DriverColors.Warning
val Muted get() = DriverColors.TextSecondary

fun verdictColor(v: Verdict) = when (v) {
    Verdict.CONVIENE -> DriverColors.Positive
    Verdict.NO_CONVIENE -> DriverColors.Negative
    Verdict.REVISAR -> DriverColors.Warning
}

@Composable
fun DriverTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = DriverColors.Accent,
        onPrimary = DriverColors.Void,
        secondary = DriverColors.Info,
        tertiary = DriverColors.Warning,
        background = DriverColors.Void,
        surface = DriverColors.Surface,
        onBackground = DriverColors.TextPrimary,
        onSurface = DriverColors.TextPrimary,
        surfaceVariant = DriverColors.Card,
        onSurfaceVariant = DriverColors.TextSecondary,
        outline = DriverColors.Border,
        error = DriverColors.Negative,
    )
    val sans = FontFamily.SansSerif
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            displayLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.6).sp),
            headlineLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
            headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
            titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = (-0.2).sp),
            titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
            bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.2.sp),
            labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
            labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
        ),
        content = content,
    )
}

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DriverColors.Void)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "DRIVER AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = DriverColors.Accent,
                    letterSpacing = 1.8.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = DriverColors.TextPrimary,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = DriverColors.TextSecondary,
                    )
                }
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
fun StatusPill(text: String, tone: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(100.dp))
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.28f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tone),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = tone,
        )
    }
}

@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = DriverColors.Accent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DriverColors.Card),
        border = BorderStroke(1.dp, DriverColors.BorderSubtle),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (emphasis) DriverColors.CardElevated else DriverColors.Surface)
            .border(1.dp, DriverColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = DriverColors.TextTertiary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (emphasis) DriverColors.Accent else DriverColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Circular metric inspired by the Automind gauges. */
@Composable
fun CircularMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = DriverColors.Accent,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(DriverColors.CardElevated)
                .border(2.dp, accent.copy(alpha = 0.45f), CircleShape),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    color = DriverColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = DriverColors.TextSecondary,
        )
    }
}

@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DriverColors.Accent,
            contentColor = DriverColors.Void,
            disabledContainerColor = DriverColors.Border,
            disabledContentColor = DriverColors.TextTertiary,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
        ),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun GhostCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DriverColors.TextPrimary,
        ),
        border = BorderStroke(1.dp, DriverColors.Border),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun CockpitField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DriverColors.Accent,
            unfocusedBorderColor = DriverColors.Border,
            focusedLabelColor = DriverColors.Accent,
            unfocusedLabelColor = DriverColors.TextSecondary,
            cursorColor = DriverColors.Accent,
            focusedTextColor = DriverColors.TextPrimary,
            unfocusedTextColor = DriverColors.TextPrimary,
            focusedContainerColor = DriverColors.Surface,
            unfocusedContainerColor = DriverColors.Surface,
        ),
    )
}

@Composable
fun VerdictBanner(
    label: String,
    color: Color,
    subtitle: String? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DriverColors.TextSecondary,
            )
        }
    }
}

@Composable
fun VSpace(h: Dp = 8.dp) = Spacer(Modifier.height(h))
