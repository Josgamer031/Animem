package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.model.LaunchDeepLink
import com.example.notifications.DeepLinkIntentHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("KitsuneStream", appName)
  }

  @Test
  fun `parse shared anime and episode deep links`() {
    val customAnime = DeepLinkIntentHelper.parse(
      Intent(Intent.ACTION_VIEW, Uri.parse("kitsunestream://anime/latanime/dandadan"))
    )
    assertEquals(LaunchDeepLink.OpenAnimeDetail("dandadan", "latanime"), customAnime)

    val customEpisode = DeepLinkIntentHelper.parse(
      Intent(Intent.ACTION_VIEW, Uri.parse("kitsunestream://episode/jkanime/frieren/18"))
    )
    assertTrue(customEpisode is LaunchDeepLink.OpenPlayer)
    val epLink = customEpisode as LaunchDeepLink.OpenPlayer
    assertEquals("frieren", epLink.slug)
    assertEquals(18, epLink.episodeNumber)
    assertEquals("jkanime", epLink.providerId)

    val jkWeb = DeepLinkIntentHelper.parse(
      Intent(Intent.ACTION_VIEW, Uri.parse("https://jkanime.net/solo-leveling/12/"))
    ) as LaunchDeepLink.OpenPlayer
    assertEquals("solo-leveling", jkWeb.slug)
    assertEquals(12, jkWeb.episodeNumber)
    assertEquals("jkanime", jkWeb.providerId)

    val latWeb = DeepLinkIntentHelper.parse(
      Intent(Intent.ACTION_VIEW, Uri.parse("https://latanime.org/ver/frieren-episodio-7"))
    ) as LaunchDeepLink.OpenPlayer
    assertEquals("frieren", latWeb.slug)
    assertEquals(7, latWeb.episodeNumber)
    assertEquals("latanime", latWeb.providerId)
  }

  @Test
  fun `repository queues and consumes explore category and genre filters`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = com.example.data.JkAnimeRepository(context)

    repository.requestExploreCategory(
      provider = com.example.model.AnimeProvider.JKANIME,
      type = "peliculas"
    )
    val catFilter = repository.pendingExploreFilter.value
    assertEquals(com.example.model.AnimeProvider.JKANIME, catFilter?.provider)
    assertEquals("peliculas", catFilter?.type)
    assertEquals("", catFilter?.status)

    repository.consumePendingExploreFilter()
    assertEquals(null, repository.pendingExploreFilter.value)

    repository.requestExploreGenre(
      provider = com.example.model.AnimeProvider.LATANIME,
      rawGenre = "Ciencia Ficción"
    )
    val genreFilter = repository.pendingExploreFilter.value
    assertEquals(com.example.model.AnimeProvider.LATANIME, genreFilter?.provider)
    assertEquals("sci-fi", genreFilter?.genre)
  }
}
