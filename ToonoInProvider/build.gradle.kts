version = 1


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

    description = "Hindi dubbed cartoons - if broken links found ping dontseehere on Discord"
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

    iconUrl = "https://toono.in/wp-content/uploads/2023/04/LOGO-2023.png"
}
