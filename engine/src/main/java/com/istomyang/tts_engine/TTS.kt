package com.istomyang.tts_engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.String

class TTS {
    private val resultChannel = Channel<AudioFrame>(8)
    private val chunkChannel = Channel<String?>(8)

    // Can reuse ws connection.
    private var client: HttpClient? = null
    private var session: DefaultClientWebSocketSession? = null

    /**
     * At first, I used Frame to save metadata and data, then I discovered that input must wait previous output done.
     */
    private var currentMetadata: AudioMetaData? = null

    private var errCount = 0

    suspend fun run() {
        try {
            client = HttpClient(CIO) {
                install(WebSockets) {
                    pingIntervalMillis = 20_000L
                }
            }

            session = client!!.webSocketSession {
                makeHttpRequestBuilder(this)
            }

            for (chunk in chunkChannel) {
                if (chunk == null) {
                    resultChannel.send(AudioFrame(null, textCompleted = true))
                    continue
                }
                val md = currentMetadata!!
                val ssml = buildSSML(chunk, metadata = md)
                val speech = buildSpeechConfig(md.outputFormat)
                communicate(speech, ssml) {
                    if (it == null) {
                        resultChannel.send(AudioFrame(null, audioCompleted = true))
                        return@communicate
                    }
                    resultChannel.send(AudioFrame(it))
                }
            }
        } catch (_: CancellationException) {
        } catch (e: Throwable) {
            if (e.message != null && e.message!!.contains("403") && errCount < 3) {
                delay(500L)
                errCount += 1
                resolveHttp403()
                run()
                return
            }
            throw e
        }
    }

    suspend fun close() {
        session?.close()
        client?.close()
    }

    suspend fun input(text: String, metadata: AudioMetaData) {
        if (metadata.invalid()) {
            throw Exception("Voice is invalid.")
        }
        currentMetadata = metadata

        val chunkSize = estimateTextLength(metadata)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        var count = 0
        while (true) {
            val size = minOf(chunkSize, textBytes.size - count)
            if (size > 0) {
                val chunk = textBytes.sliceArray(count until count + size)
                val text = String(chunk, Charsets.UTF_8)
                chunkChannel.send(text)
            } else {
                chunkChannel.send(null)
                break
            }
            count += size
        }
    }

    /**
     * Output audio data maybe has a every long text input and output more than one audio file data.
     * You should compare Audio Metadata in order to group audios by text.
     *
     * Null represents the end of the audio file.
     */
    fun output(): Flow<AudioFrame> = resultChannel.consumeAsFlow()

    private suspend fun resolveHttp403() {
        val builder = makeHttpRequestBuilder(HttpRequestBuilder(), useWs = false)
        val res = client!!.request(builder)
        val date = res.headers["Date"]
        if (date != null) {
            DRM.adjustClockSkew(date)
        }
    }

    private fun makeHttpRequestBuilder(builder: HttpRequestBuilder, useWs: Boolean = true): HttpRequestBuilder {
        return builder.apply {
            url(buildUrl(useWs))
            header("Pragma", "no-cache")
            header("Cache-Control", "no-cache")
            header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
            )
            header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            header("Accept-Encoding", "gzip, deflate, br")
            header("Accept-Language", "en-US,en;q=0.9")
        }
    }

    private suspend fun communicate(speech: String, ssml: String, onReceived: suspend (ByteArray?) -> Unit) {
        val session = session!!

        session.send(speech)
        session.send(ssml)

        while (true) {
            when (val message = session.incoming.receive()) {
                is Frame.Text -> {
                    val data = message.readText()
                    if (data.contains("Path:turn.end")) {
                        onReceived(null)
                        return
                    }
                }

                is Frame.Binary -> {
                    val data = message.readBytes()

                    // Because AI generates Audio Token continuously,
                    // the client needs to accept a block of data continuously.
                    // metadata data length is 2.
                    val audio = data.sliceArray(2 until data.size)
                    onReceived(audio)
                }

                is Frame.Close -> {
                    throw Exception("WebSocket Close: ${message.readReason()?.message}")
                }

                else -> {
                    throw Exception("Unexpected behavior: $message")
                }
            }
        }
    }

    private fun buildSpeechConfig(outputFormat: String): String {
        val timestamp = datetime2String()
        val contentType = "application/json; charset=utf-8"
        val path = "speech.config"
        return """
            X-Timestamp:$timestamp
            Content-Type:$contentType
            Path:$path
            
            {"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"$outputFormat"}}}}
        """.trimIndent().replace("\n", "\r\n")
    }

    private fun buildUrl(useWs: Boolean): String {
        return String.format(
            "%s://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Sec-MS-GEC=%s&Sec-MS-GEC-Version=1-131.0.2903.51&ConnectionId=%s",
            if (useWs) "wss" else "https",
            DRM.genSecMsGec(),
            newUUID()
        )
    }

    private fun estimateTextLength(metadata: AudioMetaData): Int {
        val lang = metadata.locale
        val voice = metadata.voiceName
        val pitch = metadata.pitch
        val rate = metadata.rate
        val volume = metadata.volume
        val tplSize = """
            X-RequestId:84dcbd2ef9591c28db9435451ea447c0
            Content-Type:application/ssml+xml
            X-Timestamp:Fri Jan 17 2025 13:22:44 GMT+0800 (中国标准时间)Z
            Path:ssml

            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="$lang"><voice name="$voice"><prosody pitch="$pitch" rate="$rate" volume="$volume"></prosody></voice></speak>
        """.trimIndent().replace("\n", "\r\n").toByteArray(Charsets.UTF_8).size
        val wsMsgSize = 2 shl 16
        return wsMsgSize - tplSize - 50 - 20 // 50 is safe margin of error
    }

    private fun buildSSML(
        text: String,
        metadata: AudioMetaData
    ): String {
        val lang = metadata.locale
        val voice = metadata.voiceName
        val pitch = metadata.pitch
        val rate = metadata.rate
        val volume = metadata.volume
        return return """
            X-RequestId:${newUUID()}
            Content-Type:application/ssml+xml
            X-Timestamp:${datetime2String()}
            Path:ssml
            
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="$lang"><voice name="$voice"><prosody pitch="$pitch" rate="$rate" volume="$volume">$text</prosody></voice></speak>
        """.trimIndent().replace("\n", "\r\n")
    }

    data class AudioFrame(
        val data: ByteArray?,
        val textCompleted: Boolean = false,
        val audioCompleted: Boolean = false
    )

    data class AudioMetaData(
        val locale: String,
        val voiceName: String,
        val volume: String,
        val outputFormat: String,
        val pitch: String,
        val rate: String,
    ) {
        internal fun invalid(): Boolean {
            return locale.isEmpty() || voiceName.isEmpty() || volume.isEmpty() || outputFormat.isEmpty() || pitch.isEmpty() || rate.isEmpty()
        }
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


    private fun String.escapeXml(): String {
        return this.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun newUUID(): String {
        return java.util.UUID.randomUUID().toString().replace("-", "")
    }
}