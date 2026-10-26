package com.slug.ergolauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Full-screen settings page. Opened from the gear button in Search / Recent. */
class SettingsActivity : Activity() {

    companion object {
        private const val REQ_EXPORT = 51
        private const val REQ_IMPORT = 52
    }

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepo
    private lateinit var col: LinearLayout
    private lateinit var picker: ColorPickerView
    private lateinit var hex: EditText
    private var settingHex = false

    private val cText = Color.parseColor("#ECEFF4")
    private val cMuted = Color.parseColor("#B4BCCB")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepo(this, prefs)
        repo.refresh()

        val sv = ScrollView(this)
        sv.fitsSystemWindows = true
        sv.setBackgroundColor(Color.parseColor("#12141A"))
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(12), dp(20), dp(48))
        sv.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(sv)

        val title = label("Launcher settings", 22f, cText)
        title.typeface = Typeface.DEFAULT_BOLD
        col.addView(title)

        head("WHEEL")
        slider("Wheel size", 0.85f, 1.15f, 0.05f, prefs.wheelSize, { "${(it * 100).roundToInt()}%" }) { prefs.wheelSize = it }
        slider("Wheel opacity", 0.5f, 1f, 0.05f, prefs.wheelOpacity, { "${(it * 100).roundToInt()}%" }) { prefs.wheelOpacity = it }
        slider("Icon size", 44f, 64f, 2f, prefs.iconSize.toFloat(), { "${it.roundToInt()} dp" }) { prefs.iconSize = it.roundToInt() }
        slider("Comfort zone: distance from corner", 160f, 270f, 10f, prefs.reach.toFloat(), { "${it.roundToInt()} dp" }) { prefs.reach = it.roundToInt() }
        slider("App rows (staggered lanes)", 1f, 5f, 1f, prefs.rows.toFloat(), { "${it.roundToInt()}" }) { prefs.rows = it.roundToInt() }
        colorSection()
        segment("Operation", listOf("Right-handed", "Left-handed"), if (prefs.left) 1 else 0) { prefs.left = it == 1 }
        segment("Icon labels", listOf("Off", "Folders", "All"), prefs.labels) { prefs.labels = it }

        head("BEHAVIOR")
        switchRow("Remember last folder", "Open the wheel straight to the folder you used last.", prefs.remember) { prefs.remember = it }
        switchRow("Reduce motion", "Turn off the wheel animations.", prefs.reduceMotion) { prefs.reduceMotion = it }
        switchRow("Haptic feedback", "A tick as items pass your thumb, a buzz when a hold completes.", prefs.haptics) { prefs.haptics = it }

        head("HOLD TIMING")
        slider("Move or uninstall an app", 1f, 3f, 0.5f, prefs.holdMove, { "%.1f s".format(it) }) { prefs.holdMove = it }
        slider("Open Hidden folder", 1f, 3f, 0.5f, prefs.holdHidden, { "%.1f s".format(it) }) { prefs.holdHidden = it }
        slider("Add or edit a widget", 0.5f, 2f, 0.5f, prefs.holdWidget, { "%.1f s".format(it) }) { prefs.holdWidget = it }

        head("NEW APPS")
        switchRow(
            "Auto-sort new apps",
            "Place new installs in the folder that matches their app category. Apps with no category go to Other.",
            prefs.autoSort
        ) { prefs.autoSort = it }

        head("DATA")
        action("Reset layout", "Puts every app back where auto-sort would place it.") {
            AlertDialog.Builder(this)
                .setTitle("Reset layout?")
                .setMessage("All apps return to their automatic folders and Hidden is emptied.")
                .setPositiveButton("Reset") { _, _ ->
                    repo.resetLayout()
                    Toast.makeText(this, "Layout reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        action("Export layout", "Saves your folders and hidden apps to a file.") {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "ergo-launcher-layout.json")
            startActivityForResult(i, REQ_EXPORT)
        }
        action("Import layout", "Restores a saved layout file.") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, REQ_IMPORT)
        }
    }

    // ---- building blocks ----

    private fun label(t: String, size: Float, color: Int): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        return v
    }

    private fun head(t: String) {
        val v = label(t, 12f, cMuted)
        v.typeface = Typeface.DEFAULT_BOLD
        v.letterSpacing = 0.12f
        v.setPadding(0, dp(26), 0, dp(6))
        col.addView(v)
        val line = View(this)
        line.setBackgroundColor(Color.parseColor("#2A303C"))
        col.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun slider(
        name: String, min: Float, max: Float, step: Float, cur: Float,
        fmt: (Float) -> String, set: (Float) -> Unit
    ) {
        val t = label("$name   ${fmt(cur)}", 15f, cText)
        t.setPadding(0, dp(14), 0, 0)
        col.addView(t)
        val sb = SeekBar(this)
        sb.max = ((max - min) / step).roundToInt()
        sb.progress = ((cur - min) / step).roundToInt()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                val v = min + p * step
                t.text = "$name   ${fmt(v)}"
                if (fromUser) set(v)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        col.addView(sb)
    }

    private fun switchRow(name: String, sub: String, cur: Boolean, set: (Boolean) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(4))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(name, 15f, cText))
        texts.addView(label(sub, 13f, cMuted))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this)
        sw.isChecked = cur
        sw.setOnCheckedChangeListener { _, c -> set(c) }
        row.addView(sw)
        col.addView(row)
    }

    private fun segment(name: String, options: List<String>, cur: Int, set: (Int) -> Unit) {
        val t = label(name, 15f, cText)
        t.setPadding(0, dp(14), 0, dp(4))
        col.addView(t)
        val g = RadioGroup(this)
        g.orientation = RadioGroup.HORIZONTAL
        for ((i, o) in options.withIndex()) {
            val b = RadioButton(this)
            b.id = View.generateViewId()
            b.text = o
            b.setTextColor(cText)
            b.isChecked = i == cur
            b.setOnClickListener { set(i) }
            g.addView(b, RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(g)
    }

    private fun action(name: String, sub: String, run: () -> Unit) {
        val b = Button(this)
        b.text = name
        b.isAllCaps = false
        b.setTextColor(cText)
        b.setOnClickListener { run() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        col.addView(b, lp)
        col.addView(label(sub, 13f, cMuted))
    }

    private fun colorSection() {
        val t = label("Wheel color", 15f, cText)
        t.setPadding(0, dp(14), 0, dp(8))
        col.addView(t)

        picker = ColorPickerView(this, prefs.color) { c ->
            prefs.color = c
            if (!settingHex) {
                settingHex = true
                hex.setText(String.format("#%06X", 0xFFFFFF and c))
                settingHex = false
            }
        }
        col.addView(picker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(10), 0, 0)
        row.addView(label("Hex  ", 14f, cText))
        hex = EditText(this)
        hex.setSingleLine()
        hex.setTextColor(cText)
        hex.setText(String.format("#%06X", 0xFFFFFF and prefs.color))
        hex.background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(Color.parseColor("#2B3240"))
            setStroke(dp(2), Color.parseColor("#3A4252"))
        }
        hex.setPadding(dp(16), 0, dp(16), 0)
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (settingHex) return
                val raw = (s?.toString() ?: "").trim().removePrefix("#")
                if (raw.length == 6 && raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    val c = Color.parseColor("#$raw")
                    prefs.color = c
                    settingHex = true
                    picker.setColor(c)
                    settingHex = false
                }
            }
        })
        row.addView(hex, LinearLayout.LayoutParams(0, dp(44), 1f))
        col.addView(row)
        col.addView(label("Drag the square and the bar, or type a hex code.", 12f, cMuted))
    }

    // ---- export / import ----

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == REQ_EXPORT) {
                contentResolver.openOutputStream(uri)?.use { it.write(repo.exportJson().toByteArray()) }
                Toast.makeText(this, "Layout exported", Toast.LENGTH_SHORT).show()
            } else if (requestCode == REQ_IMPORT) {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
                val ok = repo.importJson(text)
                Toast.makeText(this, if (ok) "Layout imported" else "That file isn't a layout backup", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't read or write that file", Toast.LENGTH_SHORT).show()
        }
    }
}
