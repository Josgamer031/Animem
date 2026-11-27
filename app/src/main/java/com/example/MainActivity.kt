package com.example

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import coil.Coil
import coil.ImageLoader
import com.example.data.JkAnimeRepository
import com.example.model.LaunchDeepLink
import com.example.notifications.DeepLinkIntentHelper
import com.example.ui.components.LocalPipController
import com.example.ui.components.PipController
import com.example.ui.navigation.KitsuneApp
import com.example.ui.theme.KitsuneStreamTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private lateinit var pipController: PipController
    private val pendingDeepLink = MutableStateFlow<LaunchDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pipController = PipController(this)
        DeepLinkIntentHelper.parse(intent)?.let { link ->
            pendingDeepLink.value = link
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val repository = JkAnimeRepository(applicationContext)
        val imageClient = repository.httpClient.okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val reqUrl = chain.request().url.toString()
                val referer = if (reqUrl.contains("latanime", ignoreCase = true)) {
                    "https://latanime.org/"
                } else {
                    "${repository.httpClient.baseUrl}/"
                }
                val req = chain.request().newBuilder()
                    .header("User-Agent", repository.httpClient.activeUserAgent)
                    .header("Referer", referer)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(req)
            }
            .build()
        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .okHttpClient(imageClient)
                .crossfade(true)
                .build()
        )
        setContent {
            CompositionLocalProvider(LocalPipController provides pipController) {
                KitsuneStreamTheme(darkTheme = true) {
                    KitsuneApp(
                        repository = repository,
                        deepLinkState = pendingDeepLink,
                        onConsumeDeepLink = { pendingDeepLink.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkIntentHelper.parse(intent)?.let { link ->
            pendingDeepLink.value = link
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::pipController.isInitialized) {
            pipController.onUserLeaveHint()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::pipController.isInitialized) {
            pipController.onPictureInPictureModeChanged(isInPictureInPictureMode)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        @Suppress("DEPRECATION")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && ::pipController.isInitialized) {
            pipController.onPictureInPictureModeChanged(isInPictureInPictureMode)
        }
    }
}
