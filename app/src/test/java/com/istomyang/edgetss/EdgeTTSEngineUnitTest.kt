package com.istomyang.edgetss

import android.util.Log
import com.istomyang.edgetss.engine.listVoices
import com.istomyang.edgetss.engine.request
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
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
            request(
                "en-US",
                "Microsoft Server Speech Text to Speech Voice (en-US, JennyNeural)",
                "+0Hz",
                "+25%",
                "+0%",
                "audio-24khz-48kbitrate-mono-mp3",
                "In February of 2016 I began to experience two separate realities at the same time.",
            ).onSuccess { data ->
                val file = File("text.mp3")
                val outputStream = FileOutputStream(file)
                outputStream.write(data)
                outputStream.close()
            }.onFailure { err ->
                Log.e("test", err.toString())
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
