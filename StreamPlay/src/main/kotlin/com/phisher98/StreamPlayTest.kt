package com.Phisher98


import com.Phisher98.StreamPlayExtractor.invokeAnimes
import com.Phisher98.StreamPlayExtractor.invokeFlicky
import com.Phisher98.StreamPlayExtractor.invokeSuperstream
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class StreamPlayTest : StreamPlay() {
    override var name = "StreamPlay-Test"
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val res = AppUtils.parseJson<LinkData>(data)
        argamap(
            {
                invokeSuperstream(
                    res.imdbId,
                    res.season,
                    res.episode,
                    callback
                )
            }
        )
        return true
    }

}