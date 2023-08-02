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

class CoolsAnimeProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://coolsanime.me/"
    override var name = "Cools Anime"

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
        val link = "$mainUrl${request.data}page/$page/"
        val document = app.get(link).document
        val home = document.select("li.post-item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }



    private fun Element.toSearchResult(): AnimeSearchResponse? {
        //val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val href = this.selectFirst("a[aria-label]")?.attr("href") ?: "null"
        var title = this.selectFirst("a[aria-label]")?.attr("aria-label") ?: "null"
        val posterUrl = this.selectFirst("a img")?.attr("data-lazy-src") ?: "null"

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<AnimeSearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //return document.select("div.post-filter-image").mapNotNull {
        return document.select("li.post-item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        
        val document = app.get(url).document

        var title = document.selectFirst("h1")?.text()?.trim()  ?: "null"
        val poster  = document.selectFirst("p img")?.attr("data-lazy-src") ?: "null"

        val tvType = TvType.TvSeries

        var episodes = mutableListOf<Episode>()

        //var c = 1

        //p:has(strong) strong

        var linkcombined = ""

        //check for links.gdrive
        var haslinksgdrive = document.select("p:has(a)")?.select("a")?.attr("href") ?:"null"

        if ("links.gdrivez" in haslinksgdrive){

            val gdrivearchivedoc = app.get(haslinksgdrive).document

            gdrivearchivedoc.select("p:not([class]):has(a)").mapNotNull {
                ep ->    
                val name = ep?.text() ?: "null"
                ep?.select("a")?.mapNotNull{
                    val tempstring = it?.attr("href") ?: "null"

                    

                    linkcombined = "$linkcombined $tempstring"
                    //val name = c.toString()
                    //c = c+1

                    //Log.d("TAG","$linkcombined")
                }

                if(linkcombined.contains("gdrive")){
                    episodes.add( Episode(linkcombined, name) )
                } 

                linkcombined = ""
                
                }

        }

        else{
            document.select("p:has(a)").mapNotNull {
                ep ->    
                val name = ep?.text() ?: "null"
                ep?.select("a")?.mapNotNull{
                    val tempstring = it?.attr("href") ?: "null"

                    linkcombined = "$linkcombined $tempstring"
                    //val name = c.toString()
                    //c = c+1
                }

                if(linkcombined.contains("gdrive")){
                    episodes.add( Episode(linkcombined, name) )
                } 

                linkcombined = ""
                
                }

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

        val eachep = data.trim().split(" ").map { it -> it.trim() }

        //Log.d("TAG","$data")

        eachep.apmap{

            singleep ->

            Log.d("TAG","$singleep")

                if(singleep.contains("gdrive")){
                    val doc = app.get(singleep).document

                    val vkshare = doc?.select("a:contains(vkshare)")?.attr("href") ?: "null"
                    val mediafire = doc?.select("a:contains(mediafire)")?.attr("href") ?: "null"
                    val doodstream = doc?.select("a:contains(dood)")?.attr("href") ?: "null"
                    val sbbrisk = doc?.select("a:contains(sbbrisk)")?.attr("href") ?: "null"
                    val sbface = doc?.select("a:contains(sbface)")?.attr("href") ?: "null"

                    val sources = listOf(vkshare, mediafire, doodstream, sbbrisk, sbface)


                    sources.apmap{
                            cursource ->

                            //Log.d("TAG","$cursource")
                            
                            if(cursource.contains("gdrive")){
                                val output = bypassez4short(cursource) ?: "null"
                                //Log.d("TAG","cursource ${cursource==mediafire}")

                                if(cursource==mediafire){
                                    val mediafirelink = app.get(output,
                                    headers =mapOf(
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:47.0) Gecko/20100101 Firefox/47.3"
                                    )
                                    )
                                    Log.d("TAG","mediafire $output")
                                    Log.d("TAG","$mediafirelink")
                                    val mediafiredoc = mediafirelink?.document

                                    val mediafirefinal = mediafiredoc?.select("a[aria-label]")?.attr("href") ?: "null"
                                    //Log.d("TAG","mediafire final $mediafirefinal")
                                    callback.invoke(
                                        ExtractorLink(
                                            source = name, name = "MediaFire", url = mediafirefinal!!, referer = "$mainUrl/",
                                            quality = Qualities.Unknown.value
                                        )
                                    )
                                }
                                
                                if(cursource==vkshare){
                                    //Log.d("TAG","output hiiiiii")
                                    //Log.d("TAG", output)
                                    val outputnew = output?.replace("httpss","https")!!
                                    val vkshare_page = app.get(outputnew)?.document?.html()!!
                                    //val link = vkshare_page?.selectFirst("script")?.get(4)?.text() ?: "null"
                                    //Log.d("TAG",vkshare_page)
                                    var vk_link = Regex("""(?<=")(.*?)(?=&export=download)""").find(vkshare_page)?.value!!
                                    vk_link += "&export=download"

                                    Log.d("TAG", "$vk_link")
                                    //Log.d("TAG", "$vkshare_page")
                                    callback.invoke(
                                        ExtractorLink(
                                            source = name, name = "vDrive", url = vk_link!!, referer = "$mainUrl/",
                                            quality = Qualities.Unknown.value
                                        )
                                    )
                                    
                                }

                                if(cursource==doodstream){
                                    val doodlink = output.replace("/d/","/e/")
                                                    .replace("httpss","https")

                                    Log.d("TAG","$doodlink")

                                    loadExtractor(doodlink, subtitleCallback,callback)
                                }
                                if(cursource==sbbrisk){
                                    val sbbrisklink = output.replace("sbbrisk.com","watchsb.com/e")
                                                            .replace("httpss","https")

                                    loadExtractor(sbbrisklink, subtitleCallback,callback)
                                }
                                if(cursource==sbface){
                                    val sbface = output.replace("sbface.com","watchsb.com/e")
                                                        .replace("httpss","https")
                                    Log.d("TAG","$sbface")

                                    loadExtractor(sbface, subtitleCallback,callback)
                                }
                            }

                    }
                }
        }

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

