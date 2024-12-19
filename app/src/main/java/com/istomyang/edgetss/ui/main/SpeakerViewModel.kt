package com.istomyang.edgetss.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.istomyang.edgetss.data.SpeakerRepository
import com.istomyang.edgetss.data.Voice
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val voiceData = mutableListOf<Voice>()
    private val _voicesUiState = MutableStateFlow(emptyList<Option>())
    val voicesUiState: StateFlow<List<Option>> = _voicesUiState.asStateFlow()

    private val _messageUiState = MutableStateFlow(null as Message?)
    val messageUiState: StateFlow<Message?> = _messageUiState.asStateFlow()

    fun loadVoices() {
        viewModelScope.launch {
            speakerRepository.fetchAll().onSuccess { voices ->
                voiceData.clear()
                voiceData.addAll(voices)
                _voicesUiState.update { voices.map { voice2Option(it) } }
            }.onFailure { e ->
                _messageUiState.update { Message(e.localizedMessage ?: "", true) }
            }
        }
    }

    fun addSpeakers(ids: List<String>) {
        val voices = voiceData.filter { it.uid in ids && !speakerUiState.value.any { has -> it.uid == has.id } }
        viewModelScope.launch {
            speakerRepository.insert(voices)
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

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val context = this[APPLICATION_KEY]!!.applicationContext
                SpeakerViewModel(
                    SpeakerRepository.create(context)
                )
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
    return Option("$name - $gender - $locale - $description", voice.name)
}

// en-US-AvaMultilingualNeural 提取 AvaMultilingualNeural 删除 Neural
private fun extractVoiceName(voice: Voice) = voice.shortName.split('-').last().replace("Neural", "")
