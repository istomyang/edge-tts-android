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
import com.istomyang.edgetss.utils.Player
import com.istomyang.tts_engine.TTS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class EdgeTTSService : TextToSpeechService() {
    companion object {
        private const val LOG_NAME = "EdgeTTSService"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private lateinit var engine: TTS
    private lateinit var player: Player
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
        player = Player()

        scope.launch {
            launch { collectConfig() }

            launch { collectAudioFromEngine() }

            try {
                engine.run()
            } catch (_: CancellationException) {
            } catch (e: Throwable) {
                resultChannel.send(Result.failure(e)) // tell error occurs.
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
        player.pause()
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

    private val resultChannel = Channel<Result<Unit>>()

    private fun collectAudioFromEngine() = scope.launch {
        engine.output().transform { frame ->
            if (frame.audioCompleted) {
                emit(Player.Frame(null, endOfFrame = true))
                return@transform
            }
            if (frame.textCompleted) {
                return@transform
            }
            emit(Player.Frame(frame.data))
        }.play {
            resultChannel.send(Result.success(Unit))
        }
    }

    private suspend fun Flow<Player.Frame>.play(onCompleted: suspend () -> Unit) = player.run(this, onCompleted)

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

            player.play()

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

            // 2. wait result
            for (result in resultChannel) {
                when {
                    result.isSuccess -> {
                        callback.done()
                        break
                    }

                    result.isFailure -> {
                        callback.error()
                        break
                    }
                }
            }
        }
    }

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

