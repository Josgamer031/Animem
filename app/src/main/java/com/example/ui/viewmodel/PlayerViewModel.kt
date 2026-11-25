package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudflareBlockedException
import com.example.data.JkAnimeRepository
import com.example.model.AnimeProvider
import com.example.model.ResolvedVideo
import com.example.model.VideoServer
import com.example.model.WatchHistoryEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class ServerPlaybackSupport {
    CHECKING,
    NATIVE_STREAM,
    UNAVAILABLE
}

data class PlayerUiState(
    val slug: String,
    val episodeNumber: Int,
    val episodeUrl: String = "",
    val provider: AnimeProvider = AnimeProvider.JKANIME,
    val animeTitle: String,
    val coverUrl: String,
    val hasNextEpisode: Boolean = false,
    val isLoadingServers: Boolean = true,
    val isResolvingStream: Boolean = false,
    val servers: List<VideoServer> = emptyList(),
    val serverSupportMap: Map<String, ServerPlaybackSupport> = emptyMap(),
    val selectedServerUrl: String = "",
    val selectedServerIndex: Int = 0,
    val resolvedVideo: ResolvedVideo? = null,
    val resumePositionMs: Long = 0L,
    val errorMessage: String? = null,
    val blockedUrl: String? = null
)

class PlayerViewModel(
    private val repository: JkAnimeRepository,
    initialSlug: String,
    initialEpisodeNumber: Int,
    initialTitle: String,
    initialCoverUrl: String,
    initialProvider: AnimeProvider = AnimeProvider.JKANIME,
    initialEpisodeUrl: String = ""
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            slug = initialSlug,
            episodeNumber = initialEpisodeNumber,
            episodeUrl = initialEpisodeUrl,
            provider = initialProvider,
            animeTitle = initialTitle.ifBlank {
                initialSlug.replace('-', ' ').replaceFirstChar { it.uppercase() }
            },
            coverUrl = initialCoverUrl
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var resolveJob: Job? = null
    private var probeJob: Job? = null
    private var allCandidateServers: List<VideoServer> = emptyList()
    private val resolvedCache = mutableMapOf<String, ResolvedVideo>()
    private val sniffAttemptedServers = mutableSetOf<String>()
    private val failedServers = mutableSetOf<String>()

    init {
        loadEpisode(initialEpisodeNumber, initialEpisodeUrl)
    }

    private fun serverKey(server: VideoServer): String =
        server.decodedEmbedUrl.ifBlank { "${server.name}_${server.rawUrl}" }

    fun loadEpisode(episodeNumber: Int, explicitEpisodeUrl: String = "") {
        if (episodeNumber < 1) return
        loadJob?.cancel()
        resolveJob?.cancel()
        probeJob?.cancel()
        allCandidateServers = emptyList()
        resolvedCache.clear()
        sniffAttemptedServers.clear()
        failedServers.clear()
        loadJob = viewModelScope.launch {
            val slug = _uiState.value.slug
            val provider = _uiState.value.provider
            val resolvedEpUrl = repository.resolveEpisodeUrl(slug, episodeNumber, provider, explicitEpisodeUrl)

            val savedEntry = repository.watchHistory?.firstOrNull()
                ?.firstOrNull {
                    it.animeSlug == slug &&
                        it.episodeNumber == episodeNumber &&
                        it.providerId == provider.id
                }

            _uiState.update {
                it.copy(
                    episodeNumber = episodeNumber,
                    episodeUrl = resolvedEpUrl,
                    hasNextEpisode = false,
                    isLoadingServers = true,
                    isResolvingStream = false,
                    servers = emptyList(),
                    serverSupportMap = emptyMap(),
                    selectedServerUrl = "",
                    selectedServerIndex = 0,
                    resolvedVideo = null,
                    resumePositionMs = savedEntry?.positionMs ?: 0L,
                    errorMessage = null,
                    blockedUrl = null
                )
            }

            if (_uiState.value.coverUrl.isBlank()) {
                repository.getAnimeDetail(slug, provider).onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            animeTitle = detail.title,
                            coverUrl = detail.coverUrl
                        )
                    }
                }
            }

            repository.getEpisodePlaybackInfo(slug, episodeNumber, provider, resolvedEpUrl)
                .onSuccess { info ->
                    val servers = info.servers
                    val hasNext = info.hasNextEpisode ?: false
                    if (servers.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                episodeUrl = info.resolvedEpisodeUrl.ifBlank { it.episodeUrl },
                                hasNextEpisode = hasNext,
                                isLoadingServers = false,
                                errorMessage = "No se encontraron servidores disponibles para el episodio $episodeNumber."
                            )
                        }
                    } else {
                        val prepared = prepareServersForProvider(servers, provider)
                        allCandidateServers = prepared
                        val initialSupport = prepared.associate { serverKey(it) to ServerPlaybackSupport.CHECKING }
                        val firstKey = serverKey(prepared.first())
                        _uiState.update {
                            it.copy(
                                episodeUrl = info.resolvedEpisodeUrl.ifBlank { it.episodeUrl },
                                hasNextEpisode = hasNext,
                                isLoadingServers = false,
                                servers = prepared,
                                serverSupportMap = initialSupport,
                                selectedServerUrl = firstKey,
                                selectedServerIndex = 0
                            )
                        }
                        resolveServerByKey(
                            key = firstKey,
                            triggerBackgroundProbe = true
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingServers = false,
                            errorMessage = mapFriendlyError(err),
                            blockedUrl = (err as? CloudflareBlockedException)?.targetUrl
                        )
                    }
                }
        }
    }

    fun skipToNextEpisodeIfAvailable() {
        val state = _uiState.value
        if (state.hasNextEpisode) {
            loadEpisode(state.episodeNumber + 1)
        }
    }

    private fun probeRemainingServers(
        servers: List<VideoServer>,
        slug: String,
        episodeNumber: Int,
        initialKey: String
    ) {
        val candidates = servers.filter { serverKey(it) != initialKey }
        if (candidates.isEmpty()) return
        val provider = _uiState.value.provider
        val epUrl = _uiState.value.episodeUrl

        probeJob = viewModelScope.launch {
            // Phase 1: Fast parallel HTTP extraction for instant native servers
            coroutineScope {
                candidates.map { server ->
                    async {
                        val key = serverKey(server)
                        val resolved = withTimeoutOrNull(6_000L) {
                            repository.resolveServerVideo(
                                server = server,
                                slug = slug,
                                episodeNumber = episodeNumber,
                                provider = provider,
                                episodeUrl = epUrl,
                                allowWebSniff = false,
                                forceWebSniff = false
                            ).getOrNull()
                        }
                        if (resolved != null && resolved.isDirectStream && !resolved.streamUrl.isNullOrBlank()) {
                            resolvedCache[key] = resolved
                            updateServerSupport(episodeNumber, key, ServerPlaybackSupport.NATIVE_STREAM)
                        }
                    }
                }.awaitAll()
            }

            // Phase 2: Sequential headless stream sniffer for any remaining servers
            for (server in candidates) {
                if (_uiState.value.episodeNumber != episodeNumber) return@launch
                val key = serverKey(server)
                if (resolvedCache[key]?.isDirectStream == true && !resolvedCache[key]?.streamUrl.isNullOrBlank()) {
                    continue
                }
                if (key in sniffAttemptedServers) continue

                sniffAttemptedServers.add(key)
                val resolved = withTimeoutOrNull(12_000L) {
                    repository.resolveServerVideo(
                        server = server,
                        slug = slug,
                        episodeNumber = episodeNumber,
                        provider = provider,
                        episodeUrl = epUrl,
                        allowWebSniff = true,
                        forceWebSniff = false
                    ).getOrNull()
                }
                if (resolved != null && resolved.isDirectStream && !resolved.streamUrl.isNullOrBlank()) {
                    resolvedCache[key] = resolved
                    updateServerSupport(episodeNumber, key, ServerPlaybackSupport.NATIVE_STREAM)
                } else {
                    failedServers.add(key)
                    updateServerSupport(episodeNumber, key, ServerPlaybackSupport.UNAVAILABLE)
                }
            }
        }
    }

    private fun updateServerSupport(
        episodeNumber: Int,
        key: String,
        support: ServerPlaybackSupport
    ) {
        _uiState.update { state ->
            if (state.episodeNumber != episodeNumber) return@update state
            val updatedMap = state.serverSupportMap + (key to support)
            // Keep only non-UNAVAILABLE servers visible (hide web-only / unplayable servers)
            val filteredServers = allCandidateServers.filter {
                updatedMap[serverKey(it)] != ServerPlaybackSupport.UNAVAILABLE
            }
            val newSelectedIdx = filteredServers
                .indexOfFirst { serverKey(it) == state.selectedServerUrl }
                .coerceAtLeast(0)
            state.copy(
                servers = filteredServers,
                serverSupportMap = updatedMap,
                selectedServerIndex = if (filteredServers.isEmpty()) 0 else newSelectedIdx
            )
        }
    }

    fun selectServer(index: Int) {
        val state = _uiState.value
        val server = state.servers.getOrNull(index) ?: return
        val key = serverKey(server)
        _uiState.update {
            it.copy(
                selectedServerUrl = key,
                selectedServerIndex = index,
                errorMessage = null
            )
        }
        resolveServerByKey(key)
    }

    fun onNativePlaybackFailed(errorDetail: String = "") {
        val current = _uiState.value
        val activeServer = current.servers.getOrNull(current.selectedServerIndex)
            ?: current.servers.firstOrNull { serverKey(it) == current.selectedServerUrl }
        val key = activeServer?.let(::serverKey) ?: current.selectedServerUrl

        val isOfflineOrDnsError = errorDetail.contains("UnknownHostException", ignoreCase = true) ||
            errorDetail.contains("no network", ignoreCase = true) ||
            errorDetail.contains("Unable to resolve host", ignoreCase = true)

        if (isOfflineOrDnsError && errorDetail.contains("no network", ignoreCase = true)) {
            _uiState.update { state ->
                state.copy(
                    errorMessage = "Sin conexión a internet en el dispositivo. Activa la red y reintenta."
                )
            }
            return
        }

        // 1. Retry this server with live stream sniffer if not yet sniffed
        if (key.isNotBlank() && key !in sniffAttemptedServers && current.resolvedVideo?.usedWebSniff != true) {
            sniffAttemptedServers.add(key)
            resolvedCache.remove(key)
            resolveServerByKey(key, forceWebSniff = true)
            return
        }

        if (key.isNotBlank()) {
            failedServers.add(key)
            updateServerSupport(current.episodeNumber, key, ServerPlaybackSupport.UNAVAILABLE)
        }

        val updatedState = _uiState.value
        val nextServer = updatedState.servers.firstOrNull { serverKey(it) !in failedServers }
        if (nextServer != null) {
            val nextIdx = updatedState.servers.indexOf(nextServer)
            selectServer(nextIdx)
        } else {
            _uiState.update { state ->
                state.copy(
                    isResolvingStream = false,
                    resolvedVideo = null,
                    errorMessage = if (isOfflineOrDnsError) {
                        "Sin conexión a internet o el servidor de video no responde. Verifica tu red y reintenta."
                    } else {
                        "Ningún servidor nativo pudo reproducir el episodio. Verifica tu conexión y reintenta."
                    }
                )
            }
        }
    }

    private fun resolveServerByKey(
        key: String,
        forceWebSniff: Boolean = false,
        triggerBackgroundProbe: Boolean = false
    ) {
        val state = _uiState.value
        val server = allCandidateServers.firstOrNull { serverKey(it) == key }
            ?: state.servers.firstOrNull { serverKey(it) == key }
            ?: return

        if (!forceWebSniff) {
            resolvedCache[key]?.let { cached ->
                if (cached.isDirectStream && !cached.streamUrl.isNullOrBlank()) {
                    _uiState.update {
                        val idx = it.servers.indexOfFirst { s -> serverKey(s) == key }.coerceAtLeast(0)
                        it.copy(
                            isResolvingStream = false,
                            resolvedVideo = cached,
                            selectedServerUrl = key,
                            selectedServerIndex = idx,
                            serverSupportMap = it.serverSupportMap + (key to ServerPlaybackSupport.NATIVE_STREAM),
                            errorMessage = null
                        )
                    }
                    if (triggerBackgroundProbe && probeJob == null) {
                        probeRemainingServers(allCandidateServers, state.slug, state.episodeNumber, key)
                    }
                    return
                }
            }
        }

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingStream = true,
                    resolvedVideo = null,
                    errorMessage = null
                )
            }
            if (forceWebSniff) {
                sniffAttemptedServers.add(key)
            }

            val resolved = repository.resolveServerVideo(
                server = server,
                slug = state.slug,
                episodeNumber = state.episodeNumber,
                provider = state.provider,
                episodeUrl = state.episodeUrl,
                allowWebSniff = true,
                forceWebSniff = forceWebSniff
            ).getOrNull()

            if (triggerBackgroundProbe && (probeJob == null || probeJob?.isActive != true)) {
                probeRemainingServers(allCandidateServers, state.slug, state.episodeNumber, key)
            }

            val hasDirect = resolved != null && resolved.isDirectStream && !resolved.streamUrl.isNullOrBlank()
            if (hasDirect && resolved != null) {
                resolvedCache[key] = resolved
                _uiState.update {
                    val idx = it.servers.indexOfFirst { s -> serverKey(s) == key }.coerceAtLeast(0)
                    it.copy(
                        isResolvingStream = false,
                        resolvedVideo = resolved,
                        selectedServerUrl = key,
                        selectedServerIndex = idx,
                        serverSupportMap = it.serverSupportMap + (key to ServerPlaybackSupport.NATIVE_STREAM),
                        errorMessage = null
                    )
                }
            } else {
                // Strictly native playback: mark unavailable & auto-advance to another server if possible
                failedServers.add(key)
                updateServerSupport(state.episodeNumber, key, ServerPlaybackSupport.UNAVAILABLE)
                val latest = _uiState.value
                val nextServer = latest.servers.firstOrNull { serverKey(it) !in failedServers }
                if (nextServer != null) {
                    val nextKey = serverKey(nextServer)
                    val nextIdx = latest.servers.indexOf(nextServer)
                    _uiState.update {
                        it.copy(
                            selectedServerUrl = nextKey,
                            selectedServerIndex = nextIdx
                        )
                    }
                    resolveServerByKey(nextKey, triggerBackgroundProbe = false)
                } else {
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            resolvedVideo = null,
                            errorMessage = "No se encontró un stream nativo compatible para este episodio."
                        )
                    }
                }
            }
        }
    }

    fun saveWatchProgress(positionMs: Long, durationMs: Long) {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveWatchProgress(
                WatchHistoryEntry(
                    animeSlug = state.slug,
                    animeTitle = state.animeTitle,
                    coverUrl = state.coverUrl,
                    episodeNumber = state.episodeNumber,
                    positionMs = positionMs.coerceAtLeast(0L),
                    durationMs = durationMs.coerceAtLeast(0L),
                    updatedAtMillis = System.currentTimeMillis(),
                    providerId = state.provider.id,
                    episodeUrl = state.episodeUrl
                )
            )
        }
    }

    private fun mapFriendlyError(err: Throwable): String {
        val msg = err.localizedMessage.orEmpty()
        return if (msg.contains("UnknownHostException", ignoreCase = true) ||
            msg.contains("no network", ignoreCase = true) ||
            msg.contains("Unable to resolve host", ignoreCase = true)
        ) {
            "Sin conexión a internet. Verifica tu red e intenta de nuevo."
        } else {
            msg.ifBlank { "Error al extraer servidores del episodio" }
        }
    }

    private fun prepareServersForProvider(
        servers: List<VideoServer>,
        provider: AnimeProvider
    ): List<VideoServer> {
        if (provider == AnimeProvider.JKANIME) {
            val desuMatches = servers.filter { server ->
                server.name.contains("desu", ignoreCase = true) ||
                    server.decodedEmbedUrl.contains("um2.php", ignoreCase = true) ||
                    server.decodedEmbedUrl.contains("um.php", ignoreCase = true) ||
                    server.decodedEmbedUrl.contains("desu", ignoreCase = true)
            }
            if (desuMatches.isNotEmpty()) {
                val namedDesu = desuMatches.filter {
                    it.name.contains("desu", ignoreCase = true) ||
                        it.decodedEmbedUrl.contains("desu", ignoreCase = true)
                }
                return (namedDesu.ifEmpty { desuMatches }).take(1)
            }
            // Fallback: keep top native-extractable hosts if Desu isn't listed on an anime
            val priority = listOf("streamwish", "filemoon", "voe", "vidhide", "mp4upload", "mixdrop")
            return servers.sortedBy { server ->
                val lower = server.name.lowercase()
                val idx = priority.indexOfFirst { lower.contains(it) }
                if (idx >= 0) idx else priority.size
            }
        }

        // Latanime: prioritize native-extractable hosts first; web-only ones get filtered out automatically
        val priority = listOf("streamwish", "ok.ru", "filemoon", "voe", "vidhide", "mp4upload", "mixdrop", "dood", "streamtape")
        return servers.sortedBy { server ->
            val lower = server.name.lowercase()
            val idx = priority.indexOfFirst { lower.contains(it) }
            if (idx >= 0) idx else priority.size
        }
    }
}
