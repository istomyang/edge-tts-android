package com.istomyang.edgetss.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.istomyang.edgetss.data.LogRepository
import com.istomyang.edgetss.data.repositoryLog
import com.istomyang.edgetss.data.repositorySpeaker
import com.istomyang.edgetss.engine.request
import com.istomyang.edgetss.utils.intoPcm
import com.istomyang.edgetss.utils.intoPcmFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.lang.Exception

class EdgeTTSService : TextToSpeechService() {
    companion object {
        const val DOMAIN = "s/tts"
        const val DOMAIN_SPEECH = "$DOMAIN/speech"
    }

    private lateinit var log: LogRepository

    private var prepared = false
    private lateinit var language: String
    private lateinit var voiceName: String
    private lateinit var outputFormat: String
    private var useFlow = false
    private var sampleRate = 24000

    override fun onCreate() {
        super.onCreate()

        val context = this.applicationContext
        log = context.repositoryLog
        val speakerRepository = context.repositorySpeaker

        CoroutineScope(Dispatchers.IO).launch {
            speakerRepository.getActiveFlow().collect { voice ->
                if (voice == null) {
                    return@collect
                }
                language = voice.locale
                voiceName = voice.name
                prepared = true

                log.info(DOMAIN, "Use speaker: $voiceName - $language")
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            speakerRepository.audioFormat().collect {
                outputFormat = it
                sampleRate = if (it.contains("opus")) {
                    48000 // audio/opus in MediaCodec output sample rate change to 48khz.
                } else {
                    24000
                }
                log.info(DOMAIN, "Use audio output format: $outputFormat")
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            speakerRepository.useFlow().collect {
                useFlow = it
                log.info(DOMAIN, "Use Flow: $it")
            }
        }
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return TextToSpeech.LANG_AVAILABLE
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> {
        return arrayOf("", "", "")
    }

    override fun onStop() {}

    private val Flow<ByteArray>.intoPcm get() = intoPcmFlow(this)

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null || !prepared) {
            return
        }

        callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)

        val text = request.charSequenceText.toString()
        val pitch = request.pitch - 100
        val rate = request.speechRate - 100

        runBlocking {
            try {
                if (useFlow) {
                    runWithFlow(text, pitch, rate, callback)
                } else {
                    run(text, pitch, rate, callback)
                }
            } catch (e: Exception) {
                log.error(DOMAIN_SPEECH, "$text | ${e.message}")
                callback.error()
            }
        }
    }

    private suspend fun runWithFlow(
        text: String,
        pitch: Int,
        rate: Int,
        callback: SynthesisCallback
    ) {
        val t0 = System.currentTimeMillis()
        var t1 = 0L // net data downloaded
        var t2 = 0L // start speaking

        var size = 0
        val bufSize = callback.maxBufferSize
        var idx = 0
        val bytes = ByteArray(bufSize)

        request(
            language,
            voiceName,
            "${pitch}Hz",
            "${rate}%",
            "+0%",
            outputFormat,
            text,
        ).onEach {
            size += it.size
        }.onCompletion {
            val sizeKB = size.toDouble() / 1024.0
            t1 = System.currentTimeMillis()
            log.info(
                DOMAIN_SPEECH,
                "$text | ${text.length}words | ${sizeKB.toInt()}KB | download ${t1 - t0}ms | save ${t1 - t2}ms"
            )
        }.intoPcm.onCompletion {
            if (idx > 0) {
                callback.audioAvailable(bytes, 0, idx)
            }
            callback.done()
        }.collect {
            if (t2 == 0L) {
                t2 = System.currentTimeMillis()
            }
            for (b in it) {
                if (idx == bufSize - 1) {
                    callback.audioAvailable(bytes, 0, bufSize)
                    idx = 0
                } else {
                    bytes[idx] = b
                    idx += 1
                }
            }
        }
    }

    private suspend fun run(
        text: String,
        pitch: Int,
        rate: Int,
        callback: SynthesisCallback
    ) {
        val t0 = System.currentTimeMillis()
        var size = 0
        val data = ByteArrayOutputStream()

        // get data
        request(
            language,
            voiceName,
            "${pitch}Hz",
            "${rate}%",
            "+0%",
            outputFormat,
            text,
        ).onEach {
            size += it.size
        }.onCompletion {
            val sizeKB = size.toDouble() / 1024.0
            val t1 = System.currentTimeMillis() - t0
            log.info(DOMAIN_SPEECH, "$text | ${text.length}words | ${sizeKB.toInt()}KB | ${t1}ms")
        }.collect {
            data.write(it)
        }

        // covert to pcm
        val pcm = intoPcm(data.toByteArray())

        // write to callback
        val bufSize = callback.maxBufferSize
        var idx = 0
        val bytes = ByteArray(bufSize)
        for (b in pcm) {
            if (idx == bufSize - 1) {
                callback.audioAvailable(bytes, 0, bufSize)
                idx = 0
            } else {
                bytes[idx] = b
                idx += 1
            }
        }
        if (idx > 0) {
            callback.audioAvailable(bytes, 0, idx)
        }
    }
}

