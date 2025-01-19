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
                channel.close()
                close.send(Unit)
            }

            close.receive()

            for (num in channel) {
                println(num)
            }

            println("Done")
        }
    }
}