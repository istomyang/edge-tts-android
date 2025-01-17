package com.istomyang.tts_engine

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Test

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
        val a = 10
        var b = 15
        val c = b / a
        println(c)
    }
}