package com.istomyang.edgetss

import org.junit.Test
import java.nio.ByteBuffer

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class BufferUnitTest {
    @Test
    fun run() {
        val size = 1024 * 1024 * 64

        val t0 = System.currentTimeMillis()

        testByteBufferGroup(size)

        val t1 = System.currentTimeMillis()

        testByteBufferSystemCopy(size)

        val t2 = System.currentTimeMillis()

        println("test1 use: ${t1 - t0}ms")
        println("test2 use: ${t2 - t1}ms")

//        test1 use: 996ms
//        test2 use: 217ms
    }

    private fun testByteBufferGroup(size: Int) {
        val group = ByteBufferGroup()
        for (i in 0 until size) {
            group.put(i.toByte())
        }
        val dest = ByteArray(size)
        group.copyTo(0, dest, 0, size)
        println("complete: ${dest.size}")
    }

    private fun testByteBufferSystemCopy(size: Int) {
        val group = ByteBufferSystemCopy()
        for (i in 0 until size) {
            group.put(i.toByte())
        }
        val dest = ByteArray(size)
        group.copyTo(0, dest, 0, size)
        println("complete: ${dest.size}")
    }
}


private class ByteBufferGroup() {
    private val unitSize = 16
    private val buffers = mutableListOf<ByteBuffer>()

    var maxPosition = 0L

    init {
        grow()
    }

    fun put(b: Byte) {
        var buffer = buffers.last()
        if (buffer.position() >= buffer.capacity()) {
            grow()
            buffer = buffers.last()
        }
        buffer.put(b)
        maxPosition += 1
    }

    fun copyTo(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
        val start = position
        val end = minOf(position + size, maxPosition)
        for (i in start until end) {
            val b = get(i)
            val idx = (i - start + offset).toInt()
            buffer?.set(idx, b)
        }
        return (end - start).toInt()
    }

    private fun get(position: Long): Byte {
        val group = position / unitSize
        val buffer = buffers[group.toInt()]
        val idx = position % unitSize
        return buffer.get(idx.toInt())
    }

    private fun grow() {
        val buffer = ByteBuffer.allocate(unitSize)
        buffers.add(buffer)
    }
}


private class ByteBufferSystemCopy() {
    private var cap = 16
    private var buffer = ByteBuffer.allocate(cap)

    var maxPosition = 0

    fun put(b: Byte) {
        if (buffer.position() >= buffer.capacity()) {
            grow()
        }
        buffer.put(b)
        maxPosition += 1
    }

    fun copyTo(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        val start = position.toInt()
        val read = minOf(size, maxPosition - start)
        val arr = this.buffer.array()
        System.arraycopy(arr, start, buffer, offset, read)
        return read
    }

    private fun grow() {
        cap = cap * 2
        val position = buffer.position()
        val oldArray = buffer.array()
        val newArray = ByteArray(cap)
        System.arraycopy(oldArray, 0, newArray, 0, position)
        buffer = ByteBuffer.wrap(newArray)
        buffer.position(position)
    }
}