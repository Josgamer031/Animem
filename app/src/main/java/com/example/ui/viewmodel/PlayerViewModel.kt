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

enum class PlayerMode {
    NATIVE_EXO,
    IN_APP_WEB
}

enum class ServerPlaybackSupport {
    CHECKING,
    NATIVE_STREAM,
    WEB_EMBED
}

data class PlayerUiState(
    val slug: String,
    val episodeNumber: Int,
    val episodeUrl: String = "",
    val provider: AnimeProvider = AnimeProvider.JKANIME,
    val animeTitle: String,
    val coverUrl: String,
    val isLoadingServers: Boolean = true,
    val isResolvingStream: Boolean = false,
    val servers: List<VideoServer> = emptyList(),
    val serverSupportMap: Map<Int, ServerPlaybackSupport> = emptyMap(),
    val selectedServerIndex: Int = 0,
    val resolvedVideo: ResolvedVideo? = null,
    val playerMode: PlayerMode = PlayerMode.NATIVE_EXO,
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
    private val resolvedCache = mutableMapOf<Int, ResolvedVideo>()

    init {
        loadEpisode(initialEpisodeNumber, initialEpisodeUrl)
    }

    fun loadEpisode(episodeNumber: Int, explicitEpisodeUrl: String = "") {
        if (episodeNumber < 1) return
        loadJob?.cancel()
        resolveJob?.cancel()
        probeJob?.cancel()
        resolvedCache.clear()
        loadJob = viewModelScope.launch {
            val slug = _uiState.value.slug
            val provider = _uiState.value.provider
            val resolvedEpUrl = repository.resolveEpisodeUrl(slug, episodeNumber, provider, explicitEpisodeUrl)

            // Check saved resume position
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
                    isLoadingServers = true,
                    isResolvingStream = false,
                    servers = emptyList(),
                    serverSupportMap = emptyMap(),
                    resolvedVideo = null,
                    resumePositionMs = savedEntry?.positionMs ?: 0L,
                    errorMessage = null,
                    blockedUrl = null
                )
            }

            // Enrich title/cover if missing
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

            repository.getEpisodeServers(slug, episodeNumber, provider, resolvedEpUrl)
                .onSuccess { servers ->
                    if (servers.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoadingServers = false,
                                errorMessage = "No se encontraron servidores disponibles para el episodio $episodeNumber."
                            )
                        }
                    } else {
                        val prioritized = prioritizeServers(servers)
                        val initialSupport = prioritized.indices.associateWith { ServerPlaybackSupport.CHECKING }
                        _uiState.update {
                            it.copy(
                                isLoadingServers = false,
                                servers = prioritized,
                                serverSupportMap = initialSupport,
                                selectedServerIndex = 0
                            )
                        }
                        resolveServerAt(0, triggerBackgroundProbe = true)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingServers = false,
                            errorMessage = err.localizedMessage ?: "Error al extraer servidores del episodio",
                            blockedUrl = (err as? CloudflareBlockedException)?.targetUrl
                        )
                    }
                }
        }
    }

    private fun probeRemainingServers(
        servers: List<VideoServer>,
        slug: String,
        episodeNumber: Int
    ) {
        if (servers.size <= 1) return
        val provider = _uiState.value.provider
        val epUrl = _uiState.value.episodeUrl
        probeJob = viewModelScope.launch {
            coroutineScope {
                (1 until servers.size).map { idx ->
                    async {
                        val server = servers[idx]
                        val resolved = withTimeoutOrNull(6_000L) {
                            repository.resolveServerVideo(server, slug, episodeNumber, provider, epUrl).getOrNull()
                        } ?: ResolvedVideo(
                            serverName = server.name,
                            streamUrl = null,
                            embedUrl = server.decodedEmbedUrl,
                            isDirectStream = false
                        )
                        resolvedCache[idx] = resolved
                        val support = if (resolved.isDirectStream && !resolved.streamUrl.isNullOrBlank()) {
                            ServerPlaybackSupport.NATIVE_STREAM
                        } else {
                            ServerPlaybackSupport.WEB_EMBED
                        }
                        _uiState.update { state ->
                            if (state.episodeNumber == episodeNumber) {
                                state.copy(serverSupportMap = state.serverSupportMap + (idx to support))
                            } else {
                                state
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    fun selectServer(index: Int) {
        val state = _uiState.value
        if (index !in state.servers.indices) return
        _uiState.update { it.copy(selectedServerIndex = index, errorMessage = null) }
        resolveServerAt(index)
    }

    fun setPlayerMode(mode: PlayerMode) {
        _uiState.update { it.copy(playerMode = mode) }
    }

    fun onNativePlaybackFailed() {
        val current = _uiState.value
        val nextIndex = current.selectedServerIndex + 1
        // If current server had a direct stream that failed in ExoPlayer, fall back to its embed web player first or try next server
        if (current.playerMode == PlayerMode.NATIVE_EXO && current.resolvedVideo?.embedUrl?.isNotBlank() == true) {
            _uiState.update { state ->
                state.copy(
                    playerMode = PlayerMode.IN_APP_WEB,
                    serverSupportMap = state.serverSupportMap + (state.selectedServerIndex to ServerPlaybackSupport.WEB_EMBED)
                )
            }
        } else if (nextIndex in current.servers.indices) {
            selectServer(nextIndex)
        }
    }

    private fun resolveServerAt(index: Int, triggerBackgroundProbe: Boolean = false) {
        val state = _uiState.value
        val server = state.servers.getOrNull(index) ?: return

        resolvedCache[index]?.let { cached ->
            val targetMode = if (cached.isDirectStream && !cached.streamUrl.isNullOrBlank()) {
                PlayerMode.NATIVE_EXO
            } else {
                PlayerMode.IN_APP_WEB
            }
            val support = if (targetMode == PlayerMode.NATIVE_EXO) {
                ServerPlaybackSupport.NATIVE_STREAM
            } else {
                ServerPlaybackSupport.WEB_EMBED
            }
            _uiState.update {
                it.copy(
                    isResolvingStream = false,
                    resolvedVideo = cached,
                    playerMode = targetMode,
                    serverSupportMap = it.serverSupportMap + (index to support)
                )
            }
            if (triggerBackgroundProbe && probeJob == null) {
                probeRemainingServers(state.servers, state.slug, state.episodeNumber)
            }
            return
        }

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            _uiState.update { it.copy(isResolvingStream = true, resolvedVideo = null) }
            val resolved = repository.resolveServerVideo(
                server = server,
                slug = state.slug,
                episodeNumber = state.episodeNumber,
                provider = state.provider,
                episodeUrl = state.episodeUrl
            )
                .getOrElse {
                    ResolvedVideo(
                        serverName = server.name,
                        streamUrl = null,
                        embedUrl = server.decodedEmbedUrl,
                        isDirectStream = false
                    )
                }
            resolvedCache[index] = resolved
            val targetMode = if (resolved.isDirectStream && !resolved.streamUrl.isNullOrBlank()) {
                PlayerMode.NATIVE_EXO
            } else {
                PlayerMode.IN_APP_WEB
            }
            val support = if (targetMode == PlayerMode.NATIVE_EXO) {
                ServerPlaybackSupport.NATIVE_STREAM
            } else {
                ServerPlaybackSupport.WEB_EMBED
            }
            _uiState.update {
                it.copy(
                    isResolvingStream = false,
                    resolvedVideo = resolved,
                    playerMode = targetMode,
                    serverSupportMap = it.serverSupportMap + (index to support)
                )
            }
            if (triggerBackgroundProbe && (probeJob == null || probeJob?.isActive != true)) {
                probeRemainingServers(state.servers, state.slug, state.episodeNumber)
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

    private fun prioritizeServers(servers: List<VideoServer>): List<VideoServer> {
        val priority = listOf("desu", "jkplayer", "ok.ru", "streamwish", "filemoon", "voe", "vidhide", "mp4upload", "mixdrop")
        return servers.sortedBy { server ->
            val lower = server.name.lowercase()
            val idx = priority.indexOfFirst { lower.contains(it) }
            if (idx >= 0) idx else priority.size
        }
    }
}
