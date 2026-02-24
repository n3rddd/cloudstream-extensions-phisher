package com.Desicinemas

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class Tvlogyflow(val source: String) : ExtractorApi() {
    override val mainUrl = "https://flow.tvlogy.to"
    override val name = "Tvlogy"
    override val requiresReferer = false

    private val workerUrl = "https://twilight-pine-eba9.phisher1.workers.dev/?url="

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val workerResponse = app.get("$workerUrl$url").text.trim()

            if (workerResponse.isNotEmpty() &&
                workerResponse.contains(".m3u8") &&
                workerResponse.startsWith("http")
            ) {
                Log.d("Tvlogy", "Using Worker URL: $workerResponse")
                val headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                callback(
                    newExtractorLink(
                        "$name $source",
                        name,
                        workerResponse,
                        type = INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                        this.headers = headers
                    }
                )
                return
            }
        } catch (_: Exception) {
            Log.e("Tvlogy", "Worker failed, falling back")
        }

        val doc = app.get(
            "https://proxy.phisher2.workers.dev/?url=$url",
            referer = mainUrl
        ).text

        if (doc.contains(".m3u8")) {
            Regex("\"src\":\"(.*?)\",\"")
                .find(doc)
                ?.groupValues
                ?.get(1)
                ?.let {
                    callback(
                        newExtractorLink(
                            "$name $source",
                            name,
                            it,
                            type = INFER_TYPE
                        ) {
                            this.referer = url
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
        } else {
            val encoded = doc.substringAfter("JuicyCodes.Run(\"")
                .substringBefore("\");")
                .replace("\"", "")
                .replace("+", "")
                .replace("\\s".toRegex(), "")

            val script = base64Decode(encoded)

            val unpacked = JsUnpacker(script)
                .unpack()
                .toString()

            val matches = Regex("file\":\\s*\"(.*?)\"")
                .findAll(unpacked)

            matches.forEach { match ->
                val matched = match.groupValues[1]

                Log.d("TvlogyFallback", matched)

                when {
                    matched.endsWith(".m3u8") -> {
                        generateM3u8(
                            name,
                            matched,
                            mainUrl
                        ).forEach(callback)
                    }

                    matched.endsWith(".vtt") -> {
                        subtitleCallback(
                            newSubtitleFile(
                                "Subtitles",
                                matched
                            )
                        )
                    }
                }
            }
        }
    }
}



class Tvlogy(private val source:String) : ExtractorApi() {
    override val mainUrl = "https://tvlogy.to"
    override val name = "Tvlogy"
    override val requiresReferer = true


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val id = url.substringAfter("data=")
        val data = mapOf(
            "hash" to id,
            "r" to "http%3A%2F%2Ftellygossips.net%2F"
        )
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val meta = app.post("$url&do=getVideo", headers = headers, referer = referer, data = data)
            .parsedSafe<MetaData>() ?: return

        callback(
            newExtractorLink(
                "$name $source",
                name,
                url = meta.videoSource,
                ExtractorLinkType.M3U8
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }

    data class MetaData(
        val hls: Boolean,
        val videoSource: String
    )

}