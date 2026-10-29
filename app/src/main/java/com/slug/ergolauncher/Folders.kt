package com.slug.ergolauncher

/**
 * Folder ids. The ring shows them in [RING] order. Hidden only appears while moving an app
 * (or when opened by holding Back), and Launcher Settings is a shortcut, not a real folder.
 * Favorites is special: an app can be in it and in its own category at the same time.
 */
object Folders {
    const val FAVORITES = 0
    const val COMMUNICATION = 1
    const val GAMES = 2
    const val INTERNET = 3
    const val MEDIA = 4
    const val UTILITIES = 5
    const val OTHER = 6
    const val RECENT = 7
    const val LAUNCHER = 8
    const val HIDDEN = 9

    val NAMES = listOf(
        "Favorites", "Communication", "Games", "Internet", "Media", "Utilities",
        "Other", "Search / Recent", "Launcher Settings", "Hidden"
    )

    val ICONS = intArrayOf(
        R.drawable.ic_star, R.drawable.ic_msg, R.drawable.ic_game, R.drawable.ic_globe,
        R.drawable.ic_play, R.drawable.ic_calc, R.drawable.ic_dots, R.drawable.ic_search,
        R.drawable.ic_gear, R.drawable.ic_hidden
    )

    /** Ring order normally. */
    val RING = listOf(FAVORITES, COMMUNICATION, GAMES, INTERNET, MEDIA, UTILITIES, OTHER, RECENT, LAUNCHER)

    /** Ring order while moving an app: plain folders and Hidden are targets. */
    val RING_MOVE = listOf(COMMUNICATION, GAMES, INTERNET, MEDIA, UTILITIES, OTHER, RECENT, HIDDEN)

    /** Converts a v1 folder id (which had a Settings folder at 5) to the current ids. */
    fun fromV1(old: Int): Int = when (old) {
        0 -> COMMUNICATION
        1 -> GAMES
        2 -> INTERNET
        3 -> MEDIA
        4 -> UTILITIES
        5, 6 -> OTHER
        7 -> RECENT
        8 -> HIDDEN
        else -> OTHER
    }
}
