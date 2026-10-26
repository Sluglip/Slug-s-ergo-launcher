package com.slug.ergolauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The home screen. The system wallpaper shows through (see AppTheme), two widget slots sit at the top,
 * and the wheel is drawn over everything in the bottom corner.
 */
class LauncherActivity : Activity(), WheelView.Host {

    companion object {
        private const val REQ_BIND = 41
        private const val REQ_CONFIG = 42
        private const val HOST_ID = 1024
    }

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepo
    private lateinit var wheel: WheelView
    private lateinit var awm: AppWidgetManager
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var slotsBox: LinearLayout
    private lateinit var search: EditText
    private val slots = ArrayList<SlotView>()

    private var pendingSlot = -1
    private var pendingId = -1

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepo(this, prefs)
        repo.refresh()
        awm = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, HOST_ID)

        val root = FrameLayout(this)
        root.fitsSystemWindows = true

        slotsBox = LinearLayout(this)
        slotsBox.orientation = LinearLayout.VERTICAL
        for (i in 0..1) {
            val s = SlotView(i)
            slots.add(s)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(if (i == 0) 150 else 170))
            lp.setMargins(dp(20), dp(16), dp(20), 0)
            slotsBox.addView(s, lp)
        }
        root.addView(
            slotsBox,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

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
        repo.refresh()
        wheel.reload()
        for (s in slots) s.bindWidget()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while the wheel is open closes it.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) wheel.collapse()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // A launcher never closes. Back steps the wheel out one level.
        wheel.onBack()
    }

    // =====================================================================================
    // WheelView.Host
    // =====================================================================================

    override fun onSearchMode(on: Boolean) {
        slotsBox.visibility = if (on) View.INVISIBLE else View.VISIBLE
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
    // widgets
    // =====================================================================================

    /** One widget slot. Hold it (time set in settings) to choose, change or remove its widget. */
    inner class SlotView(private val index: Int) : FrameLayout(this@LauncherActivity) {

        private val hint = TextView(this@LauncherActivity)
        private var hostView: View? = null
        private var downX = 0f
        private var downY = 0f
        private var fired = false
        private val slop = ViewConfiguration.get(this@LauncherActivity).scaledTouchSlop.toFloat()
        private val longRun = Runnable {
            fired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            pickWidget(index)
        }

        init {
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.argb(199, 24, 28, 36))
                setStroke(dp(1), Color.parseColor("#2A303C"))
            }
            hint.text = "Hold to add a widget"
            hint.setTextColor(Color.parseColor("#B4BCCB"))
            hint.textSize = 14f
            hint.gravity = Gravity.CENTER
            addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        private fun track(ev: MotionEvent) {
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
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            track(ev)
            return fired
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            track(ev)
            return true
        }

        fun bindWidget() {
            hostView?.let { removeView(it) }
            hostView = null
            val id = prefs.widgetId(index)
            if (id == -1) {
                hint.visibility = View.VISIBLE
                return
            }
            val info = awm.getAppWidgetInfo(id)
            if (info == null) {
                prefs.setWidgetId(index, -1)
                hint.visibility = View.VISIBLE
                return
            }
            hint.visibility = View.GONE
            val hv: AppWidgetHostView = widgetHost.createView(this@LauncherActivity, id, info)
            hv.setAppWidget(id, info)
            addView(hv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            hostView = hv
            hv.post {
                val d = resources.displayMetrics.density
                val w = (hv.width / d).toInt()
                val h = (hv.height / d).toInt()
                hv.updateAppWidgetSize(null, w, h, w, h)
            }
        }
    }

    private fun pickWidget(slot: Int) {
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
        val b = AlertDialog.Builder(this)
            .setTitle("Choose a widget")
            .setItems(names.toTypedArray()) { _, which -> bindAndAdd(slot, providers[which]) }
            .setNegativeButton("Cancel", null)
        if (prefs.widgetId(slot) != -1) {
            b.setNeutralButton("Remove widget") { _, _ ->
                widgetHost.deleteAppWidgetId(prefs.widgetId(slot))
                prefs.setWidgetId(slot, -1)
                slots[slot].bindWidget()
            }
        }
        b.show()
    }

    private fun bindAndAdd(slot: Int, info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingSlot = slot
        pendingId = id
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrAdd(slot, id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureOrAdd(slot: Int, id: Int) {
        val info = awm.getAppWidgetInfo(id)
        pendingSlot = slot
        pendingId = id
        if (info?.configure != null) {
            widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
        } else {
            setSlotWidget(slot, id)
        }
    }

    private fun setSlotWidget(slot: Int, id: Int) {
        val old = prefs.widgetId(slot)
        if (old != -1 && old != id) widgetHost.deleteAppWidgetId(old)
        prefs.setWidgetId(slot, id)
        slots[slot].bindWidget()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_BIND) {
            if (resultCode == RESULT_OK) configureOrAdd(pendingSlot, pendingId)
            else widgetHost.deleteAppWidgetId(pendingId)
        } else if (requestCode == REQ_CONFIG) {
            if (resultCode == RESULT_OK) setSlotWidget(pendingSlot, pendingId)
            else widgetHost.deleteAppWidgetId(pendingId)
        }
    }
}
