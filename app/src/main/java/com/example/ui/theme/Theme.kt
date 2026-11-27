package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.AppColorTheme

private fun buildDarkColorScheme(
    colorTheme: AppColorTheme,
    amoledBlack: Boolean
): ColorScheme {
    val primary = Color(colorTheme.primaryHex)
    val secondary = Color(colorTheme.secondaryHex)
    val tertiary = Color(colorTheme.tertiaryHex)
    val bg = if (amoledBlack) AmoledBg else ObsidianBg
    val surface = if (amoledBlack) AmoledSurface else SlateSurface
    val elevated = if (amoledBlack) AmoledElevated else SlateElevated
    val outline = if (amoledBlack) AmoledOutline else SlateOutline
    val outlineVar = if (amoledBlack) Color(0xFF181C28) else Color(0xFF212738)

    return darkColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = lerp(bg, primary, 0.28f),
        onPrimaryContainer = lerp(Color.White, primary, 0.22f),
        secondary = secondary,
        onSecondary = Color(0xFF14110A),
        secondaryContainer = lerp(bg, secondary, 0.26f),
        onSecondaryContainer = lerp(Color.White, secondary, 0.25f),
        tertiary = tertiary,
        onTertiary = Color(0xFF001E2E),
        background = bg,
        onBackground = CoolWhite,
        surface = surface,
        onSurface = CoolWhite,
        surfaceVariant = elevated,
        onSurfaceVariant = MutedSilver,
        outline = outline,
        outlineVariant = outlineVar
    )
}

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
    colorTheme: AppColorTheme = AppColorTheme.KITSUNE_RED,
    amoledBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> buildDarkColorScheme(colorTheme = colorTheme, amoledBlack = amoledBlack)
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
