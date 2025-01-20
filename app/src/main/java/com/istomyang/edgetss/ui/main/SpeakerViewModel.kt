package com.istomyang.edgetss.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.istomyang.edgetss.data.SpeakerRepository
import com.istomyang.edgetss.data.Voice
import com.istomyang.edgetss.data.repositorySpeaker
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpeakerViewModel(
    private val speakerRepository: SpeakerRepository
) : ViewModel() {

    val speakerUiState: StateFlow<List<Speaker>> = combine(
        speakerRepository.getActiveFlow(),
        speakerRepository.getFlow(),
    ) { voice, voices ->
        val id = voice?.uid ?: ""
        voices.map { Speaker.from(it, id) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = emptyList()
    )

    private val _voicesUiState = MutableStateFlow(emptyList<Option>())
    val voicesUiState: StateFlow<List<Option>> = _voicesUiState.asStateFlow()

    private val _messageUiState = MutableStateFlow(null as Message?)
    val messageUiState: StateFlow<Message?> = _messageUiState.asStateFlow()

    fun loadVoices() {
        viewModelScope.launch {
            speakerRepository.fetchAll().onSuccess { voices ->
                _voicesUiState.update {
                    voices.filter { voice ->
                        !speakerUiState.value.any { voice.uid == it.id }
                    }.map { voice2Option(it) }
                }
            }.onFailure { e ->
                _messageUiState.update { Message(e.localizedMessage ?: "", true) }
            }
        }
    }


    fun addSpeakers(ids: List<String>) {
        viewModelScope.launch {
            speakerRepository.insert(ids.filter { id -> !speakerUiState.value.any { it.id == id } }.toSet())
        }
    }

    fun removeSpeakers(ids: List<String>) {
        viewModelScope.launch {
            // handle active
            val activeId = speakerRepository.getActive()?.uid ?: ""
            if (activeId in ids) {
                speakerRepository.removeActive()
            }
            // delete
            speakerRepository.delete(ids.toSet())
        }
    }

    fun setActiveSpeaker(id: String) {
        viewModelScope.launch {
            speakerRepository.removeActive()
            speakerRepository.setActive(id)
        }
    }

    val settingsUiState: StateFlow<SettingsData> = combine(
        speakerRepository.audioFormat(),
        flowOf("")
    ) { format, a ->
        SettingsData(format)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = SettingsData("")
    )

    fun setAudioFormat(format: String) {
        viewModelScope.launch {
            speakerRepository.setAudioFormat(format)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = this[APPLICATION_KEY]!!.applicationContext
                SpeakerViewModel(context.repositorySpeaker)
            }
        }
    }
}

data class Message(val description: String, val error: Boolean = false)

data class Speaker(
    val id: String,
    val name: String,
    val gender: String,
    val locale: String,
    val description: String,
    var active: Boolean = false,
) {
    companion object {
        fun from(voice: Voice, activeId: String): Speaker {
            val description = voice.voicePersonalities + voice.contentCategories
            return Speaker(
                id = voice.name,
                name = extractVoiceName(voice),
                gender = voice.gender,
                locale = voice.locale,
                description = description,
                active = activeId == voice.name
            )
        }
    }
}

private fun voice2Option(voice: Voice): Option {
    val locale = voice.locale
    val name = extractVoiceName(voice)
    val gender = voice.gender
    val description = voice.contentCategories
    val title = "$name - $gender - $locale - $description"
    val searchKey = title.replace("[^a-zA-Z]".toRegex(), "")
    return Option(title, voice.name, searchKey)
}

// en-US-AvaMultilingualNeural 提取 AvaMultilingualNeural 删除 Neural
private fun extractVoiceName(voice: Voice) = voice.shortName.split('-').last().replace("Neural", "")
