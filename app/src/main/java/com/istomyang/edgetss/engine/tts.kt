package com.istomyang.edgetss.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale


/**
 * Request a text to Microsoft using Edge browser's speech.
 */
suspend fun request(
    lang: String,
    voice: String,
    pitch: String,
    rate: String,
    volume: String,
    outputFormat: String,
    text: String
): Result<ByteArray> {
    // You can use a packet grabbing tool, like Charles,
    // to grab how the Edge browser handles audio read aloud
    // and you'll get the flow of operations.

    // The reason for Microsoft using websocket is that
    // the audio data is sent in a continuous stream.
    val audio = ByteArrayOutputStream()
    var err: Throwable? = null

    buildConnection {
        send(request1(outputFormat))
        send(request2(lang, voice, pitch, rate, volume, text))

        while (true) {
            when (val message = incoming.receive()) {
                is Frame.Text -> {
                    val data = message.readText()
                    if (data.contains("Path:turn.end")) {
                        return@buildConnection
                    }
                }

                is Frame.Binary -> {
                    val data = message.readBytes()
                    // metadata data length
                    val len = ByteBuffer.wrap(data.sliceArray(0..1)).short

                    // Because AI generates Audio Token continuously,
                    // the client needs to accept a block of data continuously.
                    audio.write(data.sliceArray(len + 2 until data.size))
                }

                else -> {
                    err = Throwable("Unexpected behavior: $message")
                    return@buildConnection
                }
            }

        }
    }

    if (err != null) {
        return Result.failure(err!!)
    }
    return Result.success(audio.toByteArray())
}


private suspend fun buildConnection(run: suspend DefaultClientWebSocketSession.() -> Unit) {
    val client = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
    }

    client.webSocket(request = {
        url(buildWsUrl())

        header("Pragma", "no-cache")
        header("Cache-Control", "no-cache")
        header(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
        )
        header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
        header("Accept-Encoding", "gzip, deflate, br, zstd")
        header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
    }, block = run)

    client.close()
}


private fun request1(outputFormat: String): String {
    val builder = StringBuilder()
    builder.append("X-Timestamp:${datetime2String()}\n")
    builder.append("Content-Type:application/json; charset=utf-8\r\n")
    builder.append("Path:speech.config\r\n\r\n")
    val a =
        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"{1}"}}}}
        """.trimMargin().replace("{1}", outputFormat)
    builder.append(a)
    return builder.toString()
}

private fun request2(
    lang: String,
    voice: String,
    pitch: String,
    rate: String,
    volume: String,
    text: String
): String {
    val builder = StringBuilder()
    builder.append("X-RequestId:${newUUID()}\r\n")
    builder.append("Content-Type:application/ssml+xml\r\n")
    builder.append(String.format("X-Timestamp:%s\r\n", datetime2String()))
    builder.append("Path:ssml\r\n\r\n")
    val a = """
            <?xml version="1.0" encoding="UTF-8"?>
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis"  xml:lang="{1}">
                <voice name="{2}">
                    <prosody pitch="{3}" rate="{4}" volume="{5}">{6}</prosody>
                </voice>
            </speak>
        """.trimIndent().replace("{1}", lang).replace("{2}", voice).replace("{3}", pitch)
        .replace("{4}", rate).replace("{5}", volume).replace("{6}", text)
    builder.append(a)
    return builder.toString()
}

private fun buildWsUrl(): String {
    return String.format(
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Sec-MS-GEC=%s&Sec-MS-GEC-Version=1-131.0.2903.51&ConnectionId=%s",
        genSecMsGec(),
        newUUID()
    )
}

/**
 * datetime2String create like this:
 * "Fri May 16 2021 00:00:00 GMT+0800 (China Standard Time)"
 */
private fun datetime2String(): String {
    val now = ZonedDateTime.now(ZoneOffset.UTC)
    val timezone = ZoneOffset.of("+08:00")
    val datetime = now.withZoneSameInstant(timezone)
    val format =
        DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'XXX (z)", Locale.ENGLISH)
    return datetime.format(format)
}


private fun newUUID(): String {
    return java.util.UUID.randomUUID().toString().replace("-", "")
}
