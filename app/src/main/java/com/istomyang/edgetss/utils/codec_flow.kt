package com.istomyang.edgetss.utils

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

fun intoPcmFlow(src: Flow<ByteArray>): Flow<ByteArray> = flow {
    val extractor = MediaExtractor()

    var srcLoaded: Boolean = false
    FlowDataSource(src.onCompletion {
        srcLoaded = true // tell trailing component leading component's data goes to eof.
    }).register(extractor)

    val trackIndex = getAudioTrackIndex(extractor)
    val format = extractor.getTrackFormat(trackIndex)
    val mimeType = format.getString(MediaFormat.KEY_MIME)

    extractor.selectTrack(trackIndex)
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
            val sampleSize = extractor.readSampleData(buffer!!, 0)
            if (sampleSize < 0) {
                if (!srcLoaded) {
                    delay(100)
                    continue
                }
                inputEOF = true // eof = sampleSize < 0 and srcLoaded.
                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                extractor.advance()
            }
        }

        val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
        if (outputIndex >= 0) {
            val output = codec.getOutputBuffer(outputIndex)
            if (output != null) {
                val dest = ByteArray(info.size)
                output.get(dest)
                emit(dest)
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
}

private fun getAudioTrackIndex(extractor: MediaExtractor): Int {
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME)
        if (mime!!.startsWith("audio/")) {
            return i
        }
    }
    throw IllegalArgumentException("No audio track found.")
}

private class FlowDataSource(private val data: Flow<ByteArray>) : MediaDataSource() {
    private var buffer0 = ByteBuffer2()
    private var dataOfEnd = false

    init {
        CoroutineScope(Dispatchers.IO).launch {
            data.onCompletion {
                dataOfEnd = true
            }.collect {
                buffer0.put(it)
            }
        }
    }

    suspend fun register(extractor: MediaExtractor) {
        while (buffer0.position() < 1024) {
            delay(100)
        }
        extractor.setDataSource(this)
    }

    override fun close() {}

    override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
        if (position < 0 || (dataOfEnd && position >= buffer0.position())) {
            return -1
        }
        val read = buffer0.copyTo(position, buffer!!, offset, size)
        return read
    }

    override fun getSize(): Long = -1
}

/**
 * ByteBuffer2 is better than List<ByteBuffer> as backing array.
 * See BufferUnitTest.kt
 */
private class ByteBuffer2() {
    private var cap = 1024 * 100 // 100KB
    private var buffer = ByteBuffer.allocate(cap)

    fun put(b: ByteArray) {
        // check grow
        val need = buffer.position() + b.size
        if (need >= buffer.capacity()) {
            grow(need)
        }
        buffer.put(b)
    }

    fun position() = buffer.position()

    fun copyTo(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position > position()) {
            return 0
        }
        val start = position.toInt()
        val read = minOf(size - offset, position() - start)
        val arr = this.buffer.array()
        System.arraycopy(arr, start, buffer, offset, read)
        return read
    }

    private fun grow(need: Int) {
        cap = if (cap * 2 > need) {
            cap * 2
        } else {
            need
        }
        val position = buffer.position()
        val oldArray = buffer.array()
        val newArray = ByteArray(cap)
        System.arraycopy(oldArray, 0, newArray, 0, position)
        buffer = ByteBuffer.wrap(newArray)
        buffer.position(position)
        println(buffer.position())
    }
}
