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

import org.jsoup.Jsoup
import java.util.regex.Pattern

class ToonoInProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://toono.in/"
    override var name = "Toono In"

    override val hasMainPage = true

    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "/" to "Main",
        )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val link = "$mainUrl${request.data}"
        val document = app.get(link).document
        val home = document.select("div.swiper-slide article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }



    private fun Element.toSearchResult(): AnimeSearchResponse? {
        //val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val href = this.selectFirst("a.lnk-blk")?.attr("href") ?: "null"
        var title = this.selectFirst("header h2")?.text() ?: "null"
        val posterUrl = this.selectFirst("div figure img")?.attr("src") ?: "null"

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<AnimeSearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //return document.select("div.post-filter-image").mapNotNull {
        return document.select("ul[data-results] li article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        
        val document = app.get(url).document

        //skip ads
        if(document.selectFirst("div.glass-toolbar button")?.text()?.trim() == "click to Verify"){
            val linkkk = Regex("""https:\/\/ez4short\.com\/([^']*)""").find(document.html())?.value!!
            Log.d("TAG","$linkkk")
            bypassez4short(linkkk)
        }

        var title = document.selectFirst("h1.entry-title")?.text()?.trim()  ?: "null"
        val poster  = document.selectFirst("img.trs")?.attr("src") ?: "null"

        val tvType = TvType.TvSeries

        var episodes = mutableListOf<Episode>()

        document.select("ul.seasons-lst li div").mapNotNull {   
            val name = it?.selectFirst("div h3")?.text() ?: "null"
            
            val tempstring = it?.selectFirst("div a")?.attr("href") ?: "null"

            episodes.add( Episode(tempstring, name) )
            }

        


        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster.toString()
                //this.recommendations = recommendations
            }
        
    }




    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        Log.d("TAG","hiiii")
        

        val page1 = app.get(data).document

        //skip ads
        if(page1.selectFirst("div.glass-toolbar button")?.text()?.trim() == "click to Verify"){
            val linkkk = Regex("""https:\/\/ez4short\.com\/([^']*)""").find(page1.html())?.value!!
            Log.d("TAG","$linkkk")
            bypassez4short(linkkk)
        }

        val name = page1?.selectFirst("body")?.attr("class") ?: "null"

        var term = Regex("""\bterm-(\d+)\b""").find(name)?.value!!?.replace("term-","")


        val embedlink = app.get("https://toono.in/?trembed=1&trid="+term)
                            .document?.selectFirst("iframe")?.attr("src") ?: "null"

        Log.d("TAG","$embedlink")

        //https://multiquality.xyz/embed/HhmJXo5d3fvgDnV

        loadExtractor(embedlink, subtitleCallback,callback)
                             

        return true

        
    }

    private suspend fun bypassez4short(link: String) :String{

        //Log.d("TAG","hi from ez4shorts bypass fucntion")
        //Log.d("TAG","lnik $link")

        val req = app.get(link)?.document?.toString() ?: "null"
        //Log.d("TAG","request is")
        //Log.d("TAG","$req")


        val theurl = Regex( """https://(.*)'""").find(req!!)?.value?.replace("'","") ?: "null"

        //https://ez4short.com/gM6kuh the url
        //Log.d("TAG","the url $theurl")

        val client = Requests().baseClient
        val session = Session(client)


        val firstreq = session.get(
            theurl!!,
            referer = "https://techmody.io/"
        )?.document

        fun encode(input: String): String = java.net.URLEncoder.encode(input, "utf-8")

        val datareq = firstreq?.select("#go-link input")
            ?.mapNotNull { it?.attr("name")?.toString()!! to encode(it?.attr("value")?.toString()!!) }
            ?.toMap()

        delay(5000L)
         

        val bypassurlpre = session.post(url = "https://ez4short.com/links/go",
            data = datareq!!,
            headers = mapOf("x-requested-with" to "XMLHttpRequest")
        )?.text

        val bypassurlp = AppUtils.parseJson<EZ4ShortJSON>(bypassurlpre!!)?.ez4shorturl
        
        //.parsedSafe<EZ4ShortJSON>()//?.ez4shorturl

        val onclickdoc = app.get(bypassurlp!!)?.document?.selectFirst("div.mButton button")?.attr("onclick") ?: "null"

        //Log.d("TAG","onclickdoc $onclickdoc")

        val bypassurlfinal = Regex( """(["'])(https?:\/\/[^\s"'<>]+)\1""").find(onclickdoc)?.
                                value?.replace("\"","")?.replace("http","https") ?: "null"
        
        return bypassurlfinal!!
    }

    data class EZ4ShortJSON(
    //{'status': 'success', 'message': 'Go without Earn because Adblock', 
    //'url': 'https://gdrivez.xyz/redirect/MHVkOGpqenRlYzA5amVhL2VsaWYvbW9jLmVyaWZhaWRlbS53d3cvLzpzcHR0aA=='}
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("message") val mesage: String? = null,
    @JsonProperty("url") val ez4shorturl: String? = null,
)

    
}

