package com.istomyang.tts_engine

import io.ktor.util.decodeString
import io.ktor.util.moveToByteArray
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Test
import java.nio.ByteBuffer

class SomeUnitTest {
    @Test
    fun example() {
        runBlocking {
            println("hello")
            supervisorScope {
                val job1 = launch {
                    println("@@@ test1")
                    throw Exception("test1")
                }
                val job2 = launch {
                    println("@@@ test2")
                    throw Exception("test2")
                }
                val job3 = launch {
                    println("@@@ test3")
                    throw Exception("test3")
                }

                try {
                    joinAll(job1, job2, job3)
                } catch (e: Exception) {
                    println("error1: ${e.message}")
                }
            }
        }
    }

    @Test
    fun example2() {
        val text = "123abc.。你好👋".removeEmojis() // 11 char
        val chunkSize = 10
        val chunks = mutableListOf<String>()

        val buffer = ByteBuffer.allocate(chunkSize)
        for (char in text) {
            val bytes = char.toString().toByteArray(Charsets.UTF_8)
            if (buffer.remaining() < bytes.size) {
                buffer.flip()
                chunks.add(buffer.decodeString(Charsets.UTF_8))
                buffer.clear()
            }
            buffer.put(bytes)
        }
        buffer.flip()
        chunks.add(buffer.decodeString(Charsets.UTF_8))

        println(chunks)
    }

    private fun String.removeEmojis(): String {
        return this.replace("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+".toRegex(), "")
            .replace("[\\u2600-\\u27BF]+".toRegex(), "")
            .replace("[\\uD83D-\\uDDFF]+".toRegex(), "")
            .replace("[\\uD83E-\\uDDFF]+".toRegex(), "")
    }

    @Test
    fun example3() {
        val buffer = ByteBuffer.allocate(10)
        buffer.put(0)
        println(buffer.position())
        println(buffer.remaining())
        println(buffer.limit())

        buffer.flip()
        val a = buffer.moveToByteArray()
        println(a)
        println(buffer.position())
    }
}