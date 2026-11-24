package com.example

import com.example.data.LatanimeParser
import com.example.model.AnimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LatanimeParserTest {

    @Test
    fun parseHome_extractsLatestEpisodesAndCatalogForLatino() {
        val html = """
            <html><body>
              <div id="carouselExampleCaptions">
                <div class="carousel-item">
                  <a href="https://latanime.org/anime/naruto-shippuden-latino">
                    <img data-src="https://latanime.org/storage/naruto.jpg" />
                    <div class="carousel-caption"><h5>Naruto Shippuden Latino</h5></div>
                  </a>
                </div>
              </div>
              <section>
                <h2>Últimos Episodios</h2>
                <div class="row">
                  <div class="col-6">
                    <a href="https://latanime.org/ver/dragon-ball-daima-episodio-10">
                      <img src="https://latanime.org/storage/daima.jpg" />
                      <span class="serietitle">Dragon Ball Daima Episodio 10</span>
                    </a>
                  </div>
                </div>
              </section>
              <section>
                <h2>Animes Recientes</h2>
                <div class="row">
                  <div class="col-6">
                    <a href="https://latanime.org/anime/ranma-2024-latino">
                      <img data-src="https://latanime.org/storage/ranma.jpg" />
                      <h3 class="serietitle">Ranma 1/2 (2024) Latino</h3>
                    </a>
                  </div>
                </div>
              </section>
            </body></html>
        """.trimIndent()

        val feed = LatanimeParser.parseHome(html)
        assertEquals(AnimeProvider.LATANIME, feed.provider)
        assertEquals(1, feed.featured.size)
        assertEquals("naruto-shippuden-latino", feed.featured.first().slug)
        assertEquals(1, feed.latestEpisodes.size)
        assertEquals("dragon-ball-daima", feed.latestEpisodes.first().animeSlug)
        assertEquals(10, feed.latestEpisodes.first().episodeNumber)
        assertEquals("https://latanime.org/storage/daima.jpg", feed.latestEpisodes.first().coverUrl)
        assertTrue(feed.recentAdded.any { it.slug == "ranma-2024-latino" && it.coverUrl.endsWith("ranma.jpg") })
    }

    @Test
    fun parseDirectoryAndDetailAndServers_workForLatanime() {
        val dirHtml = """
            <html><body>
              <div class="row">
                <div class="col-md-4">
                  <a href="https://latanime.org/anime/solo-leveling-latino">
                    <div class="seriestu">
                      <img data-src="https://latanime.org/storage/solo.jpg" />
                      <h3 class="serietitle">Solo Leveling (Latino)</h3>
                    </div>
                  </a>
                </div>
              </div>
              <ul class="pagination">
                <li class="page-item"><a class="page-link" href="?p=2">2</a></li>
              </ul>
            </body></html>
        """.trimIndent()

        val page = LatanimeParser.parseDirectoryOrSearch(dirHtml, 1)
        assertEquals(1, page.items.size)
        assertEquals("solo-leveling-latino", page.items.first().slug)
        assertEquals(AnimeProvider.LATANIME, page.items.first().provider)
        assertTrue(page.hasNextPage)

        val detailHtml = """
            <html><body>
              <div class="serieimg"><img src="https://latanime.org/storage/solo.jpg" /></div>
              <div class="row"><h2>Solo Leveling (Latino)</h2></div>
              <p class="my-2 opacity-75">Cazadores en español latino.</p>
              <a href="/genero/accion">Acción</a>
              <div class="row">
                <a href="https://latanime.org/ver/solo-leveling-latino-episodio-1">Episodio 1</a>
                <a href="https://latanime.org/ver/solo-leveling-latino-episodio-2">Episodio 2</a>
              </div>
            </body></html>
        """.trimIndent()

        val detail = LatanimeParser.parseAnimeDetail("solo-leveling-latino", detailHtml)
        assertEquals("Solo Leveling (Latino)", detail.title)
        assertEquals(2, detail.episodes.size)
        assertEquals("https://latanime.org/ver/solo-leveling-latino-episodio-2", detail.episodes[1].url)
        assertEquals(listOf("Acción"), detail.genres)

        val encodedUrl = Base64.getEncoder().encodeToString("https://sfastwish.com/e/lat123".toByteArray())
        val epHtml = """
            <html><body>
              <ul class="cap_repro">
                <li><a class="play-video" data-player="$encodedUrl">StreamWish</a></li>
              </ul>
            </body></html>
        """.trimIndent()

        val servers = LatanimeParser.parseEpisodeServers(
            epHtml,
            "https://latanime.org/ver/solo-leveling-latino-episodio-1"
        )
        assertEquals(1, servers.size)
        assertEquals("StreamWish", servers.first().name)
        assertEquals("https://sfastwish.com/e/lat123", servers.first().decodedEmbedUrl)
    }
}
