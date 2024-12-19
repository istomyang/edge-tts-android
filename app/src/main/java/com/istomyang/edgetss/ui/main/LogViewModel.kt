package com.istomyang.edgetss.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.istomyang.edgetss.data.LogRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter


class LogViewModel(
    val logRepository: LogRepository,
) : ViewModel() {

    private val _linesUiState = MutableStateFlow(emptyList<String>())
    val linesUiState: StateFlow<List<String>> = _linesUiState.asStateFlow()

    fun loadLog() {
        viewModelScope.launch {
            val o = _linesUiState.value.count()
            val data = logRepository.query(o, 50)
            _linesUiState.update { it + data.map { log -> "${ts2DateTime(log.createdAt)} ${log.level} ${log.domain}: ${log.message}" } }
        }
    }

    fun openLog(b: Boolean) {
        viewModelScope.launch {
            logRepository.open(b)
        }
    }

    fun clearLog() {
        viewModelScope.launch {
            _linesUiState.update { emptyList() }
            logRepository.clear()
        }
    }

    val logOpened: StateFlow<Boolean> = logRepository.enabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = false
    )

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    private fun ts2DateTime(ts: Long): String {
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(ts),
            ZoneId.systemDefault()
        ).format(
            DateTimeFormatter.ofPattern("MM-dd-HH:mm:ss")
        )
    }

    companion object {
        fun factory(context: Context): LogViewModel {
            return LogViewModel(
                LogRepository.create(context),
            )
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = this[APPLICATION_KEY]!!.applicationContext
                LogViewModel(
                    LogRepository.create(context),
                )
            }
        }
    }
}