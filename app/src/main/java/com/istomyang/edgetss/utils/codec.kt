package com.istomyang.edgetss.utils

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat

fun mp3ToPcm(mp3Data: ByteArray): ByteArray {
    val pcmData = mutableListOf<Byte>()

    val extractor = MediaExtractor()
    extractor.setDataSource(DataSource(mp3Data))

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
        throw RuntimeException("No audio track found in the MP3 file.")
    }

    val format = extractor.getTrackFormat(audioTrackIndex)
    val mimeType = format.getString(MediaFormat.KEY_MIME)
    val codec = MediaCodec.createDecoderByType(mimeType!!)
    codec.configure(format, null, null, 0)
    codec.start()

    extractor.selectTrack(audioTrackIndex)

    // Decode the MP3 data to PCM
    while (true) {
        val inputIndex = codec.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            val buffer = codec.getInputBuffer(inputIndex)
            buffer?.clear()
            val sampleSize = extractor.readSampleData(buffer!!, 0)
            if (sampleSize < 0) {
                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            } else {
                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
        }

        val bufferInfo = MediaCodec.BufferInfo()
        val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        if (outputIndex >= 0) {
            val output = codec.getOutputBuffer(outputIndex)
            output?.let {
                val pcmByteArray = ByteArray(bufferInfo.size)
                it.get(pcmByteArray)
                pcmData.addAll(pcmByteArray.toList())
            }
            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    codec.stop()
    codec.release()

    return pcmData.toByteArray()
}

private class DataSource(private val data: ByteArray) : MediaDataSource() {
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