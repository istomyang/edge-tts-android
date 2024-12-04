package com.istomyang.edgetss.engine

import androidx.annotation.Keep
import com.google.gson.FieldNamingPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import io.ktor.serialization.gson.gson


public suspend fun listVoices(): Result<List<Voice>> {
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            gson() {
                setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
            }
        }
    }
    val res = client.get(urlString = buildUrl()) {
        header("sec-ch-ua-platform", "macOS")
        header(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
        )
        header(
            "sec-ch-ua",
            "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\""
        )
        header("sec-ch-ua-mobile", "?0")
        header("Accept", "*/*")
        header("X-Edge-Shopping-Flag", "1")
        header("Sec-MS-GEC", genSecMsGec())
        header("Sec-MS-GEC-Version", "1-131.0.2903.70")
        header("Sec-Fetch-Site", "none")
        header("Sec-Fetch-Mode", "cors")
        header("Sec-Fetch-Dest", "empty")
        header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
        header("Accept-Encoding", "gzip, deflate, br, zstd")
    }

    if (!res.status.isSuccess()) {
        return Result.failure(Throwable(res.status.description))
    }

    client.close()

    return Result.success(res.body())
}


private fun buildUrl(): String {
    return "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Sec-MS-GEC=${genSecMsGec()}&Sec-MS-GEC-Version=1-131.0.2903.70"
}

//{
//    "Name": "Microsoft Server Speech Text to Speech Voice (en-US, AvaMultilingualNeural)",
//    "ShortName": "en-US-AvaMultilingualNeural",
//    "Gender": "Female",
//    "Locale": "en-US",
//    "SuggestedCodec": "audio-24khz-48kbitrate-mono-mp3",
//    "FriendlyName": "Microsoft AvaMultilingual Online (Natural) - English (United States)",
//    "Status": "GA",
//    "VoiceTag": {
//    "ContentCategories": ["Conversation", "Copilot"],
//    "VoicePersonalities": ["Expressive", "Caring", "Pleasant", "Friendly"]
//    }
//}

@Keep
data class Voice(
    val name: String,
    val shortName: String,
    val gender: String,
    val locale: String,
    val suggestedCodec: String,
    val friendlyName: String,
    val status: String,
    val voiceTag: VoiceTag
)

@Keep
data class VoiceTag(val contentCategories: List<String>, val voicePersonalities: List<String>)