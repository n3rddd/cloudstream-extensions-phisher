package com.Animeowl

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Animeowl : MainAPI() {
    override var mainUrl              = "https://animeowl.live"
    override var name                 = "Animeowl"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime)

    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "genre/action" to "Action",
        "genre/adventure" to "Adventure",
        "type/movie" to "Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page=$page").document
        val home     = document.select("div.recent-anime a.post-thumb").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("img").attr("alt")
        val href      = this.attr("href")
        val posterUrl = fixUrlNull(this.select("img").attr("data-src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        // Construct the JSON body for the request
        val jsonBody = """{
        "clicked": false,
        "limit": 24,
        "page": 0,
        "pageCount": 1,
        "collectionId": null,
        "value": "$query",
        "sortt": 4,
        "lang22": 3,
        "selected": {
            "type": [],
            "genre": [],
            "year": [],
            "country": [],
            "season": [],
            "status": [],
            "sort": [],
            "language": []
        },
        "results": [],
        "label": "searching ...."
    }""".toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        // Perform the HTTP POST request
        val apiResponse = app.post("$mainUrl/api/advance-search", requestBody = jsonBody)
        val parsedResponse = tryParseJson<Searchresponse>(apiResponse.body.string())
        return parsedResponse?.results?.map { it.toSearchResponse() }
            ?: throw ErrorLoadingException("Invalid JSON response")
    }

    private fun Searchresponse.Result.toSearchResponse(): SearchResponse {
        val title = this.enName
        val poster = mainUrl+this.thumbnail// Convert raw image URL to full URL if needed
        val href = "$mainUrl/anime/${this.animeSlug}" // Construct the full URL using the slug

        return newAnimeSearchResponse(
            title,
            href,
            TvType.TvSeries
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("div.grid-main-left h1")?.text()?.trim().toString()
        val poster = mainUrl+document.select("div.cover-img-container img").attr("src")
        val description = document.selectFirst("div.anime-desc")?.text()?.trim()
        val genre=document.select("div.genre a").map { it.text() }
        val type=document.selectFirst("div.type.d-flex a")?.text().toString()
        val tvtag=if (type=="TV") TvType.TvSeries else TvType.Movie
        return if (tvtag == TvType.TvSeries) {
            val doc= app.get(url).document
            val subEpisodes=doc.select("#anime-cover-sub-content .episode-node").map { info->
                        val href = info.select("a").attr("href")
                        val episode = info.attr("title")
                        val epno=episode.toIntOrNull()
                        Episode(href, "Episode $episode",1,epno)
            }
            val dubEpisodes=doc.select("#anime-cover-dub-content .episode-node").map { info->
                val href = info.select("a").attr("href")
                val episode = info.attr("title")
                val epno=episode.toIntOrNull()
                Episode(href, "Episode $episode",1,epno)
            }
            newAnimeLoadResponse(title, url, TvType.Anime) {
                this.posterUrl = poster
                this.plot = description
                this.tags=genre
                addEpisodes(DubStatus.Subbed, subEpisodes)
                addEpisodes(DubStatus.Dubbed, dubEpisodes)
            }
        } else {
            val hrefs=document.select("a.episode-node").map { it.attr("href") }.toList()
            newMovieLoadResponse(title, url, TvType.Movie, hrefs) {
                this.posterUrl = poster
                this.plot = description
                this.tags=genre
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("Phisher",data)
        if (data.startsWith("["))
        {
            data.substringAfter("[\"").substringBefore("\"]").split("\",\"").map {
                loadExtractor(it,mainUrl,subtitleCallback, callback)
            }
        }
        else
        {
            loadExtractor(data,mainUrl,subtitleCallback, callback)
        }
        return true
    }

}
