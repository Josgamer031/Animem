package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CoverPalette(
    val vibrant: Color,
    val ambientGlow: Color
)

private object CoverPaletteCache {
    val cache = LruCache<String, Int>(64)
}

@Composable
fun rememberCoverPalette(
    imageUrl: String?,
    fallbackAccent: Color = MaterialTheme.colorScheme.primary
): CoverPalette {
    val context = LocalContext.current
    val fallbackArgb = fallbackAccent.toArgb()
    var targetArgb by remember(imageUrl, fallbackArgb) {
        mutableStateOf(
            imageUrl?.let { CoverPaletteCache.cache.get(it) } ?: fallbackArgb
        )
    }

    LaunchedEffect(imageUrl, fallbackArgb) {
        if (imageUrl.isNullOrBlank()) {
            targetArgb = fallbackArgb
            return@LaunchedEffect
        }
        val cached = CoverPaletteCache.cache.get(imageUrl)
        if (cached != null) {
            targetArgb = cached
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(48)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val drawable = (result as? SuccessResult)?.drawable ?: return@runCatching null
                val rawBitmap = (drawable as? BitmapDrawable)?.bitmap
                val bitmap = when {
                    rawBitmap != null && !rawBitmap.isRecycled &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || rawBitmap.config != Bitmap.Config.HARDWARE) -> rawBitmap
                    rawBitmap != null && !rawBitmap.isRecycled ->
                        rawBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            ?: drawable.toBitmap(width = 32, height = 32, config = Bitmap.Config.ARGB_8888)
                    else -> drawable.toBitmap(width = 32, height = 32, config = Bitmap.Config.ARGB_8888)
                }
                extractVibrantAccentArgb(bitmap)
            }.getOrNull()
        }
        if (extracted != null) {
            CoverPaletteCache.cache.put(imageUrl, extracted)
            targetArgb = extracted
        } else {
            targetArgb = fallbackArgb
        }
    }

    val animatedVibrant by animateColorAsState(
        targetValue = Color(targetArgb),
        animationSpec = tween(durationMillis = 480),
        label = "cover_palette_vibrant"
    )
    val animatedGlow by animateColorAsState(
        targetValue = Color(targetArgb).copy(alpha = 0.26f),
        animationSpec = tween(durationMillis = 480),
        label = "cover_palette_glow"
    )

    return CoverPalette(
        vibrant = animatedVibrant,
        ambientGlow = animatedGlow
    )
}

private fun extractVibrantAccentArgb(bitmap: Bitmap): Int? {
    if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
    val scaled = if (bitmap.width > 24 || bitmap.height > 24) {
        Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    } else {
        bitmap
    }
    val hsv = FloatArray(3)
    var bestScore = -1f
    var bestColor: Int? = null

    for (y in 0 until scaled.height) {
        for (x in 0 until scaled.width) {
            val pixel = scaled.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 200) continue
            AndroidColor.colorToHSV(pixel, hsv)
            val saturation = hsv[1]
            val value = hsv[2]
            if (value < 0.22f || value > 0.96f) continue
            if (saturation < 0.18f) continue
            // Favor mid-bright saturated colors for crisp dark-UI accents
            val valueWeight = 1f - kotlin.math.abs(value - 0.65f)
            val score = saturation * 1.4f + valueWeight * 0.6f
            if (score > bestScore) {
                bestScore = score
                bestColor = pixel
            }
        }
    }

    if (scaled !== bitmap) {
        scaled.recycle()
    }

    val chosen = bestColor ?: return null
    AndroidColor.colorToHSV(chosen, hsv)
    hsv[1] = hsv[1].coerceIn(0.48f, 0.92f)
    hsv[2] = hsv[2].coerceIn(0.62f, 0.92f)
    return AndroidColor.HSVToColor(hsv)
}
