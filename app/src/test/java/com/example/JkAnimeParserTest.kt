package com.example

import com.example.data.JkAnimeParser
import com.example.data.JsUnpacker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JkAnimeParserTest {

    @Test
    fun parseHome_extractsLatestEpisodesAndAiring() {
        val sampleHtml = """
            <html><body>
              <section class="hero">
                <div class="hero__items" data-setbg="/assets/images/animes/image/solo-leveling.jpg">
                  <div class="hero__text">
                    <div class="label">Anime</div>
                    <h2>Solo Leveling</h2>
                    <p>Cazadores en mazmorras.</p>
                    <a href="https://jkanime.net/solo-leveling/">Ver</a>
                  </div>
                </div>
              </section>
              <div class="trending__anime">
                <div class="anime__item">
                  <a href="https://jkanime.net/dandadan/12/">
                    <div class="anime__item__pic" data-setbg="https://cdn.jkanime.net/dandadan.jpg">
                      <div class="ep">Ep. 12</div>
                    </div>
                  </a>
                  <div class="anime__item__text">
                    <h5>Dandadan</h5>
                  </div>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val feed = JkAnimeParser.parseHome(sampleHtml)
        assertEquals(1, feed.featured.size)
        assertEquals("solo-leveling", feed.featured.first().slug)
        assertEquals(1, feed.latestEpisodes.size)
        assertEquals("dandadan", feed.latestEpisodes.first().animeSlug)
        assertEquals(12, feed.latestEpisodes.first().episodeNumber)
        assertEquals("Dandadan", feed.latestEpisodes.first().animeTitle)
    }

    @Test
    fun parseAnimeDetail_extractsMetadataGenresAndPagination() {
        val detailHtml = """
            <html><body>
              <div class="anime__details__pic" data-setbg="https://cdn.jkanime.net/poster.jpg"></div>
              <div class="anime__details__title">
                <h3>Frieren: Beyond Journey's End</h3>
                <span>Sousou no Frieren</span>
              </div>
              <div class="anime__details__text">
                <p>La maga elfa Frieren emprende un nuevo viaje.</p>
              </div>
              <div class="anime__details__widget">
                <ul>
                  <li><span>Tipo:</span> Anime</li>
                  <li><span>Estado:</span> Finalizado</li>
                  <li><span>Episodios:</span> 28</li>
                  <li><span>Generos:</span> <a href="/genero/fantasia/">Fantasía</a> <a href="/genero/aventura/">Aventura</a></li>
                </ul>
              </div>
              <div class="anime__pagination">
                <a class="numbers" href="#pag1">1 - 12</a>
                <a class="numbers" href="#pag2">13 - 24</a>
              </div>
              <script>
                $.get('/ajax/pagination_episodes/4123/1', function(data){});
              </script>
            </body></html>
        """.trimIndent()

        val detail = JkAnimeParser.parseAnimeDetail("sousou-no-frieren", detailHtml)
        assertEquals("Frieren: Beyond Journey's End", detail.title)
        assertEquals("Sousou no Frieren", detail.alternativeTitle)
        assertEquals("4123", detail.animeId)
        assertEquals("Finalizado", detail.status)
        assertEquals(listOf("Fantasía", "Aventura"), detail.genres)
        assertEquals(2, detail.pageRanges.size)
    }

    @Test
    fun parseEpisodeServers_extractsVarServersAndVarVideo() {
        val episodeHtml = """
            <html><body>
              <div class="bg-servers">
                <a class="btn-server" href="#">Desu</a>
                <a class="btn-server" href="#">StreamWish</a>
              </div>
              <script>
                var video = [];
                video[0] = '<iframe src="https://jkanime.net/um.php?e=abc123"></iframe>';
                var servers = [
                  {"remote":"https://jkanime.net/c4.php?u=aHR0cHM6Ly9zZmFzdHdpc2guY29tL2Uvem5pODdnNHJjdXlw&s=streamwish","server":"StreamWish"}
                ];
              </script>
            </body></html>
        """.trimIndent()

        val servers = JkAnimeParser.parseEpisodeServers(
            episodeHtml,
            "https://jkanime.net/dandadan/12/"
        )
        assertTrue(servers.any { it.decodedEmbedUrl.contains("um.php?e=abc123") })
        assertTrue(servers.any { it.decodedEmbedUrl == "https://sfastwish.com/e/zni87g4rcuyp" })
    }

    @Test
    fun jsUnpacker_extractsDirectHlsStreamFromPackedScript() {
        val packed = """
            <script>
            eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\b'+c.toString(a)+'\\b','g'),k[c]);return p}('0.1({2:"3://4.5/6/7.8"});',9,9,'jwplayer|setup|file|https|cdn|example|hls|master|m3u8'.split('|'),0,{}))
            </script>
        """.trimIndent()

        val urls = JsUnpacker.extractDirectMediaUrls(packed)
        assertTrue(urls.contains("https://cdn.example/hls/master.m3u8"))
    }

    @Test
    fun parseDirectoryOrSearch_extractsEmbeddedScriptJsonAndPagination() {
        val dirHtml = """
            <html><body>
              <div id="app"></div>
              <script>
                var animes = [
                  {"slug":"frieren","title":"Frieren","image":"frieren.jpg","type":"Anime","status":"Finalizado"},
                  {"slug":"bleach","title":"Bleach","image":"https://cdn.jkdesa.com/bleach.jpg","type":"Anime","status":"Finalizado"}
                ];
                var meta = {"current_page":1,"last_page":4};
              </script>
            </body></html>
        """.trimIndent()

        val page = JkAnimeParser.parseDirectoryOrSearch(dirHtml, 1)
        assertEquals(2, page.items.size)
        assertEquals("frieren", page.items[0].slug)
        assertEquals("Frieren", page.items[0].title)
        assertTrue(page.hasNextPage)
    }

    @Test
    fun parseEpisodesPageResult_extractsLaravelPaginatedObject() {
        val json = """
            {
              "current_page": 1,
              "last_page": 3,
              "total": 30,
              "data": [
                {"number": 1, "title": "Episodio 1", "image": "/assets/ep1.jpg"},
                {"number": 2, "title": "Episodio 2", "image": "/assets/ep2.jpg"}
              ]
            }
        """.trimIndent()

        val result = JkAnimeParser.parseEpisodesPageResult("frieren", json)
        assertEquals(2, result.episodes.size)
        assertEquals(3, result.lastPage)
        assertEquals(30, result.total)
        assertEquals(1, result.episodes[0].number)
    }

    @Test
    fun parseHome_separatesAiringAndRecentAndEnrichesMissingEpisodeCovers() {
        val homeHtml = """
            <html><body>
              <div class="trending__anime">
                <div class="anime__item">
                  <a href="https://jkanime.net/sakamoto-days/5/">
                    <div class="anime__item__pic" data-setbg="/assets/images/loading.gif">
                      <div class="ep">Ep. 5</div>
                    </div>
                  </a>
                  <div class="anime__item__text">
                    <h5>Sakamoto Days</h5>
                  </div>
                </div>
              </div>
              <section>
                <div class="section-title"><h4>En Emisión</h4></div>
                <div class="anime__item">
                  <a href="https://jkanime.net/sakamoto-days/">
                    <div class="anime__item__pic" data-setbg="https://cdn.jkanime.net/sakamoto.jpg"></div>
                  </a>
                  <div class="anime__item__text"><h5>Sakamoto Days</h5></div>
                </div>
              </section>
              <section>
                <div class="section-title"><h4>Agregados Recientes</h4></div>
                <div class="anime__item">
                  <a href="https://jkanime.net/medalist/">
                    <div class="anime__item__pic" data-setbg="https://cdn.jkanime.net/medalist.jpg"></div>
                  </a>
                  <div class="anime__item__text"><h5>Medalist</h5></div>
                </div>
              </section>
            </body></html>
        """.trimIndent()

        val feed = JkAnimeParser.parseHome(homeHtml)
        assertEquals(1, feed.latestEpisodes.size)
        assertEquals("https://cdn.jkanime.net/sakamoto.jpg", feed.latestEpisodes.first().coverUrl)
        assertEquals(listOf("sakamoto-days"), feed.airing.map { it.slug })
        assertEquals(listOf("medalist"), feed.recentAdded.map { it.slug })
    }

    @Test
    fun parseHome_extractsRecentAddedCoversFromSiblingWrappersAndCfSrc() {
        val homeHtml = """
            <html><body>
              <section>
                <div class="section-title"><h4>Animes Agregados Recientemente</h4></div>
                <div class="row">
                  <div class="col-6">
                    <div class="pic"><img data-cfsrc="https://cdn.jkdesu.com/assets/images/animes/image/blue-box.jpg" src="data:image/gif;base64,R0lGOD"></div>
                    <h5><a href="https://jkanime.net/blue-box/">Blue Box</a></h5>
                  </div>
                  <div class="col-6">
                    <noscript><img src="https://cdn.jkdesu.com/assets/images/animes/image/dandadan.jpg"></noscript>
                    <h5><a href="https://jkanime.net/dandadan/">Dandadan</a></h5>
                  </div>
                </div>
              </section>
            </body></html>
        """.trimIndent()

        val feed = JkAnimeParser.parseHome(homeHtml)
        assertEquals(2, feed.recentAdded.size)
        assertEquals(
            "https://cdn.jkdesu.com/assets/images/animes/image/blue-box.jpg",
            feed.recentAdded[0].coverUrl
        )
        assertEquals(
            "https://cdn.jkdesu.com/assets/images/animes/image/dandadan.jpg",
            feed.recentAdded[1].coverUrl
        )
    }
}
