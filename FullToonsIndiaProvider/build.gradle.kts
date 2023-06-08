version = 9


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

    description = "hindi dubbed cartoons - only Doodstream works, wait 10 seconds for Mediafire links"
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

    iconUrl = "https://www.google.com/s2/favicons?domain=www.fulltoonsindia.com&sz=%size%"
}
