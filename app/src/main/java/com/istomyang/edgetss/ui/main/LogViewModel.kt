package com.istomyang.edgetss.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.istomyang.edgetss.data.Log
import com.istomyang.edgetss.data.LogLevel
import com.istomyang.edgetss.data.LogRepository
import com.istomyang.edgetss.data.repositoryLog
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private val logLevel = MutableStateFlow("all")

    fun loadLogs() {
        viewModelScope.launch {
            val levels = getLevels()
            val data = logRepository.queryAll(levels)
            updateLines(data)
        }
    }

    fun collectLogs() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val o = _linesUiState.value.count()
                val levels = getLevels()
                val data = logRepository.query(levels, o, 50)
                updateLines(data)
            }
        }
    }

    private fun getLevels(): List<String> {
        return if (logLevel.value == "all") {
            listOf(LogLevel.INFO.name, LogLevel.DEBUG.name, LogLevel.ERROR.name)
        } else {
            listOf(logLevel.value)
        }
    }

    private fun updateLines(newLines: List<Log>) {
        _linesUiState.update { it + newLines.map { log -> "${ts2DateTime(log.createdAt)} ${log.level} ${log.domain}: ${log.message}" } }
    }

    fun setLogLevel(level: String) {
        logLevel.value = level
        _linesUiState.update { emptyList() } // clear screen.
        loadLogs()
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = this[APPLICATION_KEY]!!.applicationContext
                LogViewModel(context.repositoryLog)
            }
        }
    }
}