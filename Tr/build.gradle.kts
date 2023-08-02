version = 8


cloudstream {
    language = "hi"
    // All of these properties are optional, you can safely remove them

    description = "REPORT BROKEN LINKS IN DISCORD AND PING DONTSEEHERE, Hindi cartoons, wait for other video quality to load, each takes 5 sec bcz rocklinks :/"
    authors = listOf("dontseehere")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 0 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie"
    )

    iconUrl = "https://toonworld4all.me/wp-content/uploads/2020/02/Toonworld4all-logo.png"
}
