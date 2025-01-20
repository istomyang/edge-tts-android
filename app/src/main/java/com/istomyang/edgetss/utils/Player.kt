package com.istomyang.edgetss.utils

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class Player {
    class Frame(val data: ByteArray?, val endOfFrame: Boolean = false)

    private var player = AudioTrack.Builder()
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_MP3)
                .setSampleRate(24000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(
                24000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_MP3
            )
        )
        .build()

    private var a: Channel<ByteArray>? = null
    private var jobChannel = Channel<Channel<ByteArray>>()

    suspend fun run(flow: Flow<Frame>, onCompleted: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            flow.collect { frame ->
                if (frame.endOfFrame) {
                    a!!.close()
                    a = null
                    return@collect
                }
                if (a == null) {
                    a = Channel()
                    jobChannel.send(a!!)
                }
                a!!.send(frame.data!!)
            }
            jobChannel.close()
        }

        for (job in jobChannel) {
            for (data in job) {
                player.write(data, 0, data.size)
            }
            delay(1500) // sweet spot.
            onCompleted()
        }
        player.stop()
        player.release()
    }

    private var playing = false

    fun play() {
        if (playing) {
            return
        }
        playing = true
        player.play()
    }

    fun pause() {
        player.pause()
        player.flush()
        playing = false
    }
}