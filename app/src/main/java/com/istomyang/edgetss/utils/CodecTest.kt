package com.istomyang.edgetss.utils

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CodecTest(private val context: Context) {
    fun run() {
        runBlocking {
            val flow = mp3Flow().transform {
                if (it == null) {
                    emit(Codec.Frame(null, true))
                } else {
                    emit(Codec.Frame(it))
                }
            }

            var a: Channel<ByteArray>? = null
            val channel = Channel<Channel<ByteArray>>()

            launch {
                Codec(
                    flow,
                    context = context
                ).run(this.coroutineContext).collect { frame ->
                    if (a == null) {
                        a = Channel<ByteArray>()
                        channel.send(a!!)
                    }
                    if (frame.endOfFrame) {
                        a!!.close()
                        a = null
                    } else {
                        a!!.send(frame.data!!)
                    }
                }
                a?.close()
                channel.close()
            }

            for (job in channel) {
                val pcmPlayer = PcmPlayer(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                pcmPlayer.playPcm(job.consumeAsFlow())
            }

            println()
        }
    }

    private fun debug(msg: String) {
        Log.d("EdgeTTSService CodecTest", msg)
    }

    fun mp3Flow(): Flow<ByteArray?> = flow {
        var count = 0
        val byteArray = loadFile()
        val step = 1024 * 2
        debug("file size: ${byteArray.size}")
        while (count < 1) {
            var read = 0
            while (true) {
                val len = minOf(step, byteArray.size - read)
                if (len <= 0) {
                    break
                }
                emit(byteArray.copyOfRange(read, read + len))
                read += len
                debug("file remaining size: ${byteArray.size - read}")
                delay(100)
            }
            emit(null)
            count++
        }
    }

    private fun loadFile(): ByteArray {
        return ByteArray(1)
//        val inputStream: InputStream = context.resources.openRawResource(R.raw.text)
//        return inputStream.use { it.readBytes() }
    }

    class PcmPlayer(sampleRate: Int, channelConfig: Int, audioFormat: Int) {
        private val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        private val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
            AudioTrack.MODE_STREAM
        )

        fun playPcm(flow: Flow<ByteArray>) {
            runBlocking {
                audioTrack.play()
                flow.collect { pcmData ->
                    audioTrack.write(pcmData, 0, pcmData.size)
                }
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }
}


