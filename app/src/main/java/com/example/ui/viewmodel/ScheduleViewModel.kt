package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudflareBlockedException
import com.example.data.JkAnimeRepository
import com.example.model.AnimeProvider
import com.example.model.AnimeSchedule
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Success(val schedule: AnimeSchedule) : ScheduleUiState
    data class Error(val message: String, val blockedUrl: String? = null) : ScheduleUiState
}

class ScheduleViewModel(private val repository: JkAnimeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val selectedProvider: StateFlow<AnimeProvider> = repository.selectedProvider

    private val _selectedDayIndex = MutableStateFlow(todayMondayFirstIndex())
    val selectedDayIndex: StateFlow<Int> = _selectedDayIndex.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.selectedProvider.collect { provider ->
                loadSchedule(isRefresh = false, provider = provider)
            }
        }
    }

    fun selectProvider(provider: AnimeProvider) {
        if (repository.selectedProvider.value == provider) return
        _uiState.value = ScheduleUiState.Loading
        repository.setSelectedProvider(provider)
    }

    fun selectDay(index: Int) {
        _selectedDayIndex.value = index.coerceIn(0, 6)
    }

    fun loadSchedule(
        isRefresh: Boolean = true,
        provider: AnimeProvider = repository.selectedProvider.value
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
            } else if (_uiState.value !is ScheduleUiState.Success ||
                (_uiState.value as? ScheduleUiState.Success)?.schedule?.provider != provider
            ) {
                _uiState.value = ScheduleUiState.Loading
            }

            repository.getSchedule(provider)
                .onSuccess { schedule ->
                    _uiState.value = ScheduleUiState.Success(schedule)
                }
                .onFailure { error ->
                    val blocked = (error as? CloudflareBlockedException)?.targetUrl
                        ?: "${provider.baseUrl}/".takeIf {
                            error.message?.contains("Cloudflare", ignoreCase = true) == true
                        }
                    _uiState.value = ScheduleUiState.Error(
                        message = error.localizedMessage ?: "No se pudo cargar el calendario de ${provider.displayName}",
                        blockedUrl = blocked
                    )
                }
            _isRefreshing.value = false
        }
    }

    private fun todayMondayFirstIndex(): Int {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
    }
}
