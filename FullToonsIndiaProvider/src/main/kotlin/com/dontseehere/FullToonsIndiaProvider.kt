package com.dontseehere

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.utils.*
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element

//adb logcat | find "TAG"
//com.lagradost.cloudstream3.prerelease

var doodexist = false
var mediafireexist = false

class FullToonsIndiaProvider : MainAPI() { // all providers must be an instance of MainAPI
    //override var mainUrl = "https://raretoonshindi.in/"
    override var mainUrl = "https://www.fulltoonsindia.com"
    override var name = "FullToonsIndia"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )


    //Infinite loading in homepage goddamit I am not going to fix this I have no idea
    //what to do
    override val mainPage = mainPageOf(
        "search/" to "Recent Posts",
        "search/label/Power%20Rangers" to "Power Rangers",
        "search/label/Pokemon" to "Pokemon",
        "search/label/Doraemon" to "Doraemon",
        )


    //taken from hexated, I have no idea wtf I am doing
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        //requests.data is search/label/Power%20Rangers


        // val results = mutableListOf<Element>()

        // var loadmore = !document.select("a.load-more").isNullOrEmpty()

        // var cnt = 3

        // var newdocument = document

        // while(loadmore && cnt>0){
        //     val newpagehref = newdocument.select("a.load-more")?.attr("data-load") ?: "null"
            
        //     newdocument = app.get("$newpagehref").document

        //     newdocument.select("div.post-filter-image").mapNotNull {
            
        //     //it.toSearchResult()
        //     results.add(it)
        // }

        //     loadmore = !newdocument.select("a.load-more").isNullOrEmpty()
        //     cnt = cnt -1

        // }

        // val home =  results.mapNotNull{
        //     it.toSearchResult()
        // }

        Log.d("TAG","At top page $page and $request")
        
        val document = if(page==1){
                    app.get("$mainUrl/${request.data}").document
        } else if(page==2){app.get("$mainUrl/${request.data}").document}
        else{app.get("${request.data}").document}

        var newpagehref = if(page==1){
                    request.data
        } else if(page==2){document.select("a.load-more")?.attr("data-load") ?: "null"}
        else{document.select("a.load-more")?.attr("data-load") ?: "null"}
    
        val home = document.select("div.post-filter-image").mapNotNull {
            it.toSearchResult()
        }

        Log.d("TAG","newhref for next is $newpagehref")


        //return newHomePageResponse(request.name, home)

        return newHomePageResponse(request.name, home)
    }


    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        //val href = "https://www.google.com"
        val title = this.selectFirst("img")?.attr("alt") ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toBottomSearchResult(): AnimeSearchResponse? {
        //To find parent do .parent()
        //To find sibling DONT do previousElementSibling()
        // do cur ~ prev to find previous element sibling
        //okay?

        val title = this.parent()?.previousElementSibling()?.text() ?: return null
        //val title = "test"
        //val title = this.parent()?.select("~ p ~ h2")?.text()?:return null
        val href = this?.attr("href") ?:return null
        //val posterUrl = this.parent()?.previousElementSibling()?.selectFirst("picture source")?.attr("srcset").toString().split(" ")[0]
        
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            //this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/?q=$query").document


        val results = mutableListOf<Element>()

        document.select("div.post-filter-image").mapNotNull {
            //it.toSearchResult()
            results.add(it)
        }

        var loadmore = !document.select("a.load-more").isNullOrEmpty()

        var cnt = 3

        var newdocument = document

        while(loadmore && cnt>0){
            val newpagehref = newdocument.select("a.load-more")?.attr("data-load") ?: "null"
            Log.d("TAG","$newpagehref")
            newdocument = app.get("$newpagehref").document

            newdocument.select("div.post-filter-image").mapNotNull {
            
            //it.toSearchResult()
            results.add(it)
        }

            loadmore = !newdocument.select("a.load-more").isNullOrEmpty()
            cnt = cnt -1

        }


        return results.mapNotNull{
            it.toSearchResult()
        }
    }




    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document

        
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.separator img")?.attr("src"))
        //val year = document.select(".year").text().trim().toIntOrNull()

        doodexist = !document.select("a[href^=https://dood]").isNullOrEmpty()
        mediafireexist = !document.select("a:contains(Mediafire)").isNullOrEmpty()


        val tvType = if (!doodexist && !mediafireexist
        ) TvType.Movie else TvType.TvSeries
        
        //val description = document.selectFirst(".description > p:nth-child(1)")?.text()?.trim()
        val recommendations = document.select("a[href^=https://www.fulltoonsindia.com]:contains(LINK)").mapNotNull {
            it.toBottomSearchResult()
        }

        return if (tvType == TvType.TvSeries) {

            var episodes = listOf<Episode>()
            
            if(doodexist){
            episodes = document.select("a[href^=https://dood]").mapNotNull {
                val href = fixUrl(it?.attr("href") ?: return null)
                val name = it?.text()?.trim() ?: return null


                Episode(
                    href,
                    name,
                )
                }
            }

            else if(mediafireexist){
            episodes = document.select("a:contains(Mediafire)").mapNotNull {
                val href = fixUrl(it?.attr("href") ?: return null)
                val name = it?.text()?.trim() ?: return null


                Episode(
                    href,
                    name,
                )
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                //this.year = year
                //this.plot = description
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                //this.year = year
                //this.plot = description
                this.recommendations = recommendations
            }
        }
    }




    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {


        var output:String = "null"

        if(doodexist){
        output = data.replace("/d/", "/e/")

        loadExtractor(output, subtitleCallback, callback)
        }


        else if(mediafireexist){

            output = bypassRockLinks(data)

            val mediafiredoc = app.get(output).document

            output = mediafiredoc.select("a[aria-label]")?.attr("href") ?: "null"

            callback.invoke(
                                ExtractorLink(
                                    source = name,
                                    name = "Full Toon India",
                                    url = output,
                                    referer = "$mainUrl/",
                                    quality = Qualities.P360.value
                                )
                            )

        }


        else{
        loadExtractor(output, subtitleCallback, callback)
        }


        //callback.invoke(
        //    ExtractorLink(
        //        this.name,
        //        this.name,
        //        data.replace("\\", ""),
        //        referer = mainUrl,
        //        quality = Qualities.Unknown.value,
//                headers = mapOf("Range" to "bytes=0-"),
        //    )
        //)
        return true
    }

    private suspend fun bypassRockLinks(link: String) :String {
        val domain =
            //if (link.contains("rocklinks")) "https://link.techyone.co" else "https://cac.teckypress.in"
            if (link.contains("rocklinks")) "https://rl.techysuccess.com" else "https://cac.teckypress.in"
        val baseUrl =
            if (link.contains("rocklinks")) "$domain/${link.substringAfterLast("/")}?quelle=" else "$domain/${
                link.substringAfterLast("/")
            }"
        

        val ref = "https://disheye.com/"


        val client = Requests().baseClient
        val session = Session(client)
        //val html = session.get(url = baseUrl, referer = baseUrl)
        val html = session.get(url = baseUrl, referer = ref)
        fun encode(input: String): String = java.net.URLEncoder.encode(input, "utf-8")
        val document = html.document
        val data = document.select("#go-link input")
            .mapNotNull { it.attr("name").toString() to encode(it.attr("value").toString()) }
            .toMap()

        delay(10000L)
        val response = session.post(
            url = "$domain/links/go",
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json, text/javascript, ; q=0.01",
                "Accept-Language" to "en-US,en;q=0.5",
                //"Accept-Encoding" to "gzip"
            ),
            data = data,
            referer = baseUrl
        ).text
        val bypassedLink = AppUtils.parseJson<Response>(response).url
        app.get(bypassedLink).document

        return bypassedLink
    }


    

    data class Response(
        @JsonProperty("url") var url: String
    )
}