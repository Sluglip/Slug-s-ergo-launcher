package com.slug.ergolauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Skin ids and names. 0 is the plain glass disc, 10 is the picture you choose yourself. */
object Skins {
    val NAMES = listOf(
        "Glass (default)", "Record", "Moon", "Cookie", "Pizza", "Planet",
        "Donut", "Basketball", "Sunflower", "Clock face", "My own image"
    )
}

/** Draws the built-in wheel skins as full circles. They are turned by the scroll, so the art is made edge to edge. */
object SkinArt {

    fun make(skin: Int, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size / 2f
        when (skin) {
            1 -> record(c, r)
            2 -> moon(c, r)
            3 -> cookie(c, r)
            4 -> pizza(c, r)
            5 -> planet(c, r)
            6 -> donut(c, r)
            7 -> basketball(c, r)
            8 -> sunflower(c, r)
            9 -> clock(c, r)
        }
        return bmp
    }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, w: Float = 0f): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        p.style = style
        p.strokeWidth = w
        return p
    }

    private fun clipCircle(c: Canvas, r: Float, rad: Float = r) {
        val p = Path()
        p.addCircle(r, r, rad, Path.Direction.CW)
        c.clipPath(p)
    }

    /** Soft darkening toward the rim so every skin looks like a round object. */
    private fun shade(c: Canvas, r: Float, strength: Int = 90) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            r, r, r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(strength, 0, 0, 0)),
            floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(r, r, r, p)
    }

    private fun record(c: Canvas, r: Float) {
        c.drawCircle(r, r, r, paint(Color.parseColor("#17181C")))
        var i = 0
        var rad = r * 0.34f
        while (rad < r * 0.97f) {
            val a = if (i % 3 == 0) 70 else 28
            c.drawCircle(r, r, rad, paint(Color.argb(a, 255, 255, 255), Paint.Style.STROKE, r * 0.004f))
            rad += r * 0.012f
            i++
        }
        // bands between tracks
        for (b in listOf(0.52f, 0.7f, 0.86f)) {
            c.drawCircle(r, r, r * b, paint(Color.argb(120, 0, 0, 0), Paint.Style.STROKE, r * 0.02f))
        }
        // light sheen that sweeps as the record turns
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
        sheen.shader = SweepGradient(
            r, r,
            intArrayOf(
                Color.TRANSPARENT, Color.argb(70, 255, 255, 255), Color.TRANSPARENT,
                Color.TRANSPARENT, Color.argb(70, 255, 255, 255), Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.08f, 0.16f, 0.5f, 0.58f, 0.66f)
        )
        c.drawCircle(r, r, r * 0.97f, sheen)
        c.drawCircle(r, r, r * 0.3f, paint(Color.parseColor("#C0392B")))
        c.drawCircle(r, r, r * 0.3f, paint(Color.argb(90, 0, 0, 0), Paint.Style.STROKE, r * 0.01f))
        c.drawCircle(r, r, r * 0.04f, paint(Color.parseColor("#17181C")))
        c.drawCircle(r, r, r - r * 0.004f, paint(Color.argb(160, 255, 255, 255), Paint.Style.STROKE, r * 0.008f))
    }

    private fun moon(c: Canvas, r: Float) {
        val rnd = Random(7)
        val base = Paint(Paint.ANTI_ALIAS_FLAG)
        base.shader = RadialGradient(
            r * 0.8f, r * 0.8f, r * 1.4f,
            Color.parseColor("#D8D6CF"), Color.parseColor("#8E8C86"), Shader.TileMode.CLAMP
        )
        c.drawCircle(r, r, r, base)
        c.save()
        clipCircle(c, r)
        // dark seas
        for (i in 0 until 7) {
            val x = r * (0.3f + rnd.nextFloat() * 1.4f)
            val y = r * (0.3f + rnd.nextFloat() * 1.4f)
            c.drawCircle(x, y, r * (0.14f + rnd.nextFloat() * 0.2f), paint(Color.argb(70, 70, 70, 75)))
        }
        // craters
        for (i in 0 until 70) {
            val x = r * (0.1f + rnd.nextFloat() * 1.8f)
            val y = r * (0.1f + rnd.nextFloat() * 1.8f)
            val cr = r * (0.015f + rnd.nextFloat() * rnd.nextFloat() * 0.1f)
            c.drawCircle(x, y, cr, paint(Color.argb(90, 80, 80, 84)))
            c.drawCircle(x - cr * 0.12f, y - cr * 0.12f, cr * 0.82f, paint(Color.argb(60, 235, 235, 230)))
            c.drawCircle(x, y, cr, paint(Color.argb(70, 250, 250, 245), Paint.Style.STROKE, cr * 0.12f))
        }
        c.restore()
        shade(c, r, 120)
    }

    private fun cookie(c: Canvas, r: Float) {
        val rnd = Random(11)
        val base = Paint(Paint.ANTI_ALIAS_FLAG)
        base.shader = RadialGradient(
            r, r, r,
            intArrayOf(Color.parseColor("#E1AE6B"), Color.parseColor("#CD9248"), Color.parseColor("#9C6330")),
            floatArrayOf(0f, 0.75f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(r, r, r, base)
        c.save()
        clipCircle(c, r)
        for (i in 0 until 240) {
            val a = rnd.nextFloat() * (2 * PI).toFloat()
            val d = sqrt(rnd.nextFloat()) * r
            c.drawCircle(
                r + cos(a) * d, r + sin(a) * d, r * (0.004f + rnd.nextFloat() * 0.008f),
                paint(Color.argb(80, 120, 70, 30))
            )
        }
        // chocolate chips as rough lumps
        for (i in 0 until 26) {
            val a = rnd.nextFloat() * (2 * PI).toFloat()
            val d = (0.2f + sqrt(rnd.nextFloat()) * 0.72f) * r
            val x = r + cos(a) * d
            val y = r + sin(a) * d
            val cs = r * (0.045f + rnd.nextFloat() * 0.04f)
            val path = Path()
            val n = 7
            for (k in 0 until n) {
                val ang = (2 * PI * k / n).toFloat() + rnd.nextFloat() * 0.4f
                val rad = cs * (0.7f + rnd.nextFloat() * 0.5f)
                val px = x + cos(ang) * rad
                val py = y + sin(ang) * rad
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            c.drawPath(path, paint(Color.parseColor("#3F2314")))
            c.drawCircle(x - cs * 0.25f, y - cs * 0.25f, cs * 0.3f, paint(Color.argb(90, 255, 220, 190)))
        }
        c.restore()
        c.drawCircle(r, r, r * 0.985f, paint(Color.argb(110, 90, 50, 20), Paint.Style.STROKE, r * 0.03f))
        shade(c, r, 70)
    }

    private fun pizza(c: Canvas, r: Float) {
        val rnd = Random(5)
        c.drawCircle(r, r, r, paint(Color.parseColor("#D9A25B")))
        c.drawCircle(r, r, r * 0.985f, paint(Color.parseColor("#9C5E22"), Paint.Style.STROKE, r * 0.03f))
        c.drawCircle(r, r, r * 0.86f, paint(Color.parseColor("#C0392B")))
        c.save()
        clipCircle(c, r, r * 0.84f)
        c.drawCircle(r, r, r * 0.84f, paint(Color.parseColor("#F3C84F")))
        for (i in 0 until 60) {
            val a = rnd.nextFloat() * (2 * PI).toFloat()
            val d = sqrt(rnd.nextFloat()) * r * 0.84f
            val col = if (i % 2 == 0) Color.argb(110, 255, 235, 150) else Color.argb(90, 215, 150, 40)
            c.drawCircle(r + cos(a) * d, r + sin(a) * d, r * (0.03f + rnd.nextFloat() * 0.06f), paint(col))
        }
        // pepperoni
        val placed = ArrayList<FloatArray>()
        var tries = 0
        while (placed.size < 15 && tries < 400) {
            tries++
            val a = rnd.nextFloat() * (2 * PI).toFloat()
            val d = (0.3f + rnd.nextFloat() * 0.5f) * r
            val x = r + cos(a) * d
            val y = r + sin(a) * d
            var ok = true
            for (q in placed) {
                val dx = q[0] - x
                val dy = q[1] - y
                if (dx * dx + dy * dy < (r * 0.2f) * (r * 0.2f)) ok = false
            }
            if (!ok) continue
            placed.add(floatArrayOf(x, y))
            val pr = r * 0.085f
            c.drawCircle(x, y, pr, paint(Color.parseColor("#A3261E")))
            c.drawCircle(x, y, pr, paint(Color.parseColor("#7A1914"), Paint.Style.STROKE, pr * 0.14f))
            for (k in 0 until 4) {
                c.drawCircle(
                    x + (rnd.nextFloat() - 0.5f) * pr, y + (rnd.nextFloat() - 0.5f) * pr,
                    pr * 0.12f, paint(Color.argb(150, 120, 25, 20))
                )
            }
        }
        // a few green bits
        for (i in 0 until 14) {
            val a = rnd.nextFloat() * (2 * PI).toFloat()
            val d = (0.25f + rnd.nextFloat() * 0.55f) * r
            c.drawRoundRect(
                RectF(r + cos(a) * d - r * 0.02f, r + sin(a) * d - r * 0.008f, r + cos(a) * d + r * 0.02f, r + sin(a) * d + r * 0.008f),
                4f, 4f, paint(Color.parseColor("#4E8F3A"))
            )
        }
        c.restore()
        shade(c, r, 60)
    }

    private fun planet(c: Canvas, r: Float) {
        val bands = intArrayOf(
            Color.parseColor("#E8D3A8"), Color.parseColor("#C99A62"), Color.parseColor("#F1E2BE"),
            Color.parseColor("#B07A4A"), Color.parseColor("#E0BF8A"), Color.parseColor("#9A6A45"),
            Color.parseColor("#EBD9B0"), Color.parseColor("#C28E58"), Color.parseColor("#D9B27C")
        )
        c.save()
        clipCircle(c, r)
        val bh = 2 * r / bands.size
        for (i in bands.indices) {
            c.drawRect(0f, i * bh, 2 * r, (i + 1) * bh + 1f, paint(bands[i]))
        }
        // wobbly edges between bands
        val rnd = Random(3)
        for (i in 1 until bands.size) {
            val y = i * bh
            for (k in 0 until 16) {
                c.drawCircle(
                    rnd.nextFloat() * 2 * r, y + (rnd.nextFloat() - 0.5f) * bh * 0.3f,
                    r * (0.02f + rnd.nextFloat() * 0.04f), paint(Color.argb(60, 255, 255, 255))
                )
            }
        }
        // storm
        c.drawOval(RectF(r * 1.1f, r * 1.05f, r * 1.55f, r * 1.28f), paint(Color.parseColor("#B5542F")))
        c.drawOval(RectF(r * 1.18f, r * 1.1f, r * 1.47f, r * 1.23f), paint(Color.parseColor("#D77A4F")))
        c.restore()
        shade(c, r, 140)
    }

    private fun donut(c: Canvas, r: Float) {
        val rnd = Random(9)
        c.drawCircle(r, r, r, paint(Color.parseColor("#D9A05B")))
        val fr = Paint(Paint.ANTI_ALIAS_FLAG)
        fr.shader = RadialGradient(
            r, r, r, Color.parseColor("#F9A8C9"), Color.parseColor("#E8709F"), Shader.TileMode.CLAMP
        )
        // wavy frosting edge
        val path = Path()
        val n = 120
        for (k in 0..n) {
            val ang = (2 * PI * k / n).toFloat()
            val rad = r * (0.9f + 0.03f * sin(ang * 9f) + 0.015f * sin(ang * 17f))
            val x = r + cos(ang) * rad
            val y = r + sin(ang) * rad
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, fr)
        c.save()
        c.clipPath(path)
        val cols = intArrayOf(
            Color.WHITE, Color.parseColor("#FFD54F"), Color.parseColor("#4FC3F7"),
            Color.parseColor("#81C784"), Color.parseColor("#FF8A65"), Color.parseColor("#BA68C8")
        )
        for (i in 0 until 130) {
            val a = rnd.nextFloat() * (2 * PI).toFloat()
            val d = (0.28f + sqrt(rnd.nextFloat()) * 0.62f) * r
            val x = r + cos(a) * d
            val y = r + sin(a) * d
            c.save()
            c.rotate(rnd.nextFloat() * 180f, x, y)
            c.drawRoundRect(RectF(x - r * 0.035f, y - r * 0.01f, x + r * 0.035f, y + r * 0.01f), 6f, 6f, paint(cols[i % cols.size]))
            c.restore()
        }
        c.restore()
        c.drawCircle(r, r, r * 0.2f, paint(Color.parseColor("#6E4524")))
        shade(c, r, 80)
    }

    private fun basketball(c: Canvas, r: Float) {
        val base = Paint(Paint.ANTI_ALIAS_FLAG)
        base.shader = RadialGradient(
            r * 0.85f, r * 0.85f, r * 1.4f, Color.parseColor("#F08A3A"), Color.parseColor("#B8531A"), Shader.TileMode.CLAMP
        )
        c.drawCircle(r, r, r, base)
        c.save()
        clipCircle(c, r)
        val seam = paint(Color.parseColor("#2A1A10"), Paint.Style.STROKE, r * 0.03f)
        c.drawLine(r, 0f, r, 2 * r, seam)
        c.drawLine(0f, r, 2 * r, r, seam)
        c.drawCircle(r - r * 1.3f, r, r * 1.0f, seam)
        c.drawCircle(r + r * 1.3f, r, r * 1.0f, seam)
        val rnd = Random(2)
        for (i in 0 until 500) {
            c.drawCircle(rnd.nextFloat() * 2 * r, rnd.nextFloat() * 2 * r, r * 0.004f, paint(Color.argb(40, 60, 20, 0)))
        }
        c.restore()
        shade(c, r, 110)
    }

    private fun sunflower(c: Canvas, r: Float) {
        c.drawCircle(r, r, r, paint(Color.parseColor("#3C7A2A")))
        for (layer in 0 until 2) {
            val n = 26
            for (k in 0 until n) {
                val ang = (2 * PI * k / n).toFloat() + layer * (PI / n).toFloat()
                c.save()
                c.rotate(Math.toDegrees(ang.toDouble()).toFloat(), r, r)
                val col = if (layer == 0) Color.parseColor("#E8AE0C") else Color.parseColor("#F9CB1E")
                val rect = RectF(r + r * (0.5f - layer * 0.04f), r - r * 0.075f, r + r * (0.99f - layer * 0.03f), r + r * 0.075f)
                c.drawOval(rect, paint(col))
                c.drawOval(rect, paint(Color.argb(90, 150, 90, 0), Paint.Style.STROKE, r * 0.006f))
                c.restore()
            }
        }
        c.drawCircle(r, r, r * 0.58f, paint(Color.parseColor("#3A2211")))
        // seeds in a golden-angle spiral
        val golden = (PI * (3 - sqrt(5.0))).toFloat()
        for (i in 1 until 520) {
            val d = r * 0.56f * sqrt(i / 520f)
            val a = i * golden
            val col = if (i % 3 == 0) Color.parseColor("#6B4423") else Color.parseColor("#2A180B")
            c.drawCircle(r + cos(a) * d, r + sin(a) * d, r * 0.016f, paint(col))
        }
        shade(c, r, 70)
    }

    private fun clock(c: Canvas, r: Float) {
        c.drawCircle(r, r, r, paint(Color.parseColor("#2B2B2B")))
        c.drawCircle(r, r, r * 0.95f, paint(Color.parseColor("#F4F1EA")))
        val tick = paint(Color.parseColor("#2B2B2B"), Paint.Style.STROKE, r * 0.006f)
        for (m in 0 until 60) {
            val a = (2 * PI * m / 60).toFloat()
            val big = m % 5 == 0
            val r1 = r * (if (big) 0.82f else 0.88f)
            val r2 = r * 0.93f
            tick.strokeWidth = r * (if (big) 0.018f else 0.007f)
            c.drawLine(r + cos(a) * r1, r + sin(a) * r1, r + cos(a) * r2, r + sin(a) * r2, tick)
        }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG)
        txt.color = Color.parseColor("#2B2B2B")
        txt.textSize = r * 0.12f
        txt.textAlign = Paint.Align.CENTER
        txt.isFakeBoldText = true
        for (h in 1..12) {
            val a = (2 * PI * h / 12 - PI / 2).toFloat()
            val x = r + cos(a) * r * 0.7f
            val y = r + sin(a) * r * 0.7f
            c.save()
            c.rotate(Math.toDegrees(a.toDouble()).toFloat() + 90f, x, y)
            c.drawText(h.toString(), x, y + txt.textSize * 0.35f, txt)
            c.restore()
        }
        shade(c, r, 50)
    }
}
