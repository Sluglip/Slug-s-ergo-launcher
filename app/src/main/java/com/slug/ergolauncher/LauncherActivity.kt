package com.slug.ergolauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** One placed widget. Position and size are in dp, measured from the top-left of the widget canvas. */
class WEntry(val id: Int, var x: Int, var y: Int, var w: Int, var h: Int)

/**
 * The home screen. The system wallpaper shows through (see AppTheme). The whole screen behind the
 * wheel is a free-form widget canvas, and the wheel is drawn over everything in the bottom corner.
 */
class LauncherActivity : Activity(), WheelView.Host {

    companion object {
        private const val REQ_BIND = 41
        private const val REQ_CONFIG = 42
        private const val HOST_ID = 1024
        private const val MIN_DP = 80
    }

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepo
    private lateinit var wheel: WheelView
    private lateinit var awm: AppWidgetManager
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var canvasView: WidgetCanvas
    private lateinit var hint: TextView
    private lateinit var search: EditText

    private val entries = ArrayList<WEntry>()
    private val boxes = ArrayList<WidgetBox>()
    private var editing: WidgetBox? = null

    private var appsDirty = true
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appsDirty = true
        }
    }

    private var pendingId = -1
    private var pendingX = 0
    private var pendingY = 0

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepo(this, prefs)
        repo.refresh()
        awm = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, HOST_ID)

        val root = FrameLayout(this)
        root.fitsSystemWindows = true

        canvasView = WidgetCanvas()
        root.addView(
            canvasView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        hint = TextView(this)
        hint.text = "Hold anywhere on the screen to add a widget"
        hint.setTextColor(Color.parseColor("#ECEFF4"))
        hint.textSize = 14f
        hint.gravity = Gravity.CENTER
        hint.setShadowLayer(4f * density, 0f, density, Color.argb(200, 0, 0, 0))
        val hlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        hlp.topMargin = dp(80)
        root.addView(hint, hlp)

        search = EditText(this)
        search.hint = "Search apps"
        search.setHintTextColor(Color.parseColor("#B4BCCB"))
        search.setTextColor(Color.parseColor("#ECEFF4"))
        search.textSize = 16f
        search.setSingleLine()
        search.setPadding(dp(20), 0, dp(20), 0)
        search.background = GradientDrawable().apply {
            cornerRadius = dp(26).toFloat()
            setColor(Color.argb(235, 28, 32, 41))
            setStroke(dp(1), Color.parseColor("#3A4252"))
        }
        search.visibility = View.GONE
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                wheel.setQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val slp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        slp.setMargins(dp(20), dp(16), dp(20), 0)
        root.addView(search, slp)

        wheel = WheelView(this, prefs, repo)
        wheel.host = this
        root.addView(
            wheel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        setContentView(root)
        loadEntries()

        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_CHANGED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        registerReceiver(pkgReceiver, f)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pkgReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            widgetHost.startListening()
        } catch (e: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            widgetHost.stopListening()
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        repo.loadState()
        // Rescanning every app is slow, so only do it when something was installed or removed.
        if (appsDirty) {
            repo.refresh()
            appsDirty = false
        }
        wheel.reload()
        if (editing == null) buildBoxes()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home closes the wheel and any widget editing.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            wheel.collapse()
            exitEdit()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher never closes. Back leaves widget editing first, then steps the wheel out.
        if (editing != null) exitEdit() else wheel.onBack()
    }

    // =====================================================================================
    // WheelView.Host
    // =====================================================================================

    override fun onSearchMode(on: Boolean) {
        if (on) exitEdit()
        canvasView.visibility = if (on) View.INVISIBLE else View.VISIBLE
        hint.visibility = if (on || entries.isNotEmpty()) View.GONE else View.VISIBLE
        search.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            search.setText("")
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(search.windowToken, 0)
        }
    }

    override fun onSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onLaunch(app: AppEntry) {
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)
        i.component = ComponentName(app.pkg, app.cls)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("Couldn't open ${app.label}")
        }
    }

    override fun onUninstall(app: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)))
        } catch (e: Exception) {
            toast("Couldn't start uninstall for ${app.label}")
        }
    }

    override fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // =====================================================================================
    // widget canvas: storage
    // =====================================================================================

    private fun loadEntries() {
        entries.clear()
        val raw = prefs.widgetsJson
        if (raw.isNotEmpty()) {
            try {
                val a = JSONArray(raw)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    entries.add(WEntry(o.getInt("id"), o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h")))
                }
            } catch (e: Exception) {
            }
        } else {
            // Bring over the two widgets from version 1, if there were any.
            val sw = (resources.displayMetrics.widthPixels / density).toInt()
            val id0 = prefs.widgetId(0)
            val id1 = prefs.widgetId(1)
            if (id0 != -1) entries.add(WEntry(id0, 20, 16, sw - 40, 150))
            if (id1 != -1) entries.add(WEntry(id1, 20, 182, sw - 40, 170))
            if (entries.isNotEmpty()) saveEntries()
        }
    }

    private fun saveEntries() {
        val a = JSONArray()
        for (e in entries) {
            a.put(JSONObject().put("id", e.id).put("x", e.x).put("y", e.y).put("w", e.w).put("h", e.h))
        }
        prefs.widgetsJson = a.toString()
    }

    private fun canvasWdp(): Int = max(MIN_DP, (canvasView.width / density).toInt())
    private fun canvasHdp(): Int = max(MIN_DP, (canvasView.height / density).toInt())

    // =====================================================================================
    // widget canvas: views
    // =====================================================================================

    private fun buildBoxes() {
        for (b in boxes) canvasView.removeView(b)
        boxes.clear()
        editing = null
        val dead = ArrayList<WEntry>()
        for (e in entries) {
            val info = awm.getAppWidgetInfo(e.id)
            if (info == null) {
                dead.add(e)
                continue
            }
            val b = WidgetBox(e, info)
            boxes.add(b)
            canvasView.addView(b, b.params())
        }
        if (dead.isNotEmpty()) {
            entries.removeAll(dead.toSet())
            saveEntries()
        }
        hint.visibility = if (entries.isEmpty() && canvasView.visibility == View.VISIBLE) View.VISIBLE else View.GONE
    }

    private fun enterEdit(b: WidgetBox) {
        editing?.let {
            it.edit = false
            it.invalidate()
        }
        editing = b
        b.edit = true
        b.bringToFront()
        b.invalidate()
    }

    private fun exitEdit() {
        val b = editing ?: return
        b.edit = false
        b.invalidate()
        editing = null
        saveEntries()
    }

    private fun removeWidget(b: WidgetBox) {
        widgetHost.deleteAppWidgetId(b.e.id)
        entries.remove(b.e)
        boxes.remove(b)
        canvasView.removeView(b)
        if (editing === b) editing = null
        saveEntries()
        hint.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    /** The empty canvas: hold to add a widget, tap to leave editing. */
    inner class WidgetCanvas : FrameLayout(this@LauncherActivity) {
        private var downX = 0f
        private var downY = 0f
        private var fired = false
        private val slop = ViewConfiguration.get(this@LauncherActivity).scaledTouchSlop.toFloat()
        private val longRun = Runnable {
            fired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            exitEdit()
            pickWidget((downX / density).toInt(), (downY / density).toInt())
        }

        init {
            isClickable = true
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    downX = ev.x
                    downY = ev.y
                    removeCallbacks(longRun)
                    postDelayed(longRun, (prefs.holdWidget * 1000).toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(ev.x - downX, ev.y - downY) > slop) removeCallbacks(longRun)
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longRun)
                    if (!fired) exitEdit()
                }
                MotionEvent.ACTION_CANCEL -> removeCallbacks(longRun)
            }
            return true
        }
    }

    /**
     * One placed widget. Hold it to edit: drag to move, drag a corner to resize, tap the X to remove.
     * Tap empty space (or press Back) to finish.
     */
    inner class WidgetBox(val e: WEntry, info: AppWidgetProviderInfo) : FrameLayout(this@LauncherActivity) {

        var edit = false
        private val hostView: AppWidgetHostView
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val slop = ViewConfiguration.get(this@LauncherActivity).scaledTouchSlop.toFloat()
        private var fired = false
        private var downX = 0f
        private var downY = 0f
        private var mode = 0
        private var sx = 0
        private var sy = 0
        private var sw = 0
        private var sh = 0
        private val longRun = Runnable {
            fired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            enterEdit(this)
        }

        init {
            hostView = widgetHost.createView(this@LauncherActivity, e.id, info)
            hostView.setAppWidget(e.id, info)
            addView(hostView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            post { pushSize() }
        }

        fun params(): FrameLayout.LayoutParams {
            val lp = FrameLayout.LayoutParams(dp(e.w), dp(e.h))
            lp.leftMargin = dp(e.x)
            lp.topMargin = dp(e.y)
            return lp
        }

        private fun applyLayout() {
            layoutParams = params()
        }

        private fun pushSize() {
            try {
                hostView.updateAppWidgetSize(null, e.w, e.h, e.w, e.h)
            } catch (ex: Exception) {
            }
        }

        override fun dispatchDraw(c: Canvas) {
            super.dispatchDraw(c)
            if (!edit) return
            val d = density
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * d
            paint.color = prefs.color
            c.drawRect(d, d, width - d, height - d, paint)
            val xs = floatArrayOf(0f, width.toFloat())
            val ys = floatArrayOf(0f, height.toFloat())
            for (x in xs) for (y in ys) {
                paint.style = Paint.Style.FILL
                paint.color = prefs.color
                c.drawCircle(x.coerceIn(10 * d, width - 10 * d), y.coerceIn(10 * d, height - 10 * d), 9 * d, paint)
                paint.style = Paint.Style.STROKE
                paint.color = Color.WHITE
                c.drawCircle(x.coerceIn(10 * d, width - 10 * d), y.coerceIn(10 * d, height - 10 * d), 9 * d, paint)
            }
            val cx = width / 2f
            val cy = 20 * d
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#C62F34")
            c.drawCircle(cx, cy, 15 * d, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f * d
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Color.WHITE
            c.drawLine(cx - 5 * d, cy - 5 * d, cx + 5 * d, cy + 5 * d, paint)
            c.drawLine(cx + 5 * d, cy - 5 * d, cx - 5 * d, cy + 5 * d, paint)
            paint.strokeCap = Paint.Cap.BUTT
        }

        private fun hit(x: Float, y: Float): Int {
            val d = density
            if (hypot(x - width / 2f, y - 20 * d) <= 20 * d) return 9
            val r = 30 * d
            val left = x < r
            val right = x > width - r
            val top = y < r
            val bottom = y > height - r
            if (left && top) return 2
            if (right && top) return 3
            if (left && bottom) return 4
            if (right && bottom) return 5
            return 1
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (edit) return true
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    fired = false
                    downX = ev.x
                    downY = ev.y
                    removeCallbacks(longRun)
                    postDelayed(longRun, (prefs.holdWidget * 1000).toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(ev.x - downX, ev.y - downY) > slop) removeCallbacks(longRun)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longRun)
            }
            return fired
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (!edit) return true
            val d = density
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    sx = e.x
                    sy = e.y
                    sw = e.w
                    sh = e.h
                    mode = hit(ev.x, ev.y)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ((ev.rawX - downX) / d).toInt()
                    val dy = ((ev.rawY - downY) / d).toInt()
                    val cw = canvasWdp()
                    val ch = canvasHdp()
                    when (mode) {
                        1 -> {
                            e.x = (sx + dx).coerceIn(0, max(0, cw - sw))
                            e.y = (sy + dy).coerceIn(0, max(0, ch - sh))
                        }
                        2, 3, 4, 5 -> {
                            val leftEdge = mode == 2 || mode == 4
                            val topEdge = mode == 2 || mode == 3
                            if (leftEdge) {
                                val nx = (sx + dx).coerceIn(0, sx + sw - MIN_DP)
                                e.x = nx
                                e.w = sx + sw - nx
                            } else {
                                e.w = (sw + dx).coerceIn(MIN_DP, max(MIN_DP, cw - sx))
                            }
                            if (topEdge) {
                                val ny = (sy + dy).coerceIn(0, sy + sh - MIN_DP)
                                e.y = ny
                                e.h = sy + sh - ny
                            } else {
                                e.h = (sh + dy).coerceIn(MIN_DP, max(MIN_DP, ch - sy))
                            }
                        }
                    }
                    if (mode in 1..5) {
                        applyLayout()
                        invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == 9 && hit(ev.x, ev.y) == 9) {
                        removeWidget(this)
                    } else if (mode in 1..5) {
                        saveEntries()
                        pushSize()
                    }
                    mode = 0
                }
                MotionEvent.ACTION_CANCEL -> {
                    mode = 0
                    saveEntries()
                }
            }
            return true
        }
    }

    // =====================================================================================
    // widget canvas: adding
    // =====================================================================================

    private fun pickWidget(x: Int, y: Int) {
        val pm = packageManager
        val providers: List<AppWidgetProviderInfo> = awm.installedProviders
        val names = providers.map {
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(it.provider.packageName, 0)).toString()
            } catch (e: Exception) {
                it.provider.packageName
            }
            val wName = it.loadLabel(pm) ?: appName
            if (wName == appName) wName else "$wName ($appName)"
        }
        AlertDialog.Builder(this)
            .setTitle("Add a widget")
            .setItems(names.toTypedArray()) { _, which -> bindAndAdd(providers[which], x, y) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun bindAndAdd(info: AppWidgetProviderInfo, x: Int, y: Int) {
        val id = widgetHost.allocateAppWidgetId()
        pendingId = id
        pendingX = x
        pendingY = y
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrAdd(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureOrAdd(id: Int) {
        pendingId = id
        val info = awm.getAppWidgetInfo(id)
        if (info?.configure != null) {
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
        } else {
            addEntry(id)
        }
    }

    private fun addEntry(id: Int) {
        val info = awm.getAppWidgetInfo(id) ?: return
        val cw = canvasWdp()
        val ch = canvasHdp()
        val w = (info.minWidth / density).toInt().coerceIn(MIN_DP, max(MIN_DP, cw - 16))
        val h = (info.minHeight / density).toInt().coerceIn(MIN_DP, max(MIN_DP, ch - 16))
        val x = (pendingX - w / 2).coerceIn(0, max(0, cw - w))
        val y = (pendingY - h / 2).coerceIn(0, max(0, ch - h))
        val e = WEntry(id, x, y, w, h)
        entries.add(e)
        saveEntries()
        val b = WidgetBox(e, info)
        boxes.add(b)
        canvasView.addView(b, b.params())
        hint.visibility = View.GONE
        enterEdit(b)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_BIND) {
            if (resultCode == RESULT_OK) configureOrAdd(pendingId)
            else widgetHost.deleteAppWidgetId(pendingId)
        } else if (requestCode == REQ_CONFIG) {
            if (resultCode == RESULT_OK) addEntry(pendingId)
            else widgetHost.deleteAppWidgetId(pendingId)
        }
    }
}
