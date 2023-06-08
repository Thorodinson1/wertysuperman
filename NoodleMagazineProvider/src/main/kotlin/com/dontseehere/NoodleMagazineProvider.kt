package com.dontseehere

import android.util.Log

import com.fasterxml.jackson.annotation.JsonProperty

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element

class NoodleMagazineProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://noodlemagazine.com"
    override var name = "Noodle Magazine"

    override val hasMainPage = true

    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.NSFW
    )


    //https://noodlemagazine.com/video/latest?p=0
    override val mainPage = mainPageOf(
        "latest" to "Latest",
        "onlyfans" to "Onlyfans",
        "latina" to "Latina",
        "blonde" to "Blonde",
        "milf" to "MILF",
        "jav" to "JAV",
        "hentai" to "Hentai",
        "lesbian" to "Lesbian",
        )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var curpage = page - 1
        val link = "$mainUrl/video/${request.data}?p=$curpage"
        //Log.d("TAG","$link")
        val document = app.get(link).document
        val home = document.select("div.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }



    private fun Element.toSearchResult(): AnimeSearchResponse? {
        //val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        var title = this.selectFirst("a div.i_info div.title")?.text() ?: return null
        //Remove russian letters
        title = Regex("[^A-Za-z0-9\\s]+").replace(title, "")
        val posterUrl = fixUrlNull(this.selectFirst("a div.i_img img")?.attr("data-src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<AnimeSearchResponse> {

        var searchresult = mutableListOf<AnimeSearchResponse>()
        
        listOf(0,1,2,3,4).apmap{
            page ->
            val doc = app.get("$mainUrl/video/$query?p=$page").document
            //return document.select("div.post-filter-image").mapNotNull {
            doc.select("div.item").apmap{
                res ->
                searchresult.add( res.toSearchResult()!! )
            }
            
        }

        return searchresult
    }

    override suspend fun load(url: String): LoadResponse? {
        
        val document = app.get(url).document

        var title = document.selectFirst("div.l_info h1")?.text()?.trim()  ?: "null"
        //Remove russian letters
        title = Regex("[^A-Za-z0-9\\s]+").replace(title, "")
        val poster  = document.selectFirst("""meta[property="og:image"]""")?.attr("content") ?: "null"

        var reqlink = document.selectFirst("""meta[property="og:video"]""")?.attr("content") ?: "null"

        reqlink = reqlink.replace("https://nmcorp.video/player/", "https://noodlemagazine.com/playlist/")

        //Log.d("TAG", "reqlink $reqlink")

        val recommendations =
            document.select("div.item").mapNotNull {
                it.toSearchResult()
            }

        return newMovieLoadResponse(title, reqlink, TvType.NSFW, reqlink) {
                this.posterUrl = poster
                this.recommendations = recommendations
            }
        
    }




    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        //Log.d("TAG","data $data")

        val jason = app.get(data).parsed<SusJSON>()

        //Log.d("TAG","jason $jason")

        jason?.sources?.mapNotNull {

            //Log.d("TAG","it ${it?.streamlink}")

            val name = "Noodle Magazine ${it?.qualityfile} p"
            callback.invoke(
                                ExtractorLink(
                                    source = name,
                                    name = "Noodle Magazine ${it?.qualityfile} p",
                                    url = it?.streamlink!!,
                                    referer = "$mainUrl/",
                                    quality = Qualities.P360.value
                                )
                            )
            
            }

        return true
    }

    data class SusJSON (
    @JsonProperty("image") val img:  String? = null,
    @JsonProperty("sources") val sources:  ArrayList<Streams> = arrayListOf()
)

    data class Streams (
    @JsonProperty("file") val streamlink:  String? = null,//the link
    @JsonProperty("label") val qualityfile:  String? = null,//720 480 360 240
    @JsonProperty("type") val type:  String? = null,//mp4
)

}

