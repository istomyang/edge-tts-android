package com.istomyang.edgetss.ui.view

import android.app.Application
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.istomyang.edgetss.data.PreferenceRepository
import com.istomyang.edgetss.data.SpeakerRepository
import com.istomyang.edgetss.data.database.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val LocalMainViewModel = compositionLocalOf<MainViewModel> {
    error("No ViewModel provided")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val preferenceRepository: PreferenceRepository
    private val speakerRepository: SpeakerRepository

    var speakers = MutableStateFlow<List<Speaker>>(emptyList())


    fun fetchLanguages(cb: (data: List<String>) -> Unit) {
        viewModelScope.launch {
            val data = speakerRepository.queryLocales().map {
                it.split("-").first()
            }.distinct()
            cb(data)
        }
    }

    fun fetchCountries(lang: String, cb: (data: List<String>) -> Unit) {
        viewModelScope.launch {
            val data = speakerRepository.queryLocales().filter { it.startsWith(lang) }
                .map { it.split("-").last() }.distinct()
            cb(data)
        }
    }

    fun fetchVoices(locale: String, cb: (data: List<VoiceInfo>) -> Unit) {
        viewModelScope.launch {
            val data = speakerRepository.query(locale).map { VoiceInfo.from(it) }
            cb(data)
        }
    }


    fun addSpeaker(id: Int) {
        viewModelScope.launch {
            preferenceRepository.addSelectedSpeakerId(id)
        }
    }

    fun setActiveSpeakerId(id: Int?) {
        viewModelScope.launch {
            preferenceRepository.setActiveSpeakerId(id)
        }
    }

    fun deleteSelectedSpeakerIds(ids: List<Int>) {
        viewModelScope.launch {
            val activeId = preferenceRepository.activeSpeakerId.first()
            if (ids.any { it == activeId }) {
                setActiveSpeakerId(null)
            }
            val undeleted = speakers.value.filter { !ids.contains(it.id) }.map { it.id }
            preferenceRepository.setSelectedSpeakerIds(undeleted)
        }
    }

    fun ensureLocalInitialized() {
        CoroutineScope(Dispatchers.IO).launch {
            speakerRepository.ensureLocalInitialized()
        }
    }

    init {
        val ctx = application.applicationContext
        preferenceRepository = PreferenceRepository.create(ctx)
        speakerRepository = SpeakerRepository.create(ctx)

        viewModelScope.launch {
            preferenceRepository.selectedSpeakerIds.collect { ids ->
                if (ids.isNotEmpty()) {
                    val active = preferenceRepository.activeSpeakerId.first()
                    speakers.value = speakerRepository.getByIds(ids).map {
                        val speaker = Speaker.from(it)
                        speaker.active = speaker.id == active
                        speaker
                    }
                }
            }
            preferenceRepository.activeSpeakerId.collect { id ->
                if (id == null) {
                    return@collect
                }
                for (speaker in speakers.value) {
                    speaker.active = speaker.id == id
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}

data class Speaker(
    val id: Int,
    val name: String,
    val gender: String,
    val locale: String,
    val description: String,
    var active: Boolean = false,
) {
    companion object {
        fun from(voice: Voice): Speaker {
            val description = voice.voicePersonalities + voice.contentCategories
            return Speaker(
                id = voice.uid,
                name = extractVoiceName(voice),
                gender = voice.gender,
                locale = voice.locale,
                description = description,
            )
        }
    }
}

data class VoiceInfo(val title: String, val id: Int) {
    companion object {
        fun from(voice: Voice): VoiceInfo {
            val name = extractVoiceName(voice)
            val gender = voice.gender
            val description = voice.contentCategories
            return VoiceInfo("$name - $gender - $description", voice.uid)
        }
    }
}

// en-US-AvaMultilingualNeural 提取 AvaMultilingualNeural 删除 Neural
private fun extractVoiceName(voice: Voice) = voice.shortName.split('-').last().replace("Neural", "")
