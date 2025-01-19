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
import io.ktor.util.moveToByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

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
        runBlocking {
            engine.close()
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onStop() {
    }

    private suspend fun collectConfig() {
        speakerRepository.getActiveFlow().collect { voice ->
            if (voice != null) {
                locale = voice.locale
                voiceName = voice.name
                outputFormat = voice.suggestedCodec
                prepared = true
                info("use speaker: $voiceName - $locale")
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
                debug("collect audio frame | audioCompleted")
                return@onEach
            }
            if (frame.textCompleted) {
                endOfText = true
                debug("collect audio frame | textCompleted")
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
            debug("collect audio frame | error: $it")
        }.onEach { frame ->
            if (frame.endOfFrame && endOfText) {
                debug("collect audio frame | decode endOfText.")
                synthesisChannel.send(null) // text is ok.
            }
        }.collect { frame ->
            frame.data?.let {
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

        debug("start synthesizing text: ${text.description}")

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
            try {
                engine.input(text, metadata)
            } catch (e: Throwable) {
                callback.error()
                error("synthesize text error: $e")
                return@runBlocking
            }

            // 2. wait audio data
            val buffer = ByteBuffer.allocate(callback.maxBufferSize)

            for (data in synthesisChannel) {
                if (data == null) {
                    if (buffer.position() > 0) {
                        buffer.flip()
                        val a = buffer.moveToByteArray()
                        callback.audioAvailable(a, 0, a.size)
                    }
                    debug("${text.description} | submit audio chunk to end.")
                    callback.done()
                    return@runBlocking
                }
                if (data.size > buffer.remaining()) {
                    val len = minOf(buffer.remaining(), data.size)
                    buffer.put(data, 0, len)
                    buffer.flip()
                    val a = buffer.moveToByteArray()
                    callback.audioAvailable(a, 0, a.size)
                    buffer.clear()

                    buffer.put(data, len, data.size - len)
                    continue
                }
                buffer.put(data)
            }

            debug("${text.description} | error and exit this text.")
            callback.error()
        }
    }

    private fun Flow<Codec.Frame>.decode(): Flow<Codec.Frame> = Codec(this).run(scope.coroutineContext)

    private fun debug(message: String) {
        Log.d(LOG_NAME, message)
        scope.launch {
            logRepository.debug(LOG_NAME, message)
        }
    }

    private fun info(message: String) {
        logRepository.info(LOG_NAME, message)
        Log.i(LOG_NAME, message)
    }

    private fun error(message: String) {
        logRepository.error(LOG_NAME, message)
        Log.e(LOG_NAME, message)
    }

    private val String.description: String
        get() {
            val size = this.length
            return if (size > 10) {
                "${this.substring(0, 10)}..."
            } else {
                this
            }
        }
}

