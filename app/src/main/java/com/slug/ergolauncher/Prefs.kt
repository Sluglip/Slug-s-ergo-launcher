package com.slug.ergolauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/** All launcher settings, stored in SharedPreferences. Reads are live, so every screen sees changes immediately. */
class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("ergo", Context.MODE_PRIVATE)

    var wheelSize: Float
        get() = sp.getFloat("wheelSize", 1f)
        set(v) = sp.edit().putFloat("wheelSize", v).apply()

    var wheelOpacity: Float
        get() = sp.getFloat("wheelOpacity", 0.86f)
        set(v) = sp.edit().putFloat("wheelOpacity", v).apply()

    /** Icon diameter in dp. */
    var iconSize: Int
        get() = sp.getInt("iconSize", 56)
        set(v) = sp.edit().putInt("iconSize", v).apply()

    var color: Int
        get() = sp.getInt("color", Color.parseColor("#F2B544"))
        set(v) = sp.edit().putInt("color", v).apply()

    /** True when the wheel sits in the bottom-left corner. */
    var left: Boolean
        get() = sp.getBoolean("left", false)
        set(v) = sp.edit().putBoolean("left", v).apply()

    /** 0 = no labels, 1 = folder labels only, 2 = folder and app labels. */
    var labels: Int
        get() = sp.getInt("labels", 1)
        set(v) = sp.edit().putInt("labels", v).apply()

    var remember: Boolean
        get() = sp.getBoolean("remember", false)
        set(v) = sp.edit().putBoolean("remember", v).apply()

    var reduceMotion: Boolean
        get() = sp.getBoolean("reduceMotion", false)
        set(v) = sp.edit().putBoolean("reduceMotion", v).apply()

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Seconds to hold an app before it can be moved or uninstalled. */
    var holdMove: Float
        get() = sp.getFloat("holdMove", 2f)
        set(v) = sp.edit().putFloat("holdMove", v).apply()

    /** Seconds to hold Back on the folder ring to open the Hidden folder. */
    var holdHidden: Float
        get() = sp.getFloat("holdHidden", 2f)
        set(v) = sp.edit().putFloat("holdHidden", v).apply()

    /** Seconds to hold a widget slot to change its widget. */
    var holdWidget: Float
        get() = sp.getFloat("holdWidget", 1f)
        set(v) = sp.edit().putFloat("holdWidget", v).apply()

    var autoSort: Boolean
        get() = sp.getBoolean("autoSort", true)
        set(v) = sp.edit().putBoolean("autoSort", v).apply()

    var lastCat: Int
        get() = sp.getInt("lastCat", -1)
        set(v) = sp.edit().putInt("lastCat", v).apply()

    /** Number of staggered lanes on the app ring, 1 to 5. */
    var rows: Int
        get() = sp.getInt("rows", 3).coerceIn(1, 5)
        set(v) = sp.edit().putInt("rows", v).apply()

    /** Radius in dp of the outermost app lane: how far the icons sit from the corner. */
    var reach: Int
        get() = sp.getInt("reach", 232).coerceIn(160, 270)
        set(v) = sp.edit().putInt("reach", v).apply()

    /** Widget canvas as JSON: [{id,x,y,w,h}] in dp. Empty string means never saved. */
    var widgetsJson: String
        get() = sp.getString("widgets", "") ?: ""
        set(v) = sp.edit().putString("widgets", v).apply()

    fun widgetId(slot: Int): Int = sp.getInt("widget$slot", -1)

    fun setWidgetId(slot: Int, id: Int) {
        sp.edit().putInt("widget$slot", id).apply()
    }
}
