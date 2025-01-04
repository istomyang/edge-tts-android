package com.istomyang.edgetss.utils

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

suspend fun intoPcm(mp3Data: ByteArray): ByteArray {
    val pcmData = ByteArrayOutputStream()

    val extractor = MediaExtractor()
    extractor.setDataSource(ByteArrayDataSource(mp3Data))

    var audioTrackIndex = -1
    val numTracks = extractor.trackCount
    for (i in 0 until numTracks) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (mime!!.startsWith("audio/")) {
            audioTrackIndex = i
            break
        }
    }

    if (audioTrackIndex == -1) {
        throw Throwable("No audio track found in the MP3 file.")
    }

    extractor.selectTrack(audioTrackIndex)

    val format = extractor.getTrackFormat(audioTrackIndex)
    val mimeType = format.getString(MediaFormat.KEY_MIME)
    val codec = MediaCodec.createDecoderByType(mimeType!!)
    codec.configure(format, null, null, 0)
    codec.start()

    val info = MediaCodec.BufferInfo()
    var inputEOF = false
    var outputWait = 0 // wait codec handle all data.

    while (true) {
        val inputIndex = codec.dequeueInputBuffer(10_000)
        if (inputIndex >= 0 && !inputEOF) {
            val buffer = codec.getInputBuffer(inputIndex)
            buffer?.clear()
            val sampleSize = extractor.readSampleData(buffer!!, 0)
            if (sampleSize < 0) {
                inputEOF = true
                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
        }

        val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
        if (outputIndex >= 0) {
            val output = codec.getOutputBuffer(outputIndex)
            output?.let {
                val dest = ByteArray(info.size)
                it.get(dest)
                pcmData.write(dest)
            }
            codec.releaseOutputBuffer(outputIndex, false)
        } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputEOF) {
            if (outputWait < 2) { // maybe codec is running.
                outputWait += 1
                delay(100)
                continue
            }
            break
        }
    }

    codec.stop()
    codec.release()
    extractor.release()

    return pcmData.toByteArray()
}

private class ByteArrayDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun close() {}

    override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
        if (position < 0 || position >= data.size) {
            return -1
        }
        val remaining = data.size - position.toInt()
        val bytesToRead = minOf(remaining, size)
        if (buffer != null) {
            System.arraycopy(data, position.toInt(), buffer, offset, bytesToRead)
        }
        return bytesToRead
    }

    override fun getSize(): Long = this.data.size.toLong()
}