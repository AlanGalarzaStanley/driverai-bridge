package ar.driverai.bridge.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
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

val Green = Color(0xFF3DDC97)
val Red = Color(0xFFFF5C5C)
val Amber = Color(0xFFFFC857)
val Muted = Color(0xFF9AA3AF)

fun verdictColor(v: Verdict) = when (v) {
    Verdict.CONVIENE -> Green
    Verdict.NO_CONVIENE -> Red
    Verdict.REVISAR -> Amber
}

@Composable
fun DriverTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Amber,
        onPrimary = Color(0xFF12141A),
        secondary = Color(0xFF6EA8FE),
        background = Color(0xFF0B0D11),
        surface = Color(0xFF12161E),
        onBackground = Color(0xFFF4F6F8),
        onSurface = Color(0xFFF4F6F8),
        surfaceVariant = Color(0xFF1A202B),
        outline = Color(0xFF2C3442),
    )
    val base = Typography()
    MaterialTheme(
        colorScheme = colors,
        typography = base.copy(
            bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Amber)
            content()
        }
    }
}
