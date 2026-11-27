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

    @Test
    fun parseDirectoryOrSearch_handlesRawJsonAndFallbackWithoutCover() {
        val rawJson = """
            {"current_page":1,"last_page":2,"data":[
              {"slug":"naruto","title":"Naruto","image":"/assets/naruto.jpg","type":"Anime"},
              {"slug":"naruto-shippuden","title":"Naruto Shippuden"}
            ]}
        """.trimIndent()
        val jsonPage = JkAnimeParser.parseDirectoryOrSearch(rawJson, 1)
        assertEquals(2, jsonPage.items.size)
        assertEquals("naruto", jsonPage.items[0].slug)
        assertTrue(jsonPage.hasNextPage)

        val fallbackHtml = """
            <html><body>
              <div class="row">
                <div class="col-lg-12">
                  <div class="search-item"><a href="https://jkanime.net/one-piece/">One Piece</a></div>
                  <div class="search-item"><a href="https://jkanime.net/bleach/">Bleach</a></div>
                </div>
              </div>
            </body></html>
        """.trimIndent()
        val htmlPage = JkAnimeParser.parseDirectoryOrSearch(fallbackHtml, 1)
        assertEquals(2, htmlPage.items.size)
        assertEquals(listOf("one-piece", "bleach"), htmlPage.items.map { it.slug })
    }

    @Test
    fun parseHasNextEpisode_detectsLastVsMiddleEpisode() {
        val middleHtml = """
            <html><body>
              <div class="ep-nav">
                <a href="https://jkanime.net/frieren/11/">Episodio Anterior</a>
                <a href="https://jkanime.net/frieren/">Listado</a>
                <a href="https://jkanime.net/frieren/13/">Siguiente Episodio</a>
              </div>
            </body></html>
        """.trimIndent()
        assertEquals(true, JkAnimeParser.parseHasNextEpisode(middleHtml, "frieren", 12))

        val lastHtml = """
            <html><body>
              <div class="ep-nav">
                <a href="https://jkanime.net/frieren/27/">Episodio Anterior</a>
                <a href="https://jkanime.net/frieren/">Listado de Episodios</a>
              </div>
            </body></html>
        """.trimIndent()
        assertEquals(false, JkAnimeParser.parseHasNextEpisode(lastHtml, "frieren", 28))
    }

    @Test
    fun jsUnpacker_validatesUrlsAndDecodesAtob() {
        val b64 = java.util.Base64.getEncoder()
            .encodeToString("https://cdn.myvideo.net/stream/master.m3u8".toByteArray())
        val script = """<script>var src = atob("$b64"); var bad = "https://example.com/fake.m3u8";</script>"""
        val urls = JsUnpacker.extractDirectMediaUrls(script)
        assertTrue(urls.contains("https://cdn.myvideo.net/stream/master.m3u8"))
        assertTrue(urls.none { it.contains("example.com") })
        assertEquals("https://cdn.host.tv/v.mp4", JsUnpacker.normalizeStreamUrl("//cdn.host.tv/v.mp4"))

        val hexEscaped = """<script>var hls = "https:\x2f\x2fcdn.host.tv\x2fv\x2fmaster.m3u8\u003ftoken\u003d123\u0026exp\u003d9";</script>"""
        val hexUrls = JsUnpacker.extractDirectMediaUrls(hexEscaped)
        assertTrue(hexUrls.contains("https://cdn.host.tv/v/master.m3u8?token=123&exp=9"))
    }

    @Test
    fun parseSchedule_extractsWeekdayBucketsAndTimes() {
        val scheduleHtml = """
            <html><body>
              <div class="semana">
                <h3>Lunes</h3>
                <div class="box">
                  <a href="https://jkanime.net/solo-leveling/">
                    <img src="https://cdn.jkanime.net/solo.jpg" />
                    <h5>Solo Leveling</h5>
                  </a>
                  <span class="hora">14:30</span>
                </div>
              </div>
              <div class="semana">
                <h3>Sábado</h3>
                <div class="box">
                  <a href="https://jkanime.net/one-piece/">
                    <img data-src="https://cdn.jkanime.net/op.jpg" />
                    <h5>One Piece</h5>
                  </a>
                  <span class="hora">21:00</span>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val schedule = JkAnimeParser.parseSchedule(scheduleHtml)
        assertEquals(7, schedule.days.size)
        val monday = schedule.days.first { it.dayKey == "lunes" }
        val saturday = schedule.days.first { it.dayKey == "sabado" }
        assertEquals(1, monday.items.size)
        assertEquals("solo-leveling", monday.items.first().slug)
        assertEquals("14:30", monday.items.first().releaseTime)
        assertEquals(1, saturday.items.size)
        assertEquals("one-piece", saturday.items.first().slug)
        assertEquals("21:00", saturday.items.first().releaseTime)
    }

    @Test
    fun webAdBlocker_blocksAdUrlsAndUntrustedRedirects() {
        assertTrue(com.example.data.VideoStreamResolver.isAdOrTrackerUrl("https://cdn.adsterra.com/popunder.js"))
        assertTrue(com.example.data.VideoStreamResolver.isAdOrTrackerUrl("https://exosrv.com/click?id=12"))
        assertTrue(com.example.data.VideoStreamResolver.isAdOrTrackerUrl("intent://open.ad.app/#Intent;end"))
        assertTrue(!com.example.data.VideoStreamResolver.isAdOrTrackerUrl("https://sfastwish.com/e/abc123"))

        assertTrue(
            com.example.data.VideoStreamResolver.isTrustedEmbedNavigation(
                "https://sfastwish.com/e/abc123?autoplay=1",
                "https://sfastwish.com/e/abc123"
            )
        )
        assertTrue(
            !com.example.data.VideoStreamResolver.isTrustedEmbedNavigation(
                "https://random-casino-redirect.xyz/win",
                "https://sfastwish.com/e/abc123"
            )
        )
    }

    @Test
    fun watchHistoryEntry_deserializesLegacyJsonWithDefaultProfileId() {
        val legacyJson = """{"animeSlug":"frieren","animeTitle":"Frieren","coverUrl":"https://cdn/f.jpg","episodeNumber":3,"positionMs":12000,"durationMs":1400000,"updatedAtMillis":1000,"providerId":"latanime"}"""
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val parsed = json.decodeFromString<com.example.model.WatchHistoryEntry>(legacyJson)
        assertEquals("default", parsed.profileId)
        assertEquals("latanime", parsed.providerId)
        assertEquals(3, parsed.episodeNumber)
    }

    @Test
    fun parseHome_extractsSiblingHeadingGridsForAiringAndRecent() {
        val siblingLayoutHtml = """
            <html><body>
              <div class="container">
                <div class="section-title"><h4>Programación en Emisión</h4></div>
                <div class="row">
                  <div class="col-6">
                    <a href="https://jkanime.net/solo-leveling-arise/">
                      <img src="https://cdn.jkdesu.com/solo.jpg">
                      <h5>Solo Leveling</h5>
                    </a>
                  </div>
                </div>
                <div class="section-title"><h4>Animes Agregados Recientemente</h4></div>
                <div class="row">
                  <div class="col-6">
                    <a href="https://jkanime.net/apothecary-diaries-2/">
                      <img src="https://cdn.jkdesu.com/maomao.jpg">
                      <h5>Kusuriya no Hitorigoto 2</h5>
                    </a>
                  </div>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val feed = JkAnimeParser.parseHome(siblingLayoutHtml)
        assertEquals(listOf("solo-leveling-arise"), feed.airing.map { it.slug })
        assertEquals(listOf("apothecary-diaries-2"), feed.recentAdded.map { it.slug })
    }

    @Test
    fun parseHome_extractsEmbeddedScriptArraysForAiringAndRecent() {
        val scriptHomeHtml = """
            <html><body>
              <div id="app"></div>
              <script>
                var emision = [
                  {"slug":"one-piece","title":"One Piece","image":"onepiece.jpg","type":"Anime"}
                ];
                var recientes = [
                  {"slug":"lazarus","title":"Lazarus","image":"lazarus.jpg","type":"Anime"}
                ];
              </script>
            </body></html>
        """.trimIndent()

        val feed = JkAnimeParser.parseHome(scriptHomeHtml)
        assertEquals(listOf("one-piece"), feed.airing.map { it.slug })
        assertEquals(listOf("lazarus"), feed.recentAdded.map { it.slug })
    }

    @Test
    fun parseDirectoryFilterOptions_andBadgeSeparationWorkCorrectly() {
        val filterHtml = """
            <html><body>
              <form action="/directorio/" method="get">
                <select name="estado">
                  <option value="">Todos</option>
                  <option value="1">En Emisión</option>
                  <option value="2">Finalizado</option>
                </select>
                <select name="tipo">
                  <option value="">Todos</option>
                  <option value="Movie">Película</option>
                  <option value="OVA">OVA</option>
                </select>
                <select name="genero">
                  <option value="ciencia-ficcion">Ciencia Ficción</option>
                </select>
              </form>
              <a href="https://jkanime.net/genero/accion/">Acción</a>
              <div class="custom_item2">
                <a href="https://jkanime.net/suzume/">
                  <img src="https://cdn.jkdesu.com/suzume.jpg">
                </a>
                <h5>Suzume no Tojimari</h5>
                <ul>
                  <li class="ep">1 Ep</li>
                  <li class="type">Pelicula</li>
                  <li class="status">Finalizado</li>
                </ul>
              </div>
            </body></html>
        """.trimIndent()

        val opts = JkAnimeParser.parseDirectoryFilterOptions(filterHtml)
        assertEquals("1", opts.statusOptions["emision"])
        assertEquals("2", opts.statusOptions["finalizado"])
        assertEquals("Movie", opts.typeOptions["peliculas"])
        assertEquals("https://jkanime.net/genero/accion/", opts.genreLinks["accion"])

        val page = JkAnimeParser.parseDirectoryOrSearch(filterHtml, 1)
        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals("suzume", item.slug)
        assertEquals("Pelicula", item.type)
        assertEquals("Finalizado", item.status)
        assertEquals("1 Ep", item.episodeBadge)
    }

    @Test
    fun parseHome_doesNotPullPopularProductIntoRecentAdded() {
        val html = """
            <html><body>
              <div class="popular__product">
                <div class="section-title"><h4>Populares de siempre</h4></div>
                <div class="anime__item">
                  <a href="https://jkanime.net/naruto-classic/">
                    <div class="anime__item__pic" data-setbg="https://cdn.jkdesu.com/naruto.jpg"></div>
                  </a>
                  <div class="anime__item__text"><h5>Naruto</h5></div>
                </div>
              </div>
              <div class="last_animes">
                <a href="https://jkanime.net/witch-watch/">
                  <img src="https://cdn.jkdesu.com/witch.jpg">
                  <h5>Witch Watch</h5>
                </a>
              </div>
            </body></html>
        """.trimIndent()

        val feed = JkAnimeParser.parseHome(html)
        assertEquals(listOf("witch-watch"), feed.recentAdded.map { it.slug })
    }

    @Test
    fun parseAnimeDetail_ignoresNonNumericDataIdAndExtractsEpisodePageOnclickAndEmbeddedJson() {
        val html = """
            <html><body>
              <div class="like-btn" data-id="bookmark-heart"></div>
              <div class="anime__details__pic" data-setbg="https://cdn.jkdesu.com/kaiju.jpg" data-anime-id="5592"></div>
              <div class="anime__details__title"><h3>Kaiju No. 8</h3></div>
              <div id="episode_page">
                <a href="#p1" onclick="pagination('5592', 1)">1 - 12</a>
                <a href="#p2" onclick="pagination('5592', 2)">13 - 24</a>
              </div>
              <script>
                var episodios = [
                  {"number": 1, "title": "Episodio 1", "image": "/assets/ep1.jpg"},
                  {"number": 2, "title": "Episodio 2", "image": "/assets/ep2.jpg"}
                ];
              </script>
            </body></html>
        """.trimIndent()

        val detail = JkAnimeParser.parseAnimeDetail("kaiju-no-8", html)
        assertEquals("5592", detail.animeId)
        assertEquals(2, detail.pageRanges.size)
        assertEquals(listOf(1, 2), detail.episodes.map { it.number })
    }

    @Test
    fun parseDirectoryOrSearch_excludesSidebarRecommendationsFromEmptySearch() {
        val html = """
            <html><body>
              <div class="anime__page__content">
                <p>No se encontraron resultados.</p>
              </div>
              <aside class="anime__sidebar__view">
                <div class="anime__item">
                  <a href="https://jkanime.net/sidebar-promo/">
                    <div class="anime__item__pic" data-setbg="https://cdn.jkdesu.com/promo.jpg"></div>
                  </a>
                  <div class="anime__item__text"><h5>Sidebar Promo</h5></div>
                </div>
              </aside>
            </body></html>
        """.trimIndent()

        val page = JkAnimeParser.parseDirectoryOrSearch(html, 1)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun favoriteAnime_serializesAndDeserializesCorrectly() {
        val fav = com.example.model.FavoriteAnime(
            animeSlug = "solo-leveling",
            animeTitle = "Solo Leveling",
            coverUrl = "https://cdn.jkdesu.com/solo.jpg",
            type = "Anime",
            status = "En emisión",
            totalEpisodesText = "12",
            providerId = "jkanime",
            profileId = "default",
            addedAtMillis = 123456789L
        )
        val json = kotlinx.serialization.json.Json
        val encoded = json.encodeToString(com.example.model.FavoriteAnime.serializer(), fav)
        val decoded = json.decodeFromString(com.example.model.FavoriteAnime.serializer(), encoded)
        assertEquals(fav, decoded)
    }

    @Test
    fun userPreferences_serializesAndPreservesDefaults() {
        val defaults = com.example.model.UserPreferences()
        assertEquals(true, defaults.autoPlayNext)
        assertEquals(false, defaults.autoSkipOpening)
        assertEquals(com.example.model.DeviceMode.PHONE, defaults.deviceMode)
        assertEquals(false, defaults.hasSelectedDeviceMode)
        assertTrue(defaults.recentSearches.isEmpty())

        val prefs = com.example.model.UserPreferences(
            autoPlayNext = false,
            autoSkipOpening = true,
            recentSearches = listOf("Frieren", "Dandadan"),
            deviceMode = com.example.model.DeviceMode.SMART_TV,
            hasSelectedDeviceMode = true
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(com.example.model.UserPreferences.serializer(), prefs)
        val decoded = json.decodeFromString(com.example.model.UserPreferences.serializer(), encoded)
        assertEquals(prefs, decoded)
    }

    @Test
    fun favoriteAnime_legacyJsonDefaultsWatchStatusAndRoundTripsAllStatuses() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val legacyJson = """{"animeSlug":"frieren","animeTitle":"Frieren","providerId":"jkanime","profileId":"default"}"""
        val decodedLegacy = json.decodeFromString(com.example.model.FavoriteAnime.serializer(), legacyJson)
        assertEquals(com.example.model.WatchStatus.WATCHING, decodedLegacy.watchStatus)

        com.example.model.WatchStatus.entries.forEach { st ->
            val updated = decodedLegacy.copy(watchStatus = st)
            val roundTrip = json.decodeFromString(
                com.example.model.FavoriteAnime.serializer(),
                json.encodeToString(com.example.model.FavoriteAnime.serializer(), updated)
            )
            assertEquals(st, roundTrip.watchStatus)
        }
    }

    @Test
    fun computeProfileWatchStats_aggregatesHistoryFavoritesAndDuration() {
        val history = listOf(
            com.example.model.WatchHistoryEntry(
                animeSlug = "solo-leveling",
                animeTitle = "Solo Leveling",
                coverUrl = "",
                episodeNumber = 1,
                positionMs = 1_200_000L, // 20 min
                durationMs = 1_440_000L,
                providerId = "jkanime"
            ),
            com.example.model.WatchHistoryEntry(
                animeSlug = "solo-leveling",
                animeTitle = "Solo Leveling",
                coverUrl = "",
                episodeNumber = 2,
                positionMs = 1_500_000L, // 25 min
                durationMs = 1_500_000L,
                providerId = "jkanime"
            ),
            com.example.model.WatchHistoryEntry(
                animeSlug = "dandadan",
                animeTitle = "Dandadan",
                coverUrl = "",
                episodeNumber = 1,
                positionMs = 1_500_000L, // 25 min
                durationMs = 1_500_000L,
                providerId = "latanime"
            )
        )
        val favorites = listOf(
            com.example.model.FavoriteAnime(
                animeSlug = "solo-leveling",
                animeTitle = "Solo Leveling",
                watchStatus = com.example.model.WatchStatus.WATCHING
            ),
            com.example.model.FavoriteAnime(
                animeSlug = "dandadan",
                animeTitle = "Dandadan",
                watchStatus = com.example.model.WatchStatus.COMPLETED
            ),
            com.example.model.FavoriteAnime(
                animeSlug = "blue-box",
                animeTitle = "Blue Box",
                watchStatus = com.example.model.WatchStatus.PLAN_TO_WATCH
            )
        )
        val stats = com.example.model.computeProfileWatchStats(history, favorites)
        assertEquals(3, stats.totalEpisodesWatched)
        assertEquals(2, stats.uniqueAnimesWatched)
        assertEquals(4_200_000L, stats.totalWatchTimeMs)
        assertEquals("1h 10m", com.example.model.formatWatchDuration(stats.totalWatchTimeMs))
        assertEquals("0 min", com.example.model.formatWatchDuration(0L))
        assertEquals(1, stats.watchingCount)
        assertEquals(1, stats.planToWatchCount)
        assertEquals(1, stats.completedCount)
        assertEquals(0, stats.droppedCount)
        assertEquals(2, stats.jkanimeEpisodesCount)
        assertEquals(1, stats.latanimeEpisodesCount)
    }

    @Test
    fun userPreferences_premiereAlertFieldsSerializeCorrectly() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val prefs = com.example.model.UserPreferences(
            premiereAlertsEnabled = true,
            notifiedPremiereKeys = listOf("jkanime:frieren:28", "latanime:dandadan:12")
        )
        val decoded = json.decodeFromString(
            com.example.model.UserPreferences.serializer(),
            json.encodeToString(com.example.model.UserPreferences.serializer(), prefs)
        )
        assertEquals(true, decoded.premiereAlertsEnabled)
        assertEquals(listOf("jkanime:frieren:28", "latanime:dandadan:12"), decoded.notifiedPremiereKeys)
    }
}
