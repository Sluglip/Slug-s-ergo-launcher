package com.slug.ergolauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The quarter wheel. It lives in the bottom corner and has three stages:
 * 0 = start button, 1 = folder ring, 2 = app ring.
 * Everything is drawn here and rings scroll by sweeping a thumb around the corner pivot.
 */
class WheelView(
    context: Context,
    private val prefs: Prefs,
    private val repo: AppRepo
) : View(context) {

    interface Host {
        fun onSearchMode(on: Boolean)
        fun onSettings()
        fun onRemoveFavorite(app: AppEntry, done: () -> Unit)
        fun onLaunch(app: AppEntry)
        fun onUninstall(app: AppEntry)
        fun toast(msg: String)
    }

    private class Hit(val kind: Int, val idx: Int, val x: Float, val y: Float, val r: Float)

    var host: Host? = null

    private val u = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val uiHandler = Handler(Looper.getMainLooper())

    // ---- state ----
    var stage = 0
        private set
    private var cat = 0
    private var scroll = 0f
    private var cscroll = 0f
    private var query = ""
    private var moving: AppEntry? = null
    private var from = 0
    private var delArm = false

    // ---- animated geometry ----
    private var bigR = 0f
    private var btnR = 0f
    private var fade = 1f
    private var geoAnim: ValueAnimator? = null

    // ---- touch tracking ----
    private val hits = ArrayList<Hit>()
    private var downHit: Hit? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longFired = false
    private var dragging = false
    private var dragA0 = 0f
    private var dragS0 = 0f
    private var lastTick = -1

    // ---- press-and-hold feedback ----
    private var holdRunnable: Runnable? = null
    private var pressing = false
    private var pressStart = 0L
    private var pressHold = 1L
    private var pressKey: String? = null
    private var pressBack = false

    private val disarm = Runnable {
        delArm = false
        invalidate()
    }

    // ---- paints and glyphs ----
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private val cBubble = Color.parseColor("#2B3240")
    private val cBorder = Color.parseColor("#3A4252")
    private val cLight = Color.parseColor("#ECEFF4")
    private val cGreen = Color.parseColor("#3DDC84")
    private val cRed = Color.parseColor("#C62F34")
    private val cDark = Color.parseColor("#12141A")

    private fun glyph(id: Int): Drawable = context.getDrawable(id)!!.mutate()

    private val catIcons: List<Drawable> = Folders.ICONS.map { glyph(it) }
    private val icGrid = glyph(R.drawable.ic_grid)
    private val icBack = glyph(R.drawable.ic_back)
    private val icTrash = glyph(R.drawable.ic_trash)

    companion object {
        private const val START = 12f
        private const val CSTEP = 26f
        private const val MINA = -2f
        private const val MAXA = 94f
    }

    // =====================================================================================
    // geometry helpers
    // =====================================================================================

    private fun pivX(): Float = if (prefs.left) 0f else width.toFloat()
    private fun pivY(): Float = height.toFloat()

    private fun px(r: Float, t: Float): Float {
        val a = Math.toRadians(t.toDouble())
        val d = (r * cos(a)).toFloat()
        return if (prefs.left) pivX() + d else pivX() - d
    }

    private fun py(r: Float, t: Float): Float =
        pivY() - (r * sin(Math.toRadians(t.toDouble()))).toFloat()

    private fun ang(x: Float, y: Float): Float {
        val dx = if (prefs.left) x - pivX() else pivX() - x
        return Math.toDegrees(atan2((pivY() - y).toDouble(), dx.toDouble())).toFloat()
    }

    private fun targetBig(s: Int): Float = (if (s == 0) 150f else 320f) * prefs.wheelSize * u
    private fun targetBtn(s: Int): Float = (if (s == 0) 150f else 80f) * prefs.wheelSize * u

    private fun lo(n: Int, step: Float): Float = min(0f, 82f - (START + step * (n - 1)))

    /** Angle between neighbouring apps. A single lane needs more room than a zigzag. */
    private fun astep(): Float = when (prefs.rows) {
        1 -> 15f
        2, 3 -> 13f
        else -> 14f
    }

    private fun ringIds(): List<Int> = if (moving != null) Folders.RING_MOVE else Folders.RING

    private fun catCount(): Int = ringIds().size

    /** Outermost lane radius in dp, widened if the chosen number of rows needs more room. */
    private fun reachDp(): Float = max(prefs.reach.toFloat(), 110f + 30f * (prefs.rows - 1))

    /** Radius in dp of the given lane index (0 = outermost) for the current number of rows. */
    private fun laneR(lane: Int): Float {
        val n = prefs.rows
        if (n == 1) return reachDp()
        val gap = min(54f, (reachDp() - 110f) / (n - 1))
        return reachDp() - gap * lane
    }

    /** Triangle wave so apps zigzag: 0,1,2,1,0,1,2... */
    private fun laneOf(i: Int): Int {
        val n = prefs.rows
        if (n == 1) return 0
        val period = 2 * (n - 1)
        val m = i % period
        return if (m < n) m else period - m
    }

    private fun curList(): List<AppEntry> {
        if (cat == Folders.RECENT && query.isNotBlank()) {
            val q = query.trim().lowercase()
            return repo.apps.filter {
                repo.folderOf(it) != Folders.HIDDEN && it.label.lowercase().contains(q)
            }
        }
        return repo.inFolder(cat)
    }

    private fun clampFor(s: Int, v: Float): Float {
        val n = if (s == 1) catCount() else curList().size
        return max(lo(n, if (s == 1) CSTEP else astep()), min(0f, v))
    }

    private fun lum(c: Int): Float {
        fun ch(v: Int): Float {
            val x = v / 255f
            return if (x <= 0.03928f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * ch(Color.red(c)) + 0.7152f * ch(Color.green(c)) + 0.0722f * ch(Color.blue(c))
    }

    private fun haptic(kind: Int) {
        if (prefs.haptics) performHapticFeedback(kind)
    }

    // =====================================================================================
    // public controls
    // =====================================================================================

    /** Call after settings change or when the activity comes back to the front. */
    fun reload() {
        geoAnim?.cancel()
        bigR = targetBig(stage)
        btnR = targetBtn(stage)
        fade = 1f
        invalidate()
    }

    fun setQuery(q: String) {
        query = q
        scroll = 0f
        invalidate()
    }

    fun collapse() {
        moving = null
        delArm = false
        if (stage != 0) setStage(0)
    }

    /** Returns true when the wheel used the back press. */
    fun onBack(): Boolean {
        if (stage == 0) return false
        backTap()
        return true
    }

    private fun setStage(s: Int) {
        stage = s
        host?.onSearchMode(s == 2 && cat == Folders.RECENT)
        animateGeometry()
        invalidate()
    }

    private fun animateGeometry() {
        geoAnim?.cancel()
        val b0 = bigR
        val n0 = btnR
        val b1 = targetBig(stage)
        val n1 = targetBtn(stage)
        if (prefs.reduceMotion || width == 0) {
            bigR = b1
            btnR = n1
            fade = 1f
            invalidate()
            return
        }
        fade = 0f
        geoAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                bigR = b0 + (b1 - b0) * t
                btnR = n0 + (n1 - n0) * t
                fade = min(1f, t * 1.6f)
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bigR = targetBig(stage)
        btnR = targetBtn(stage)
    }

    // =====================================================================================
    // drawing
    // =====================================================================================

    override fun onDraw(canvas: Canvas) {
        hits.clear()
        val k = prefs.wheelSize
        val accent = prefs.color
        val fg = if (lum(accent) > 0.35f) cDark else Color.WHITE

        if (stage > 0) {
            fill.color = Color.argb((prefs.wheelOpacity * 255).roundToInt(), 24, 28, 36)
            canvas.drawCircle(pivX(), pivY(), bigR, fill)
            stroke.color = Color.parseColor("#2A303C")
            stroke.strokeWidth = u
            canvas.drawCircle(pivX(), pivY(), bigR, stroke)
            drawScrollbar(canvas, k, accent)
            if (stage == 1) drawCats(canvas, k, accent) else drawApps(canvas, k, accent)
            drawSideButtons(canvas)
        }
        drawBackButton(canvas, accent, fg)

        if (pressing) postInvalidateOnAnimation()
    }

    private fun pressProgress(): Float =
        ((SystemClock.uptimeMillis() - pressStart).toFloat() / pressHold).coerceIn(0f, 1f)

    private fun a255(a: Float): Int = (a * 255).roundToInt().coerceIn(0, 255)

    private fun drawGlyph(c: Canvas, d: Drawable, cx: Float, cy: Float, size: Float, color: Int, a: Float) {
        d.setBounds(
            (cx - size / 2).roundToInt(), (cy - size / 2).roundToInt(),
            (cx + size / 2).roundToInt(), (cy + size / 2).roundToInt()
        )
        d.setTint(color)
        d.alpha = a255(a)
        d.draw(c)
    }

    private fun drawBubble(c: Canvas, cx: Float, cy: Float, r: Float, border: Int, bg: Int, a: Float) {
        fill.color = bg
        fill.alpha = a255(a)
        c.drawCircle(cx, cy, r, fill)
        stroke.color = border
        stroke.alpha = a255(a)
        stroke.strokeWidth = 2 * u
        c.drawCircle(cx, cy, r - u, stroke)
    }

    private fun drawText(c: Canvas, s: String, cx: Float, baseline: Float, sizeDp: Float, a: Float) {
        text.textSize = sizeDp * u
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        text.color = cLight
        text.alpha = a255(a)
        text.setShadowLayer(3 * u, 0f, u, Color.argb(180, 0, 0, 0))
        c.drawText(s, cx, baseline, text)
        text.clearShadowLayer()
    }

    private fun drawNewPill(c: Canvas, left: Float, top: Float, a: Float) {
        text.textSize = 10 * u
        text.typeface = Typeface.DEFAULT_BOLD
        val w = text.measureText("NEW") + 12 * u
        val h = 16 * u
        rect.set(left, top, left + w, top + h)
        fill.color = cGreen
        fill.alpha = a255(a)
        c.drawRoundRect(rect, h / 2, h / 2, fill)
        text.color = Color.parseColor("#08240F")
        text.alpha = a255(a)
        c.drawText("NEW", left + w / 2, top + h - 4.5f * u, text)
    }

    private fun arcRect(r: Float): RectF {
        rect.set(pivX() - r, pivY() - r, pivX() + r, pivY() + r)
        return rect
    }

    /** Draws an arc between two ring angles (0 = along the bottom edge, 90 = up the side edge). */
    private fun drawRingArc(c: Canvas, r: Float, a1: Float, a2: Float, p: Paint) {
        val start = if (prefs.left) -a2 else 180f + a1
        c.drawArc(arcRect(r), start, a2 - a1, false, p)
    }

    private fun drawScrollbar(c: Canvas, k: Float, accent: Int) {
        val n = if (stage == 1) catCount() else curList().size
        val step = if (stage == 1) CSTEP else astep()
        val loV = lo(n, step)
        val sc = if (stage == 1) cscroll else scroll
        val len = if (loV < 0) (78f * 90f / (90f - loV)).coerceIn(14f, 78f) else 78f
        val pos = if (loV < 0) (sc / loV).coerceIn(0f, 1f) else 0f
        val t0 = 6f + pos * (78f - len)
        val r = (320f * k - 10f) * u

        stroke.strokeWidth = 5 * u
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = Color.argb(56, 236, 239, 244)
        drawRingArc(c, r, 6f, 84f, stroke)
        stroke.color = accent
        drawRingArc(c, r, t0, t0 + len, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.alpha = 255
    }

    private fun drawCats(c: Canvas, k: Float, accent: Int) {
        val isz = prefs.iconSize * u
        val half = isz / 2
        val count = catCount()
        val ids = ringIds()
        for (i in 0 until count) {
            val id = ids[i]
            val a = START + CSTEP * i + cscroll
            if (a <= MINA || a >= MAXA) continue
            val r = (reachDp() - 32f) * k * u
            val cx = px(r, a)
            val cy = py(r, a)
            val border = if (moving != null && id == from) accent else cBorder
            drawBubble(c, cx, cy, half, border, cBubble, fade)
            drawGlyph(c, catIcons[id], cx, cy, isz * 0.43f, cLight, fade)
            if (prefs.labels != 0) drawText(c, Folders.NAMES[id], cx, cy - half - 6 * u, 11f, fade)
            if (id != Folders.HIDDEN && repo.folderHasFresh(id)) {
                drawNewPill(c, cx + half * 0.35f, cy - half - 10 * u, fade)
            }
            hits.add(Hit(1, id, cx, cy, half))
        }
    }

    private fun drawApps(c: Canvas, k: Float, accent: Int) {
        val list = curList()
        val isz = prefs.iconSize * u
        val half = isz / 2
        for (i in list.indices) {
            val app = list[i]
            val a = START + astep() * i + scroll
            if (a <= MINA || a >= MAXA) continue
            val r = laneR(laneOf(i)) * k * u
            val cx = px(r, a)
            val cy = py(r, a)
            val pressed = pressing && pressKey == app.key
            val s = if (pressed) 1f + 0.3f * pressProgress() else 1f
            c.save()
            c.scale(s, s, cx, cy)
            drawBubble(c, cx, cy, half, if (pressed) accent else cBorder, cBubble, fade)
            val ic = repo.icon(app)
            val sz = isz * 0.74f
            ic.setBounds(
                (cx - sz / 2).roundToInt(), (cy - sz / 2).roundToInt(),
                (cx + sz / 2).roundToInt(), (cy + sz / 2).roundToInt()
            )
            ic.alpha = a255(fade)
            ic.draw(c)
            c.restore()
            if (repo.isFresh(app.key)) {
                val dx = cx + half * 0.55f
                val dy = cy - half + 2 * u
                fill.color = Color.parseColor("#12141A")
                fill.alpha = a255(fade)
                c.drawCircle(dx, dy, 7 * u, fill)
                fill.color = cGreen
                fill.alpha = a255(fade)
                c.drawCircle(dx, dy, 5 * u, fill)
            }
            if (prefs.labels == 2) {
                val label = if (app.label.length > 12) app.label.substring(0, 11) + "…" else app.label
                drawText(c, label, cx, cy + half + 13 * u, 10f, fade)
            }
            hits.add(Hit(2, i, cx, cy, half))
        }
    }

    private fun drawSideButtons(c: Canvas) {
        val cx = if (prefs.left) width - 56 * u else 56 * u
        val cy = pivY() - 112 * u
        val r = 32 * u
        val m = moving
        if (stage == 1 && m != null) {
            drawBubble(c, cx, cy, r, if (delArm) Color.WHITE else cRed, if (delArm) cRed else cBubble, 1f)
            drawGlyph(c, icTrash, cx, cy, 26 * u, Color.WHITE, 1f)
            drawText(c, if (delArm) "Tap again to uninstall" else "Uninstall", cx, cy + r + 16 * u, 11f, 1f)
            hits.add(Hit(3, 0, cx, cy, r))
        }
    }

    private fun drawBackButton(c: Canvas, accent: Int, fg: Int) {
        val r = btnR
        c.save()
        if (pressBack && pressing) {
            val s = 1f + 0.15f * pressProgress()
            c.scale(s, s, pivX(), pivY())
        }
        fill.color = accent
        fill.alpha = 255
        c.drawCircle(pivX(), pivY(), r, fill)
        stroke.color = Color.parseColor("#343C4B")
        stroke.strokeWidth = u
        stroke.alpha = 255
        c.drawCircle(pivX(), pivY(), r, stroke)
        val off = 0.4243f * r
        val ix = if (prefs.left) pivX() + off else pivX() - off
        val iy = pivY() - off
        drawGlyph(c, if (stage == 0) icGrid else icBack, ix, iy, (if (stage == 0) 30f else 26f) * u, fg, 1f)
        c.restore()
        hits.add(Hit(5, 0, pivX(), pivY(), r))
    }

    // =====================================================================================
    // touch
    // =====================================================================================

    private fun hitAt(x: Float, y: Float): Hit? {
        for (i in hits.indices.reversed()) {
            val h = hits[i]
            if (hypot(x - h.x, y - h.y) <= h.r + 4 * u) return h
        }
        return null
    }

    private fun scheduleHold(ms: Long, action: () -> Unit) {
        cancelHold()
        pressStart = SystemClock.uptimeMillis()
        pressHold = max(1L, ms)
        pressing = true
        val r = Runnable {
            pressing = false
            pressKey = null
            pressBack = false
            longFired = true
            dragging = false
            action()
            invalidate()
        }
        holdRunnable = r
        uiHandler.postDelayed(r, pressHold)
        postInvalidateOnAnimation()
    }

    private fun cancelHold() {
        holdRunnable?.let { uiHandler.removeCallbacks(it) }
        holdRunnable = null
        pressing = false
        pressKey = null
        pressBack = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val h = hitAt(e.x, e.y)
                if (h == null) {
                    // Outside the wheel: let widgets underneath have the touch.
                    val inside = stage >= 1 && hypot(e.x - pivX(), e.y - pivY()) <= bigR
                    if (!inside) return false
                }
                downHit = h
                downX = e.x
                downY = e.y
                moved = false
                longFired = false
                dragging = stage >= 1 && (h == null || h.kind == 1 || h.kind == 2)
                if (dragging) {
                    dragA0 = ang(e.x, e.y)
                    dragS0 = if (stage == 1) cscroll else scroll
                    lastTick = -1
                }
                if (h != null) {
                    if (h.kind == 2 && stage == 2 && cat != Folders.RECENT && moving == null) {
                        val list = curList()
                        if (h.idx < list.size) {
                            val app = list[h.idx]
                            scheduleHold((prefs.holdMove * 1000).toLong()) { startMove(app) }
                            pressKey = app.key
                        }
                    } else if (h.kind == 5 && stage == 1 && moving == null) {
                        scheduleHold((prefs.holdHidden * 1000).toLong()) { openHidden() }
                        pressBack = true
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downX, e.y - downY) > slop) {
                    moved = true
                    cancelHold()
                }
                if (moved && dragging) {
                    val nv = clampFor(stage, dragS0 + (ang(e.x, e.y) - dragA0))
                    val step = if (stage == 1) CSTEP else astep()
                    val idx = (-nv / step).roundToInt()
                    if (idx != lastTick) {
                        lastTick = idx
                        haptic(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    if (stage == 1) cscroll = nv else scroll = nv
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val wasLong = longFired
                val h = downHit
                cancelHold()
                if (!moved && !wasLong && h != null) click(h)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun click(h: Hit) {
        when (h.kind) {
            1 -> chooseCat(h.idx)
            2 -> {
                val list = curList()
                if (h.idx < list.size) {
                    val app = list[h.idx]
                    haptic(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (cat != Folders.RECENT) repo.touchRecent(app.key)
                    host?.onLaunch(app)
                }
            }
            3 -> trash()
            5 -> backTap()
        }
    }

    private fun chooseCat(k: Int) {
        val m = moving
        if (k == Folders.LAUNCHER) {
            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            host?.onSettings()
            return
        }
        if (m != null) {
            if (k == from) {
                moving = null
                delArm = false
                cat = k
                scroll = 0f
                setStage(2)
                return
            }
            if (k == Folders.RECENT) {
                host?.toast("Recent fills itself in")
                return
            }
            if (k == Folders.FAVORITES) {
                // Favorites is an extra: the app keeps its own folder and we return to it.
                val already = repo.isFavorite(m)
                repo.addFavorite(m)
                moving = null
                delArm = false
                cat = from
                scroll = 0f
                haptic(HapticFeedbackConstants.LONG_PRESS)
                host?.toast(if (already) "${m.label} is already in Favorites" else "Added ${m.label} to Favorites")
                setStage(2)
                return
            }
            repo.move(m, k)
            moving = null
            delArm = false
            cat = k
            scroll = lo(curList().size, astep())
            haptic(HapticFeedbackConstants.LONG_PRESS)
            host?.toast(
                if (k == Folders.HIDDEN) "${m.label} is now hidden"
                else "Moved ${m.label} to ${Folders.NAMES[k]}"
            )
            setStage(2)
        } else {
            haptic(HapticFeedbackConstants.VIRTUAL_KEY)
            cat = k
            scroll = 0f
            query = ""
            prefs.lastCat = k
            setStage(2)
        }
    }

    private fun backTap() {
        when (stage) {
            0 -> {
                if (prefs.remember && prefs.lastCat >= 0 && prefs.lastCat != Folders.HIDDEN) {
                    cat = prefs.lastCat
                    scroll = 0f
                    query = ""
                    setStage(2)
                } else {
                    setStage(1)
                }
            }
            1 -> {
                if (moving != null) {
                    moving = null
                    delArm = false
                    cat = from
                    scroll = 0f
                    setStage(2)
                } else {
                    setStage(0)
                }
            }
            else -> {
                repo.clearFresh(cat)
                setStage(1)
            }
        }
    }

    private fun startMove(app: AppEntry) {
        if (cat == Folders.FAVORITES) {
            haptic(HapticFeedbackConstants.LONG_PRESS)
            host?.onRemoveFavorite(app) {
                scroll = clampFor(2, scroll)
                invalidate()
            }
            return
        }
        moving = app
        from = cat
        cscroll = 0f
        delArm = false
        haptic(HapticFeedbackConstants.LONG_PRESS)
        setStage(1)
    }

    private fun openHidden() {
        cat = Folders.HIDDEN
        scroll = 0f
        query = ""
        haptic(HapticFeedbackConstants.LONG_PRESS)
        setStage(2)
    }

    private fun trash() {
        val m = moving ?: return
        if (!delArm) {
            delArm = true
            uiHandler.removeCallbacks(disarm)
            uiHandler.postDelayed(disarm, 3000)
            invalidate()
        } else {
            uiHandler.removeCallbacks(disarm)
            delArm = false
            moving = null
            cat = from
            scroll = 0f
            setStage(2)
            host?.onUninstall(m)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelHold()
        geoAnim?.cancel()
        uiHandler.removeCallbacks(disarm)
    }
}
