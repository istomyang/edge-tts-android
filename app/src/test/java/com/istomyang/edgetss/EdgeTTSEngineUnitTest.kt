package com.istomyang.edgetss

import com.istomyang.edgetss.engine.listVoices
import com.istomyang.edgetss.engine.request
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class EdgeTTSEngineUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun run_request() {
        runBlocking {
            val t0 = System.currentTimeMillis()
            var size = 0

            try {
                request(
                    "en-US",
                    "Microsoft Server Speech Text to Speech Voice (en-US, JennyNeural)",
                    "+0Hz",
                    "+25%",
                    "+0%",
                    "audio-24khz-48kbitrate-mono-mp3",
                    "In February of 2016 I began to experience two separate realities at the same time.",
                ).onStart {
                    val t = System.currentTimeMillis() - t0
                    print(t)
                }.onEach {
                    size += 1
                }.onCompletion {
                    val sizeKB = size.toDouble() / 1024.0
                    print("[$sizeKB KB]")
                }.collect {

                }
            } catch (e: Exception) {
                print("[$e]")
            }
        }
    }

    @Test
    fun datetime() {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val timezone = ZoneOffset.of("+08:00")
        val datetime = now.withZoneSameInstant(timezone)
        val format =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'XXX (z)", Locale.ENGLISH)
        println(datetime.format(format))
    }

    @Test
    fun run_list_voice() {
        runBlocking {
            listVoices().onSuccess {
                println(it)
            }.onFailure {
                println(it.toString())
            }
        }
    }


}
