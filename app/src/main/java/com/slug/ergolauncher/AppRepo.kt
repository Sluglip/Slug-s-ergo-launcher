package com.slug.ergolauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject

class AppEntry(
    val key: String,
    val pkg: String,
    val cls: String,
    val label: String,
    val sysCategory: Int
)

/**
 * Knows every launchable app, which folder it lives in, which apps are new, and the recent list.
 * New installs are sorted automatically using the category the app declares about itself.
 */
class AppRepo(private val ctx: Context, private val prefs: Prefs) {

    var apps: List<AppEntry> = emptyList()
        private set

    private val byKey = HashMap<String, AppEntry>()
    private val assign = HashMap<String, Int>()
    private val fresh = HashSet<String>()
    private val recent = ArrayList<String>()
    private val known = HashSet<String>()
    private val iconCache = HashMap<String, Drawable>()

    init {
        loadState()
    }

    /** Reads folder assignments, new-app flags and recents from storage. */
    fun loadState() {
        assign.clear()
        fresh.clear()
        recent.clear()
        known.clear()
        try {
            val o = JSONObject(prefs.sp.getString("assign", "{}") ?: "{}")
            for (k in o.keys()) assign[k] = o.getInt(k)
        } catch (e: Exception) {
        }
        try {
            val a = JSONArray(prefs.sp.getString("recent", "[]") ?: "[]")
            for (i in 0 until a.length()) recent.add(a.getString(i))
        } catch (e: Exception) {
        }
        fresh.addAll(prefs.sp.getStringSet("fresh", emptySet()) ?: emptySet())
        known.addAll(prefs.sp.getStringSet("known", emptySet()) ?: emptySet())
    }

    private fun save() {
        val o = JSONObject()
        for ((k, v) in assign) o.put(k, v)
        val a = JSONArray()
        for (r in recent) a.put(r)
        prefs.sp.edit()
            .putString("assign", o.toString())
            .putString("recent", a.toString())
            .putStringSet("fresh", HashSet(fresh))
            .putStringSet("known", HashSet(known))
            .apply()
    }

    /** Rescans installed apps. Returns true if anything changed. */
    fun refresh(): Boolean {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = ArrayList<AppEntry>()
        for (ri in pm.queryIntentActivities(intent, 0)) {
            val ai = ri.activityInfo
            if (ai.packageName == ctx.packageName) continue
            found.add(
                AppEntry(
                    ai.packageName + "/" + ai.name,
                    ai.packageName,
                    ai.name,
                    ri.loadLabel(pm).toString(),
                    ai.applicationInfo.category
                )
            )
        }
        found.sortBy { it.label.lowercase() }

        val firstRun = known.isEmpty()
        var changed = false
        val keys = HashSet<String>()
        for (a in found) keys.add(a.key)

        for (a in found) {
            if (!assign.containsKey(a.key)) {
                assign[a.key] = if (prefs.autoSort) autoFolder(a) else Folders.OTHER
                if (!firstRun && !known.contains(a.key)) fresh.add(a.key)
                changed = true
            }
        }

        val gone = assign.keys.filter { !keys.contains(it) }
        if (gone.isNotEmpty()) {
            for (g in gone) {
                assign.remove(g)
                fresh.remove(g)
            }
            changed = true
        }
        if (recent.removeAll { !keys.contains(it) }) changed = true

        known.clear()
        known.addAll(keys)

        apps = found
        byKey.clear()
        for (a in found) byKey[a.key] = a
        iconCache.clear()
        if (changed || firstRun) save()
        return changed
    }

    /** Picks a folder from the category the app declares, with a name-based fallback. */
    private fun autoFolder(a: AppEntry): Int {
        when (a.sysCategory) {
            ApplicationInfo.CATEGORY_GAME -> return Folders.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE -> return Folders.MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> return Folders.COMMUNICATION
            ApplicationInfo.CATEGORY_NEWS -> return Folders.INTERNET
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return Folders.UTILITIES
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> return Folders.SETTINGS
        }
        val p = a.pkg.lowercase()
        return when {
            p.contains("settings") -> Folders.SETTINGS
            p.contains("dialer") || p.contains("contacts") || p.contains("messag") ||
                p.contains("mms") || p.contains("sms") || p.contains("mail") -> Folders.COMMUNICATION
            p.contains("chrome") || p.contains("browser") || p.contains("firefox") -> Folders.INTERNET
            p.contains("camera") || p.contains("gallery") || p.contains("photos") ||
                p.contains("music") || p.contains("video") || p.contains("player") -> Folders.MEDIA
            p.contains("calculator") || p.contains("clock") || p.contains("calendar") ||
                p.contains("files") || p.contains("notes") -> Folders.UTILITIES
            else -> Folders.OTHER
        }
    }

    fun folderOf(a: AppEntry): Int = assign[a.key] ?: Folders.OTHER

    fun inFolder(f: Int): List<AppEntry> {
        if (f == Folders.RECENT) return recent.mapNotNull { byKey[it] }
        return apps.filter { assign[it.key] == f }
    }

    fun move(a: AppEntry, folder: Int) {
        assign[a.key] = folder
        save()
    }

    fun touchRecent(key: String) {
        recent.remove(key)
        recent.add(0, key)
        while (recent.size > 8) recent.removeAt(recent.size - 1)
        save()
    }

    fun isFresh(key: String): Boolean = fresh.contains(key)

    fun folderHasFresh(f: Int): Boolean {
        if (fresh.isEmpty()) return false
        for (k in fresh) if (assign[k] == f) return true
        return false
    }

    fun clearFresh(f: Int) {
        val gone = fresh.filter { assign[it] == f }
        if (gone.isNotEmpty()) {
            fresh.removeAll(gone.toSet())
            save()
        }
    }

    fun icon(a: AppEntry): Drawable {
        return iconCache.getOrPut(a.key) {
            try {
                ctx.packageManager.getActivityIcon(ComponentName(a.pkg, a.cls))
            } catch (e: Exception) {
                ctx.packageManager.defaultActivityIcon
            }
        }
    }

    /** Puts every app back where auto-sort would place it. */
    fun resetLayout() {
        assign.clear()
        fresh.clear()
        known.clear()
        recent.clear()
        refresh()
        save()
    }

    fun exportJson(): String {
        val o = JSONObject()
        val a = JSONObject()
        for ((k, v) in assign) a.put(k, v)
        o.put("version", 1)
        o.put("assign", a)
        val r = JSONArray()
        for (x in recent) r.put(x)
        o.put("recent", r)
        return o.toString(2)
    }

    fun importJson(text: String): Boolean {
        return try {
            val o = JSONObject(text)
            val a = o.getJSONObject("assign")
            for (k in a.keys()) assign[k] = a.getInt(k)
            recent.clear()
            val r = o.optJSONArray("recent")
            if (r != null) for (i in 0 until r.length()) recent.add(r.getString(i))
            refresh()
            save()
            true
        } catch (e: Exception) {
            false
        }
    }
}
