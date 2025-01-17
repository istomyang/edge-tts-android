package com.istomyang.tts_engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.ByteArrayOutputStream
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
class ExampleUnitTest {
    @Test
    fun example() {
        runBlocking {
            SpeakerManager().list().onSuccess {
                println(it)
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    @Test
    fun runTTS() {
        runBlocking {
            val engine = TTS()

            val metadata = TTS.AudioMetaData(
                locale = "en-US",
                voiceName = "Microsoft Server Speech Text to Speech Voice (en-US, JennyNeural)",
                volume = "+0%",
                outputFormat = "audio-24khz-48kbitrate-mono-mp3",
                pitch = "+0Hz",
                rate = "+25%",
            )

            launch {
                try {
                    engine.run()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            launch {
                val stream = ByteArrayOutputStream()
                var count = 1
                engine.output().collect {
                    if (it.audioCompleted) {
                        val name = "test$count.mp3"
                        writeFile(name, stream.toByteArray())
                        println("write to file: $name")
                        count++
                        stream.reset()
                        return@collect
                    }
                    if (it.textCompleted) {
                        println("text is ok.")
                        return@collect
                    }
                    stream.write(it.data)
                    println("write to stream: ${it.data!!.size}")
                }
            }

            inputText(engine, metadata)
//            inputTextDelay(engine, metadata)
//            inputLongText(engine, metadata)

            delay(10_000)
            engine.close()
            println("close engine")
        }
    }

    @Test
    fun example2() {
        val s = datetime2String()
        println(s)
    }

    private suspend fun inputText(engine: TTS, md: TTS.AudioMetaData) {
        val txt = "This was a big problem for candidate Trump. It was also a big problem for me."
        engine.input("1$txt", md)
    }

    private suspend fun inputTextDelay(engine: TTS, md: TTS.AudioMetaData) {
        val txt = "This was a big problem for candidate Trump. It was also a big problem for me."
        delay(10_000)
        engine.input("1$txt", md)
        delay(3_000)
        engine.input("2$txt", md)
    }

    private suspend fun inputLongText(engine: TTS, md: TTS.AudioMetaData) {
        val txt = "This was a big problem for candidate Trump. It was also a big problem for me."
        val builder = StringBuilder()
        for (i in 1..1000) {
            builder.append("$i$txt")
        }
        engine.input(builder.toString(), md)
    }

    private fun datetime2String(): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val format =
            DateTimeFormatter.ofPattern(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000' '(Coordinated Universal Time)'",
                Locale.ENGLISH
            )
        return now.format(format)
    }

    private fun writeFile(name: String, data: ByteArray) {
        FileOutputStream(name).use { fos ->
            fos.write(data)
        }
    }
}
