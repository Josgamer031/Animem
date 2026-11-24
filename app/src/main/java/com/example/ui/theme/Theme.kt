package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = OnCrimsonContainer,
    secondary = AmberGold,
    onSecondary = Color(0xFF1F1400),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = TealAccent,
    onTertiary = Color(0xFF001E2E),
    background = ObsidianBg,
    onBackground = CoolWhite,
    surface = SlateSurface,
    onSurface = CoolWhite,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = MutedSilver,
    outline = SlateOutline,
    outlineVariant = Color(0xFF212738)
)

private val LightColorScheme = lightColorScheme(
    primary = CrimsonLightPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonLightContainer,
    onPrimaryContainer = OnCrimsonLightContainer,
    secondary = Color(0xFFD48806),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8BE),
    onSecondaryContainer = Color(0xFF332000),
    tertiary = Color(0xFF0284C7),
    background = LightCanvas,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMutedInk,
    outline = Color(0xFFD0D6E4)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KitsuneStreamTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
