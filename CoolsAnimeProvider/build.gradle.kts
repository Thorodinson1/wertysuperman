version = 4


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

    description = "Hindi dubbed cartoons - Good site imo, if broken links found ping dontseehere on Discord"
    authors = listOf("dontseehere")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "Cartoon"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=https://coolsanime.me//&sz=%size%"
}
