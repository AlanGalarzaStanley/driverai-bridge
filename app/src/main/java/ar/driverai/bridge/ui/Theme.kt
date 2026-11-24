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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.driverai.bridge.engine.Verdict

object DriverColors {
    val Void = Color(0xFF070A0E)
    val Hull = Color(0xFF0D1218)
    val Panel = Color(0xFF141B24)
    val PanelRaised = Color(0xFF1B2430)
    val Rail = Color(0xFF243041)
    val Ink = Color(0xFFF2F5F8)
    val InkDim = Color(0xFF9AA8B5)
    val InkFaint = Color(0xFF6B7A89)
    val Cyan = Color(0xFF3DE0D0)
    val Go = Color(0xFF2EE59D)
    val Stop = Color(0xFFFF5E6C)
    val Caution = Color(0xFFFFC857)
    val Ice = Color(0xFF7EB6FF)
}

val Green get() = DriverColors.Go
val Red get() = DriverColors.Stop
val Amber get() = DriverColors.Caution
val Muted get() = DriverColors.InkDim

fun verdictColor(v: Verdict) = when (v) {
    Verdict.CONVIENE -> DriverColors.Go
    Verdict.NO_CONVIENE -> DriverColors.Stop
    Verdict.REVISAR -> DriverColors.Caution
}

@Composable
fun DriverTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = DriverColors.Cyan,
        onPrimary = DriverColors.Void,
        secondary = DriverColors.Ice,
        tertiary = DriverColors.Caution,
        background = DriverColors.Void,
        surface = DriverColors.Hull,
        onBackground = DriverColors.Ink,
        onSurface = DriverColors.Ink,
        surfaceVariant = DriverColors.Panel,
        onSurfaceVariant = DriverColors.InkDim,
        outline = DriverColors.Rail,
        error = DriverColors.Stop,
    )
    val mono = FontFamily.SansSerif
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            displayLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
            headlineLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.3).sp),
            headlineMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
            titleLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
            titleMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.2.sp),
            bodyLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.4.sp),
            labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("DRIVER AI", style = MaterialTheme.typography.labelMedium, color = DriverColors.Cyan, letterSpacing = 2.sp)
                Text(title, style = MaterialTheme.typography.titleLarge, color = DriverColors.Ink)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DriverColors.InkDim)
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
fun StatusPill(text: String, tone: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.12f))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(tone))
        Text(text, style = MaterialTheme.typography.labelMedium, color = tone)
    }
}

@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = DriverColors.Cyan,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DriverColors.Panel),
        border = BorderStroke(1.dp, DriverColors.Rail),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(2.dp).background(accent.copy(alpha = 0.85f)))
            Column(Modifier.padding(16.dp)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}

@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (emphasis) DriverColors.PanelRaised else DriverColors.Hull)
            .border(1.dp, DriverColors.Rail, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = DriverColors.InkFaint)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (emphasis) DriverColors.Cyan else DriverColors.Ink,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PrimaryCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DriverColors.Cyan,
            contentColor = DriverColors.Void,
            disabledContainerColor = DriverColors.Rail,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
}

@Composable
fun GhostCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverColors.Ink),
        border = BorderStroke(1.dp, DriverColors.Rail),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
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
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DriverColors.Cyan,
            unfocusedBorderColor = DriverColors.Rail,
            focusedLabelColor = DriverColors.Cyan,
            unfocusedLabelColor = DriverColors.InkDim,
            cursorColor = DriverColors.Cyan,
            focusedTextColor = DriverColors.Ink,
            unfocusedTextColor = DriverColors.Ink,
            focusedContainerColor = DriverColors.Hull,
            unfocusedContainerColor = DriverColors.Hull,
        ),
    )
}

@Composable
fun VerdictBanner(label: String, color: Color, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = DriverColors.InkDim)
        }
    }
}

@Composable
fun VSpace(h: Dp = 8.dp) = Spacer(Modifier.height(h))
