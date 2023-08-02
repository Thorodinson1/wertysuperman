package com.dontseehere

import android.util.Log

import com.fasterxml.jackson.annotation.JsonProperty

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import kotlinx.coroutines.delay
import org.jsoup.nodes.Element

import java.net.URLEncoder


class ToonWorldForAllProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://toonworld4all.me"
    override var name = "Toon World For All"

    override val hasMainPage = true

    //tracks = \[([\s\S]*?)buildPlaylist

    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )


    //Infinite loading in homepage goddamit I am not going to fix this I have no idea
    //what to do

    override val mainPage = mainPageOf(
        "tag/animated-movies/" to "Animated Movies",
        "tag/hollywood-movies/" to "Hollywood Movies",
        "tag/hindi-cartoons/" to "Hindi Cartoons",
        "tag/eng-cartoons/" to "English Cartoons",
        "tag/anime/" to "Anime"
        )


    //taken from hexated, I have no idea wtf I am doing

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }


    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrl(this.selectFirst("h2.entry-title a")?.attr("href") ?: return null)
        var title = this.selectFirst("h2.entry-title")?.text() ?: return null
        title = title.replaceAfter("[Hindi","").replace("[Hindi","")
                .replace("Multi Audio","").replace("Dual Audio","")
                .replace("BluRay","")
        val posterUrl = fixUrlNull(this.selectFirst("div.herald-post-thumbnail a img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //return document.select("div.post-filter-image").mapNotNull {
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        
        val document = app.get(url).document

        var title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "title"
        title = title.replaceAfter("[Hindi","").replace("[Hindi","")
                .replace("Multi Audio","").replace("Dual Audio","")
                .replace("BluRay","")
        
        val poster = document.selectFirst("p img")?.attr("src") ?: "https://ww3.pelisplus.to/images/logo2.png"

        val moviecheck = document.select("a.mks_button_medium").size

        val tvType = if (moviecheck==1
        ) TvType.Movie else TvType.TvSeries
        
        //val plot = document.selectFirst("div.description")?.text()?.trim()
        //val year = document.selectFirst("div.genres.rating span a")?.text()?.trim()?.toInt() ?:null

        return if (tvType == TvType.TvSeries) {

            var episodes: MutableList<Episode> = mutableListOf<Episode>()


            
            //If no other season found
            if ( document.select("p:contains(DOWNLOAD SEASON) a").isNullOrEmpty() ){
                episodes = scrapSingleSeason(url, 1)
            }

            //This scraps the site till it finds ALL the seasons in single page
            else{
                //var s= Regex("""season-()\d""").find(url)?.value?.replace("season-","")?.toInt()
                //episodes = scrapSingleSeason(url, s!!)

                var seasonlist: MutableList<String> = mutableListOf<String>()
                
                document.select("p:contains(DOWNLOAD SEASON) a")?.mapNotNull{
                    val seasonlinktemp = it?.attr("href") ?: "null"
                    seasonlist.add(seasonlinktemp)
                }
                seasonlist.add(url)

                seasonlist?.apmap{
                    //val newepisodes = episodes.addAll(scrapSingleSeason(it.toString()!!))
                    //for (i in scrapSingleSeason(it?.attr("href").toString()!!) ){
                    //    episodes.add(i)
                    //}
                    var seasonurl = it
                    var s = Regex("""season-()\d""").find(seasonurl)
                        ?.value?.replace("season-","")?.toInt()
                    episodes.addAll( scrapSingleSeason(seasonurl, s!!) )
                }
            }     

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster.toString()
                //this.year = year
                //this.plot = plot
                //this.recommendations = recommendations
            } 

        } else {

            //Type redirect/main.php bla bla bla
            val movielink = document.select("a.mks_button_medium")?.attr("href") ?: "null"
            newMovieLoadResponse(title, movielink, TvType.Movie, movielink) {
                this.posterUrl = poster.toString()
                //this.year = year
                //this.plot = plot
                //this.recommendations = recommendations
            }
        }
    }

    private suspend fun scrapSingleSeason( seasonlink : String, seasonNum:Int): MutableList<Episode> {
        val document = app.get(seasonlink).document
        var ep = mutableListOf<Episode>()

        Log.d("TAG","season $seasonlink")

        //Episode list with blue buttons for each episode
        document.select("div.mks_accordion_item").mapNotNull {
            val href = fixUrl(it?.select("div.mks_accordion_content p a")?.attr("href") ?:"null")
            //Log.d("TAG","episode $href")
            val name = it?.selectFirst("div.mks_accordion_heading")?.text() ?: "null"
            ep.add( Episode(href, name, seasonNum) )
        }

        //Example of this page type -> Trasformers Prime and Phineas and Ferb
        val hrefall = document.selectFirst("div.mks_toggle_content a:contains(Mirror)")?.attr("href") ?:"null"
        if(hrefall!=="null"){
            val mirrortoallep = rocktoonworld(hrefall)
            //Now our link looks like this https://www.mirrored.to/multilinks/1baxd9mvdf
            //amap as can have multple seasons
            if(mirrortoallep.contains("https://www.mirrored.to")){
                val eplistpage = app.get(mirrortoallep).document.select("ul li").amap{
                    val hrefsingle = it?.selectFirst("a")?.attr("href") ?: "null"
                    Log.d("TAG", "href single is $hrefsingle")
                    var name = Regex("""\/([^\/]+)\.mkv_links""").
                                find(hrefsingle.toString())?.value
                                ?.replace("/[Toonworld4all]_","")?.replace(".mkv_links","")
                                ?.replace("_"," ")?.replaceAfter("[Hindi","")?.replace("[Hindi","")
                    ep.add( Episode(hrefsingle, name, seasonNum) )
                }
            }
            else if(mirrortoallep.contains("http://pastehere.xyz")){
                val eplistpage = app.get(mirrortoallep).document.select("div ol li").amap{
                val hrefsingle = it?.selectFirst("a")?.attr("href") ?: "null"
                var name = Regex("""\/([^\/]+)\.mkv_links""").
                            find(hrefsingle.toString())?.value
                            ?.replace("/[Toonworld4all]_","")?.replace(".mkv_links","")
                            ?.replace("_"," ")?.replaceAfter("[Hindi","")?.replace("[Hindi","")
                ep.add( Episode(hrefsingle, name, seasonNum) )   
                }
            }
        }

        return ep


    }




    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val isitmirrorto = !app.get(data).document.select("a:contains(Mirror)").isNullOrEmpty()
        
        //direct mirror.to link, you might be here from pastebin.xyz, or Transformers Prime
        if(data.contains("https://www.mirrored.to/")){
            mirrortounlock(data, subtitleCallback, callback)
        }

        //This means the show is a series
        else if (data.contains("$mainUrl/episode/")){        
            //Now we have a link like this
            //https://toonworld4all.me/episode/legend-of-korra-3x1

            //If if it is a series AND contains mirror to links in series page
            val page = app.get(data).document

            val seriesandmirror = page.select("a:contains(Mirror)")
            val isseriesanddood = !page.select("a:contains(DOOD)").isNullOrEmpty()

            //Is a series AND contain direct doostream
            //contains a DoodStream directly like here https://toonworld4all.me/episode/beyblade-burst-rise-1x1

            if(isseriesanddood){

            val hrefdood = page.select("a:contains(DOOD)")?.attr("href") ?: "null"
            var newlink = rocktoonworld(hrefdood)

            if(newlink.contains("dood")){
                    newlink = newlink.replace("/d/", "/e/")
                }
                for(streamsblink in listOf("streamsb","streamsss","sbflix","sbchill","sbfull","sbhight","sbanh")){
                    if(newlink.contains(streamsblink)){
                        val l = "https://".length + streamsblink.length + ".com/".length

                        //newlink = newlink.substring(0,l) + "e/" + newlink.substring(l)
                        newlink = "https://watchsb.com/" + "e/" + newlink.substring(l)
                    }
                }
                if(newlink.contains("streamtape")){
                    newlink = newlink.replace("/v/", "/e/")
                }
                
                newlink = newlink.replace(".html", "")
                Log.d("TAG","is dood $newlink")
            loadExtractor(newlink, subtitleCallback, callback)
        }

        //If series, and contains mirror to links

            seriesandmirror.amap{
                val curqualitylink = it?.attr("href") ?: "null"
                val bypass = rocktoonworld(curqualitylink)
                Log.d("TAG","$bypass")
                mirrortounlock(bypass, subtitleCallback, callback)
                
            }   


        }

        //Movie page
        else if(isitmirrorto && data.contains("$mainUrl/redirect/main.php")){
            val curqualitylink = app.get(data).document.select("a:contains(Mirror)")?.attr("href") ?: "null"
            val bypass = rocktoonworld(curqualitylink)
            mirrortounlock(bypass, subtitleCallback, callback)
        }

        //Movie and no Mirror to, but direct Doodstream etc
        else if(!isitmirrorto){
                var newlink = rocktoonworld(data)
                if(newlink.contains("dood")){
                    newlink = newlink.replace("/d/", "/e/")
                }
                for(streamsblink in listOf("streamsb","streamsss","sbflix","sbchill","sbfull","sbhight","sbanh")){
                    if(newlink.contains(streamsblink)){
                        val l = "https://".length + streamsblink.length + ".com/".length

                        //newlink = newlink.substring(0,l) + "e/" + newlink.substring(l)
                        newlink = "https://watchsb.com/" + "e/" + newlink.substring(l)
                    }
                }
                if(newlink.contains("streamtape")){
                    newlink = newlink.replace("/v/", "/e/")
                }

                Log.d("TAG","movie mirror not found -> $newlink")

                loadExtractor(newlink, subtitleCallback, callback)

            }
        
        return true
    }

    private suspend fun mirrortounlock(link: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
            var bypass = link
            if(bypass.contains("https://mir.cr")){
                bypass = "https://www.mirrored.to/files/" + bypass.substring(15)
            }

            if(!bypass.contains("https://www.mirrored.to/")){
                return
            }
            
            val part2doclink = app.get(bypass).document?.selectFirst("div.col-sm a")?.attr("href") ?: "null"
            val part2doc = app.get(part2doclink).toString()
            var link2 = Regex("""ajaxRequest.open\("GET", "(.*)", true\);""").find(part2doc)!!.value!!
            link2 = "https://www.mirrored.to"+link2.replace("ajaxRequest.open(\"GET\", \"","").replace("\", true);","")
            val sources = listOf("DoodStream","StreamTape","StreamSB") // and zippyshare?????
            val part3 = app.get(link2).document
            for(source in sources){
                val sourcelink = "https://www.mirrored.to" + 
                        part3?.selectFirst("img[alt=\""+source+"\"]")?.parent()
                        ?.nextElementSibling()?.child(0)?.attr("href") ?: "null"
                var part4 = app.get(sourcelink).document?.selectFirst("div.code_wrap")?.text() ?: "null"
                if(source=="DoodStream"){
                    part4 = part4.replace("/d/", "/e/")
                }
                if(source=="StreamTape"){
                    part4 = part4.replace("/v/", "/e/")
                }
                if(source=="StreamSB"){
                    for(streamsblink in listOf("streamsb","streamsss","sbflix","sbchill","sbfull","sbhight","sbanh")){
                        if(part4.contains(streamsblink)){
                            val l = "https://".length + streamsblink.length + ".com/".length
                            part4 = "https://watchsb.com/" + "e/" + part4.substring(l)
                        }                        
                    }           
                }
                Log.d("TAG","mirror to $part4")  
                loadExtractor(part4, subtitleCallback, callback) 
            }
    }

    private suspend fun rocktoonworld(redirectulr: String) :String{

        val payload = redirectulr.replace("https://toonworld4all.me/redirect/main.php?url=","")

        val req = app.get(redirectulr,
        headers = mapOf(
            """authority""" to """toonworld4all.me""",
            """accept""" to """text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9""",
            """accept-language""" to """en-US,en;q=0.9""",
            //cookie = ...
            """sec-ch-ua""" to """ "Not_A Brand";v="99", "Microsoft Edge";v="109", "Chromium";v="109" """,
            """sec-ch-ua-mobile""" to """?0""",
            """sec-ch-ua-platform""" to """ "Windows" """,
            """sec-fetch-dest""" to """document""",
            """sec-fetch-mode""" to """navigate""",
            """sec-fetch-site""" to """none""",
            """user-agent""" to """Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36 Edg/109.0.1518.55""",
        ),
        ).toString()
        var link = Regex("cUPMDTk: (.*)__").find(req)!!.value!!
        link = link.replace("""cUPMDTk: "\/"""," https://go.rocklinks.net/").replace("?__","")

        val x = bypassRockLinks(link)

        return x

    }

   private suspend fun bypassRockLinks(link: String) :String{
        val domain =
            if (link.contains("rocklinks")) "https://link.techyone.co" else "https://cac.teckypress.in"
        val baseUrl =
            if (link.contains("rocklinks")) "$domain/${link.substringAfterLast("/")}?quelle=" else "$domain/${
                link.substringAfterLast("/")
            }"
        val client = Requests().baseClient
        val session = Session(client)
        val html = session.get(url = baseUrl, referer = baseUrl)
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

        Log.d("TAG","$link")
        Log.d("TAG", "${bypassedLink.toString()}")

        app.get(bypassedLink).document

        return bypassedLink
    }

    data class Response(
        @JsonProperty("url") var url: String
    )

}

