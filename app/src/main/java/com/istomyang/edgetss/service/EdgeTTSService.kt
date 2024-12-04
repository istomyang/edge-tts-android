package com.istomyang.edgetss.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.istomyang.edgetss.data.PreferenceRepository
import com.istomyang.edgetss.data.SpeakerRepository
import com.istomyang.edgetss.engine.request
import com.istomyang.edgetss.utils.mp3ToPcm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class EdgeTTSService : TextToSpeechService() {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private lateinit var preferenceRepository: PreferenceRepository


    private var prepared = false
    private lateinit var language: String
    private lateinit var voiceName: String
    private lateinit var outputFormat: String

    override fun onCreate() {
        super.onCreate()
        
        val preferenceRepository = PreferenceRepository.create(this)
        val speakerRepository = SpeakerRepository.create(this)

        CoroutineScope(Dispatchers.IO).launch {
            preferenceRepository.activeSpeakerId.collectLatest { id ->
                if (id == null) return@collectLatest
                val speaker = speakerRepository.getById(id) ?: return@collectLatest
                language = speaker.locale
                voiceName = speaker.name
                outputFormat = speaker.suggestedCodec
                prepared = true
            }
        }
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return TextToSpeech.LANG_AVAILABLE;
    }


    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> {
        return arrayOf("", "", "")
    }

    override fun onStop() {}

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null || !prepared) {
            return
        }

        val text = request.charSequenceText.toString()
        val pitch = request.pitch - 100
        val rate = request.speechRate - 100

        callback.start(24000, AudioFormat.ENCODING_PCM_16BIT, 1)

        runBlocking {
            request(
                language,
                voiceName,
                "${pitch}Hz",
                "${rate}%",
                "+0%",
                outputFormat,
                text,
            ).onSuccess { data ->
                val pcmData = mp3ToPcm(data)
                sendAudioData(callback, pcmData).onFailure {
                    callback.error()
                }.onSuccess {
                    callback.done()
                }
            }.onFailure {
                callback.error()
            }
        }
    }

    private fun sendAudioData(callback: SynthesisCallback, audioData: ByteArray): Result<Unit> {
        val bufferSize = callback.maxBufferSize
        val length = audioData.size
        var offset = 0

        while (offset < length) {
            val bytesToWrite = bufferSize.coerceAtMost(length - offset)
            val result = callback.audioAvailable(audioData, offset, bytesToWrite)
            if (result != TextToSpeech.SUCCESS) {
                return Result.failure(Throwable("error"))
            }
            offset += bytesToWrite
        }

        return Result.success(Unit)
    }
}

