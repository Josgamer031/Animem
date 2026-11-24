package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.Coil
import coil.ImageLoader
import com.example.data.JkAnimeRepository
import com.example.ui.navigation.KitsuneApp
import com.example.ui.theme.KitsuneStreamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            KitsuneStreamTheme(darkTheme = true) {
                KitsuneApp(repository = repository)
            }
        }
    }
}
