package com.jondude1.orbit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jondude1.orbit.data.Kind

object OrbitColors {
    val bg = Color(0xFF0F1220)
    val surface = Color(0xFF171B2E)
    val surfaceHigh = Color(0xFF20253D)
    val surfaceHighest = Color(0xFF2A3050)
    val amber = Color(0xFFFFB547)
    val periwinkle = Color(0xFF8C9BFF)
    val teal = Color(0xFF4FD1C5)
    val pink = Color(0xFFF59AC0)
    val text = Color(0xFFE8EAF6)
    val muted = Color(0xFF9AA0C0)
    val danger = Color(0xFFFF7A7A)

    fun kind(k: Kind): Color = when (k) {
        Kind.APP -> periwinkle
        Kind.PROJECT -> teal
        Kind.THOUGHT -> amber
        Kind.LEARN -> pink
    }
}

private val scheme = darkColorScheme(
    primary = OrbitColors.amber,
    onPrimary = Color(0xFF2B1A00),
    primaryContainer = Color(0xFF4A3510),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = OrbitColors.periwinkle,
    onSecondary = Color(0xFF111A5A),
    secondaryContainer = Color(0xFF2C3570),
    onSecondaryContainer = Color(0xFFDDE1FF),
    tertiary = OrbitColors.teal,
    background = OrbitColors.bg,
    onBackground = OrbitColors.text,
    surface = OrbitColors.bg,
    onSurface = OrbitColors.text,
    surfaceVariant = OrbitColors.surfaceHigh,
    onSurfaceVariant = OrbitColors.muted,
    surfaceContainerLowest = OrbitColors.bg,
    surfaceContainerLow = OrbitColors.surface,
    surfaceContainer = OrbitColors.surface,
    surfaceContainerHigh = OrbitColors.surfaceHigh,
    surfaceContainerHighest = OrbitColors.surfaceHighest,
    outline = Color(0xFF4A5075),
    outlineVariant = Color(0xFF2E3455),
    error = OrbitColors.danger,
)

private val base = Typography()
private val type = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
