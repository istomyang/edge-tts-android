package com.istomyang.edgetss

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun bytes() {
        val a = (5 / 2).toInt()
        println(a)
    }

    @Test
    fun channel() {
        val ch = Channel<Int>(32)
        runBlocking {
            ch.send(1)
            ch.send(2)
            ch.send(3)
            ch.send(4)
            ch.send(5)
            ch.send(6)
            ch.close()
        }
        runBlocking {
            var count = 0
            for (i in ch) {
                count += 1
                println(i)
            }
            println(count)
            for (i in ch) {
                count += 1
                println(i)
            }
            println(count)
        }
    }

    @Test
    fun test_flow() {
        runBlocking {
            val f = emit().onStart {
                println("start")
            }.onEach {
                println("$it")
            }.onCompletion {
                println("completed")
            }

            squared(f).onStart {
                println("start2")
            }.onEach {
                println("$it")
            }.onCompletion {
                println("completed2")
            }.collect { it ->
                println(it)
            }
        }
    }

    @Test
    fun test_flow2() {
        runBlocking {
            emit().onStart {
                println("start")
            }.onEach {
                println("$it")
            }.onCompletion {
                println("completed")
            }.squared.onStart {
                println("start2")
            }.onEach {
                println("$it")
            }.onCompletion {
                println("completed2")
            }.collect { it ->
                println(it)
            }
        }
    }

    private fun emit() = flow {
        delay(3000)
        for (i in 1..10) {
            delay(100)
            emit(i)
        }
    }

    val Flow<Int>.squared get() = squared(this)

    private fun squared(src: Flow<Int>) = flow {
        src.collect {
            emit(it * 2)
        }
    }
}
