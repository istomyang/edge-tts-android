package com.istomyang.tts_engine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ChannelUnitTest {
    @Test
    fun example() {
        runBlocking {
            val channel = Channel<Int>(8)
            val close = Channel<Unit>()

            launch {
                repeat(8) {
                    channel.send(it)
                }
                channel.close(Exception("123"))
                close.send(Unit)
            }

            close.receive()

            try {
                while (true) {
                    val a = channel.receiveCatching()
                    if (a.isClosed) {
                        a.exceptionOrNull()?.let {
                            throw it
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            println("Done")
        }
    }
}