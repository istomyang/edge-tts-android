package com.istomyang.edgetss.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

private fun debug(msg: String) {
    Log.d("EdgeTTSService Codec", msg)
}

class Codec(private val source: Flow<Frame>, private val context: Context) {

    class Frame(val data: ByteArray?, val endOfFrame: Boolean = false)

    fun run(context: CoroutineContext): Flow<Frame> = flow {
        val dataChannel = Channel<Flow<ByteArray>>()
        val resultChannel = Channel<ByteArray?>() // null is eof of frame.

        var pkgSendCount = 0
        var pkgReceiveCount = 0
        var mut = Mutex()

        CoroutineScope(context).launch {
            var ch: Channel<ByteArray>? = null
            source.onCompletion {
                dataChannel.close() // wait decode.
            }.collect { frame ->
                if (frame.endOfFrame) {
                    mut.withLock {
                        debug("send pkg: $pkgSendCount")
                    }
                    ch?.close()
                    ch = null
                    return@collect
                }
                if (ch == null) {
                    ch = Channel(UNLIMITED)
                    dataChannel.send(ch!!.consumeAsFlow())
                }
                mut.withLock {
                    pkgSendCount++
                }
                ch!!.send(frame.data!!)
            }
        }

        CoroutineScope(context).launch {
            var error: Throwable? = null
            try {
                for (src in dataChannel) {
                    // src drain when frame is eof.
                    decode(src).onCompletion {
                        resultChannel.send(null)
                    }.collect {
                        mut.withLock {
                            pkgReceiveCount += 1
                        }
                        resultChannel.send(it)
                    }

                    mut.withLock {
                        debug("receive pkg: $pkgReceiveCount a: ${pkgReceiveCount / pkgSendCount}")
                        pkgReceiveCount = 0
                        pkgSendCount = 0
                    }
                }
            } catch (e: Throwable) {
                error = e
            } finally {
                resultChannel.close(error) // close
            }
        }

        while (true) {
            val result = resultChannel.receiveCatching()
            if (result.isClosed) {
                result.exceptionOrNull()?.let { throw it }
                break // end
            }

            val data = result.getOrNull()
            if (data == null) {
                emit(Frame(null, endOfFrame = true))
            } else {
                emit(Frame(data))
            }
        }
    }

    private fun decode(source: Flow<ByteArray>): Flow<ByteArray> = flow {
        val extractor = MediaExtractor()

        var endOfSource = false
        AudioDataSource(source.onCompletion {
            endOfSource = true
        }).register(extractor, 1024)

        val trackIndex = getAudioTrackIndex(extractor)
        val format = extractor.getTrackFormat(trackIndex)
        val mimeType = format.getString(MediaFormat.KEY_MIME)

        extractor.selectTrack(trackIndex)
        val codec = MediaCodec.createDecoderByType(mimeType!!)
        codec.configure(format, null, null, 0)
        codec.start()

        while (true) {
            val inputIndex = codec.dequeueInputBuffer(100_000) // wait 100ms is enough.
            if (inputIndex >= 0) {
                val buffer = codec.getInputBuffer(inputIndex)
                when (val sampleSize = extractor.readSampleData(buffer!!, 0)) {
                    -1 -> {
                        if (endOfSource) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, 0)
                        }
                    }
                    else -> {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val info = MediaCodec.BufferInfo()
            val outputIndex = codec.dequeueOutputBuffer(info, 200_000)
            if (outputIndex >= 0) {
                val output = codec.getOutputBuffer(outputIndex)
                if (output != null) {
                    val dest = ByteArray(info.size)
                    output.get(dest)
                    emit(dest)
                }
                codec.releaseOutputBuffer(outputIndex, false)
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait 200ms is enough for codec to decode any pending data.
                if (endOfSource) {
                    break
                }
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

    private class AudioDataSource(private val data: Flow<ByteArray>) : MediaDataSource() {
        private var buffer0 = ByteBuffer2()
        private var dataOfEnd = false

        init {
            CoroutineScope(Dispatchers.IO).launch {
                data.onCompletion {
                    dataOfEnd = true
                }.collect { data ->
                    buffer0.put(data)
                }
            }
        }


        suspend fun register(extractor: MediaExtractor, loadSize: Int, errCount: Int = 0) {
            while (buffer0.position() < loadSize) {
                delay(100)
            }
            try {
                extractor.setDataSource(this)
            } catch (e: Exception) {
                if (errCount > 3) {
                    throw e
                }
                Log.e("FlowDataSource", "error: ${e.message ?: "register"} - $loadSize")
                register(extractor, loadSize = loadSize * 2, errCount = errCount + 1)
            }
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

        private fun ByteBuffer.copyTo(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position > position()) {
                return 0
            }
            val start = position.toInt()
            val read = minOf(size - offset, position() - start)
            val arr = this.array()
            System.arraycopy(arr, start, buffer, offset, read)
            return read
        }
    }

    /**
     * ByteBuffer2 is better than List<ByteBuffer> as backing array.
     * See BufferUnitTest.kt
     */
    private class ByteBuffer2 {
        private var cap = 1024 * 10 // 10KB
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
        }
    }
}
