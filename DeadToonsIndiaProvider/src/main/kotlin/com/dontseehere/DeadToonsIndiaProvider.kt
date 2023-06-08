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

class DeadToonsIndiaProvider : MainAPI() { // all providers must be an instance of MainAPI
    //override var mainUrl = "https://raretoonshindi.in/"
    override var mainUrl = "https://deadtoons.co"
    override var name = "DeadToonsIndia"

    //override val hasMainPage = true
    override val hasMainPage = false

    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )


    //Infinite loading in homepage goddamit I am not going to fix this I have no idea
    //what to do

    override val mainPage = mainPageOf(
       "/" to "Latest",
       )


    //taken from hexated, I have no idea wtf I am doing

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
       val document = app.get("$mainUrl${request.data}page/$page/").document
       val home = document.select("div.post-filter-image").mapNotNull {
           it.toSearchResult()
       }
       return newHomePageResponse(request.name, home)
    }


    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val href = fixUrl(this.selectFirst("div.col-lg-6.col-md-6.col-sm-7 div.entry-header h2.entry-title.h3 a")?.attr("href") ?: return null)
        //val href = "https://www.google.com"
        val title = this.selectFirst("div.col-lg-6.col-md-6.col-sm-7 div.entry-header h2.entry-title.h3")?.text() ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.col-lg-6.col-md-6.col-sm-5 div.herald-post-thumbnail.herald-format-icon-middle picture img")?.attr("src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //return document.select("div.post-filter-image").mapNotNull {
        return document.select("article.herald-lay-b.herald-post").mapNotNull {
            it.toSearchResult()
        }
    }

    private fun Element.toBottomSearchResult(): AnimeSearchResponse? {
        //To find parent do .parent()
        //To find sibling DONT do previousElementSibling()
        // do cur ~ prev to find previous element sibling
        //okay?

        val title = this.parent()?.previousElementSibling()?.previousElementSibling()?.text() ?: return null
        //val title = "test"
        //val title = this.parent()?.select("~ p ~ h2")?.text()?:return null
        val href = this?.attr("href") ?:return null
        val posterUrl = this.parent()?.previousElementSibling()?.selectFirst("picture source")?.attr("srcset").toString().split(" ")[0]
        Log.d("TAG","$posterUrl")
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

//NOW DO BELOW

    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document

        //Log.d("TAG", "HELLOOOO")

        
        val title = document.selectFirst("div.col-lg-12.col-md-12.col-sm-12 h2")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.herald-post-thumbnail.herald-post-thumbnail-single div picture img")?.attr("src"))
        //val year = document.select(".year").text().trim().toIntOrNull()



        //div.col-lg-12.col-md-12.col-sm-12 div div h3[style^="text-align: center;"]
        
        val strmsbexist = document.select("div.col-lg-12.col-md-12.col-sm-12 div div h3[style]:contains(StreamSB)")

        //For series pages, different links are
        //h2:not(.entry-title.h5) a[href^=https://deadtoons.co]

        //val seriespagexist = document.select("h2:not(.entry-title.h5) a[href^=https://deadtoons.co]")
        
        //val tvType = if (strmsbexist.isNullOrEmpty() || seriespagexist.isNullOrEmpty()
        val tvType = if (strmsbexist.isNullOrEmpty()
        ) TvType.Movie else TvType.TvSeries
        
        //val description = document.selectFirst(".description > p:nth-child(1)")?.text()?.trim()
        val recommendations = document.select("a.dti_btn").mapNotNull {
            it.toBottomSearchResult()
        }

         

        return if (tvType == TvType.TvSeries) {

            //var episodes : List<Episode> = listOf()
             
             //if(seriespagexist.isNullOrEmpty()){
             
             val epPagelink= document.selectFirst("div.col-lg-12.col-md-12.col-sm-12 div div h3[style]:contains(StreamSB) a")?.attr("href")?: return null
            
             val doc = app.get(epPagelink).document

             val episodes = doc.select("div.dti a").mapNotNull {
                val href = fixUrl(it?.attr("href") ?: return null)

                //Log.d("TAG", epPagelink)

                val name = it?.text()?.trim() ?: return null

                //Log.d("TAG", "opening page, link is $href")

                Episode(
                    href,
                    name,
                )
            }
             
             //}
            //  else{               
            // seriespagexist.forEach {           
            //  val curpagelink = it?.attr("href")?: return null
            //  val curdocument = app.get(url).document
            //  val epPagelink= curdocument.selectFirst("div.col-lg-12.col-md-12.col-sm-12 div div h3[style]:contains(StreamSB) a")?.attr("href")?: return null           
            //  val doc = app.get(epPagelink).document
            //  episodes = doc.select("div.dti a").mapNotNull {
            //     val href = fixUrl(it?.attr("href") ?: return null)
            //     //Log.d("TAG", epPagelink)
            //     val name = it?.text()?.trim() ?: return null
            //     //Log.d("TAG", "opening page, link is $href")
            //     Episode(
            //         href,
            //         name,
            //     )
            // }
            //     }
            //  }


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

        
        
        //data is url
        //https://r.dti.link/redirect/sb/?ydcfvkcg5pbs.html
        //to
        //https://watchsb.com/e/ydcfvkcg5pbs.html

        //Log.d("TAG", "Original data is $data")
        
        //if(strmsbexist){
        val output = data.replace("https://r.dti.link/redirect/sb/?","https://streamsb.net/e/")
        //}

        //Log.d("TAG", "output is $output")

        
        loadExtractor(output, subtitleCallback, callback)

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

}