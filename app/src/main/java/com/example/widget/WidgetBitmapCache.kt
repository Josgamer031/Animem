package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.example.data.JkAnimeHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object WidgetBitmapCache {
    private const val CACHE_DIR_NAME = "widget_covers"
    private const val MAX_CACHE_FILES = 24
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun loadCachedBitmap(
        context: Context,
        url: String,
        widthPx: Int,
        heightPx: Int
    ): Bitmap? {
        if (url.isBlank() || widthPx <= 0 || heightPx <= 0) return null
        val file = cacheFile(context, url, widthPx, heightPx)
        if (!file.exists() || file.length() <= 0L) return null
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    fun prefetchForSnapshotAsync(
        context: Context,
        snapshot: WidgetSnapshot
    ) {
        val appContext = context.applicationContext
        val targets = buildList {
            if (snapshot.continueCoverUrl.isNotBlank()) {
                add(Triple(snapshot.continueCoverUrl, 112, 154))
            }
            snapshot.todayPremieres.take(3).forEach { item ->
                if (item.coverUrl.isNotBlank()) {
                    add(Triple(item.coverUrl, 68, 94))
                }
            }
        }
        if (targets.isEmpty()) return

        val missing = targets.filter { (url, w, h) ->
            !cacheFile(appContext, url, w, h).exists()
        }
        if (missing.isEmpty()) return

        scope.launch {
            val anyDownloaded = prefetchMissingSync(appContext, missing)
            if (anyDownloaded) {
                KitsuneHomeWidgetProvider.requestUpdateAll(appContext)
            }
        }
    }

    suspend fun prefetchForSnapshotSync(
        context: Context,
        snapshot: WidgetSnapshot
    ): Boolean {
        val appContext = context.applicationContext
        val targets = buildList {
            if (snapshot.continueCoverUrl.isNotBlank()) {
                add(Triple(snapshot.continueCoverUrl, 112, 154))
            }
            snapshot.todayPremieres.take(3).forEach { item ->
                if (item.coverUrl.isNotBlank()) {
                    add(Triple(item.coverUrl, 68, 94))
                }
            }
        }
        val missing = targets.filter { (url, w, h) ->
            !cacheFile(appContext, url, w, h).exists()
        }
        if (missing.isEmpty()) return false
        return prefetchMissingSync(appContext, missing)
    }

    private fun prefetchMissingSync(
        context: Context,
        targets: List<Triple<String, Int, Int>>
    ): Boolean {
        var downloadedAny = false
        for ((url, w, h) in targets) {
            val key = "${url}_${w}x$h"
            if (!inFlightKeys.add(key)) continue
            try {
                val outFile = cacheFile(context, url, w, h)
                if (outFile.exists() && outFile.length() > 0L) continue
                val referer = if (url.contains("latanime", ignoreCase = true)) {
                    "https://latanime.org/"
                } else {
                    "https://jkanime.net/"
                }
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", JkAnimeHttpClient.USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()
                val bytes = client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.bytes()
                } ?: continue
                if (bytes.isEmpty()) continue

                val decoded = decodeSampledBitmap(bytes, w, h) ?: continue
                val radiusPx = (w * 0.16f).coerceIn(10f, 20f)
                val rounded = centerCropRounded(decoded, w, h, radiusPx)
                if (rounded !== decoded) {
                    decoded.recycle()
                }

                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { fos ->
                    rounded.compress(Bitmap.CompressFormat.PNG, 90, fos)
                }
                rounded.recycle()
                downloadedAny = true
            } catch (_: Throwable) {
            } finally {
                inFlightKeys.remove(key)
            }
        }
        if (downloadedAny) {
            pruneCache(context)
        }
        return downloadedAny
    }

    private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var inSampleSize = 1
        val halfH = bounds.outHeight / 2
        val halfW = bounds.outWidth / 2
        while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    private fun centerCropRounded(
        source: Bitmap,
        targetW: Int,
        targetH: Int,
        radiusPx: Float
    ): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        if (srcW <= 0f || srcH <= 0f) return source

        val scale = maxOf(targetW / srcW, targetH / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val left = (targetW - scaledW) / 2f
        val top = (targetH - scaledH) / 2f

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val rectF = RectF(0f, 0f, targetW.toFloat(), targetH.toFloat())
        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val dstRect = RectF(left, top, left + scaledW, top + scaledH)
        canvas.drawBitmap(source, Rect(0, 0, source.width, source.height), dstRect, paint)
        return output
    }

    private fun cacheFile(context: Context, url: String, w: Int, h: Int): File {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        val hash = sha256("${url}_${w}x$h").take(24)
        return File(dir, "$hash.png")
    }

    private fun pruneCache(context: Context) {
        val dir = File(context.cacheDir, CACHE_DIR_NAME)
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_CACHE_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_CACHE_FILES)
            .forEach { it.delete() }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
