package com.istomyang.edgetss

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEmpty
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class FlowUnitTest {
    @Test
    fun exampleChannel() {
        runBlocking {
            try {
                flow1(coroutineContext + Dispatchers.IO).onCompletion {
                    println("onCompletion")
                }.onEmpty {
                    println("onEmpty")
                }.collect {
                    println(it)
                }
            } catch (e: Throwable) {
                println("@error: ${e.message}")
            }
        }
    }

    private fun flow1(context: CoroutineContext) = flow {
        val dataChannel = Channel<Int>()
        val resultChannel = Channel<Int>()

        CoroutineScope(context).launch {
            repeat(5) {
                dataChannel.send(it)
            }
            dataChannel.close()
        }

        CoroutineScope(context).launch {
            var error: Throwable? = null
            try {
                for (num in dataChannel) {
                    delay(500)
                    resultChannel.send(num)
                }
                throw Exception("error")
            } catch (e: Throwable) {
                error = e
            } finally {
                resultChannel.close(error)
            }
        }

        while (true) {
            val result = resultChannel.receiveCatching()
            when {
                result.isSuccess -> {
                    emit(result.getOrThrow())
                }

                result.isClosed -> {
                    result.exceptionOrNull()?.let {
                        throw it
                    }
                    break
                }
            }
        }

        println("flow completed")
    }
}
