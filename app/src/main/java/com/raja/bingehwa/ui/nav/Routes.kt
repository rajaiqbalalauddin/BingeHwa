package com.raja.bingehwa.ui.nav

/** All destinations in one place so navigation strings are never typed twice. */
object Routes {
    const val WELCOME = "welcome"
    const val TASTE = "taste"
    const val LIBRARY = "library"
    const val UPDATES = "updates"
    const val DISCOVER = "discover"
    const val PROFILE = "profile"
    const val NOTIF_SETTINGS = "notifSettings"
    const val SOURCES = "sources"

    const val DETAIL = "detail/{seriesId}"
    const val READER = "reader/{chapterId}/{seriesId}"
    const val COMMENTS = "comments/{seriesId}/{chapterId}"

    fun detail(seriesId: String) = "detail/$seriesId"
    fun reader(chapterId: String, seriesId: String) = "reader/$chapterId/$seriesId"

    /** Use "-" as chapterId for the series-wide thread. */
    fun comments(seriesId: String, chapterId: String?) = "comments/$seriesId/${chapterId ?: "-"}"

    /** Bottom-nav tabs fade between each other; deeper screens slide. */
    val tabs = listOf(LIBRARY, UPDATES, DISCOVER, PROFILE)
}
