package com.slug.ergolauncher

/** The nine folders on the categories ring. Hidden is only shown while moving an app or when opened by holding Back. */
object Folders {
    const val COMMUNICATION = 0
    const val GAMES = 1
    const val INTERNET = 2
    const val MEDIA = 3
    const val UTILITIES = 4
    const val SETTINGS = 5
    const val OTHER = 6
    const val RECENT = 7
    const val HIDDEN = 8

    val NAMES = listOf(
        "Communication", "Games", "Internet", "Media", "Utilities",
        "Settings", "Other", "Search / Recent", "Hidden"
    )

    val ICONS = intArrayOf(
        R.drawable.ic_msg, R.drawable.ic_game, R.drawable.ic_globe, R.drawable.ic_play,
        R.drawable.ic_calc, R.drawable.ic_tools, R.drawable.ic_dots, R.drawable.ic_search,
        R.drawable.ic_hidden
    )
}
