package com.istomyang.edgetss.service

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import com.istomyang.edgetss.data.LogRepository
import com.istomyang.edgetss.data.SpeakerRepository
import com.istomyang.edgetss.data.repositoryLog
import com.istomyang.edgetss.data.repositorySpeaker
import com.istomyang.edgetss.utils.Codec
import com.istomyang.tts_engine.TTS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.String

class EdgeTTSService : TextToSpeechService() {
    companion object {
        private const val LOG_NAME = "EdgeTTSService"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var engine: TTS
    private lateinit var logRepository: LogRepository
    private lateinit var speakerRepository: SpeakerRepository

    private var prepared = false
    private var locale: String? = null
    private var voiceName: String? = null
    private var outputFormat: String? = null
    private var sampleRate = 24000

    override fun onCreate() {
        super.onCreate()

        val context = this.applicationContext
        logRepository = context.repositoryLog
        speakerRepository = context.repositorySpeaker

        engine = TTS()

        debug("start launching TTS engine.")

        scope.launch {
            launch { collectConfig() }

            launch { collectAudioFromEngine() }

            try {
                engine.run()
            } catch (_: CancellationException) {
            } catch (e: Throwable) {
                synthesisChannel.close() // tell error occurs.
                error("engine run error: $e")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onStop() {
        runBlocking {
            engine.close()
        }
    }

    private suspend fun collectConfig() {
        var voiceOk = false
        var formatOk = false

        combine(
            speakerRepository.getActiveFlow(),
            speakerRepository.audioFormat()
        ) { voice, format ->
            if (voice != null) {
                locale = voice.locale
                voiceName = voice.name
                voiceOk = true
                info("use speaker: $voiceName - $locale")
            }
            if (format.isNotEmpty()) {
                outputFormat = format
                sampleRate = if (format.contains("opus")) {
                    48000 // audio/opus in MediaCodec output sample rate change to 48khz.
                } else {
                    24000
                }
                formatOk = true
                info("use audio output format: $outputFormat")
            }
            0
        }.collect {
            if (voiceOk && formatOk) {
                prepared = true
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

    /**
     * Null represents the end of the text.
     * Close represents error occurred.
     */
    private val synthesisChannel = Channel<ByteArray?>()

    private fun collectAudioFromEngine() = scope.launch {
        var endOfText = false
        engine.output().onEach { frame ->
            if (frame.audioCompleted) {
                return@onEach
            }
            if (frame.textCompleted) {
                endOfText = true
                return@onEach
            }
        }.transform { frame ->
            if (frame.audioCompleted) {
                emit(Codec.Frame(null, endOfFrame = true))
                return@transform
            }
            if (frame.textCompleted) {
                return@transform
            }
            emit(Codec.Frame(frame.data))
        }.decode().catch {
            synthesisChannel.close()
            error("decode error: $it")
        }.onEach { frame ->
            if (frame.endOfFrame && endOfText) {
                synthesisChannel.send(null) // text is ok.
            }
        }.collect {
            it.data?.let {
                synthesisChannel.send(it)
            }
        }
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null || !prepared) {
            return
        }

        val text = request.charSequenceText.toString()
        val pitch = request.pitch - 100
        val rate = request.speechRate - 100

        info("start synthesizing text: $text")

        runBlocking {
            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)

            // 1. input text
            val metadata = TTS.AudioMetaData(
                locale = locale!!,
                voiceName = voiceName!!,
                volume = "+0%",
                outputFormat = outputFormat!!,
                pitch = "${pitch}Hz",
                rate = "${rate}%",
            )
            engine.input(text, metadata)

            // 2. wait audio data
            val bufSize = callback.maxBufferSize
            var idx = 0
            val bytes = ByteArray(bufSize)
            while (true) {
                val result = synthesisChannel.receiveCatching()
                if (result.isClosed) {
                    callback.error()
                    return@runBlocking
                }

                result.getOrNull().let { data ->
                    if (data == null) {
                        if (idx > 0) {
                            callback.audioAvailable(bytes, 0, idx)
                        }
                        callback.done()
                        return@runBlocking
                    }
                    for (b in data) {
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
        }
    }

    private fun Flow<Codec.Frame>.decode(): Flow<Codec.Frame> = Codec(this).run(scope.coroutineContext)

    private fun debug(message: String) {
        logRepository.debug(LOG_NAME, message)
        Log.d(LOG_NAME, message)
    }

    private fun info(message: String) {
        logRepository.info(LOG_NAME, message)
        Log.i(LOG_NAME, message)
    }

    private fun error(message: String) {
        logRepository.error(LOG_NAME, message)
        Log.e(LOG_NAME, message)
    }
}

