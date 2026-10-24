package com.yailen.videomaker

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Dibuja cada fotograma: fondo, personaje con movimiento natural, objetos/colores, título y subtítulos. */
class SceneRenderer(
    private val w: Int,
    private val h: Int,
    private val bitmaps: List<Bitmap>,
    private val title: String,
    scenes: List<String>,
    private val items: List<List<Visual>>
) {
    private val m = min(w, h).toFloat()

    private val palettes = listOf(
        intArrayOf(0xFFFF8A65.toInt(), 0xFFFFD54F.toInt()),
        intArrayOf(0xFF4FC3F7.toInt(), 0xFF81C784.toInt()),
        intArrayOf(0xFFBA68C8.toInt(), 0xFFF06292.toInt()),
        intArrayOf(0xFF4DB6AC.toInt(), 0xFFFFF176.toInt()),
        intArrayOf(0xFF7986CB.toInt(), 0xFF4DD0E1.toInt())
    )
    private val shaders = palettes.map {
        LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), it[0], it[1], Shader.TileMode.CLAMP)
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 55 }
    private val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f, 0f, 3f, Color.argb(200, 0, 0, 0))
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = m * 0.07f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = m * 0.055f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 3f, Color.argb(170, 0, 0, 0))
    }

    private val layouts = scenes.map {
        StaticLayout.Builder.obtain(it, 0, it.length, textPaint, (w * 0.86f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .build()
    }

    // malla para deformar la imagen suavemente (respiración, balanceo)
    private val mw = 10
    private val mh = 14
    private val verts = FloatArray((mw + 1) * (mh + 1) * 2)

    fun draw(c: Canvas, scene: Int, tSec: Float, frac: Float, time: Float) {
        if (bitmaps.isNotEmpty()) drawPhoto(c, bitmaps[scene % bitmaps.size], scene, frac, time)
        else drawGradient(c, scene, time)

        val layout = layouts[scene]
        val top = h - layout.height - h * 0.09f

        drawItems(c, items.getOrElse(scene) { emptyList() }, tSec, time, top)

        if (title.isNotBlank()) {
            val bob = sin(time * 2f) * m * 0.01f
            c.drawText(title, w / 2f, h * 0.06f + m * 0.055f + bob, titlePaint)
        }

        val a = min(1f, tSec / 0.35f)
        val pad = m * 0.03f
        boxPaint.alpha = (150 * a).toInt()
        c.drawRoundRect(
            RectF(w * 0.05f, top - pad, w * 0.95f, top + layout.height + pad),
            m * 0.04f, m * 0.04f, boxPaint
        )
        c.save()
        c.translate(w * 0.07f, top)
        textPaint.alpha = (255 * a).toInt()
        layout.draw(c)
        c.restore()
    }

    private fun drawGradient(c: Canvas, scene: Int, time: Float) {
        bgPaint.shader = shaders[scene % shaders.size]
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        bgPaint.shader = null
        for (k in 0 until 8) {
            val cx = w * (0.5f + 0.42f * sin(time * (0.4f + 0.13f * k) + k * 1.7f))
            val cy = h * (0.5f + 0.42f * cos(time * (0.3f + 0.11f * k) + k * 2.3f))
            val r = m * (0.06f + 0.03f * (k % 4))
            c.drawCircle(cx, cy, r, circlePaint)
        }
    }

    /** Foto/personaje "vivo": zoom suave + respiración + balanceo + ondulación (fijo abajo, se mueve más arriba). */
    private fun drawPhoto(c: Canvas, bmp: Bitmap, scene: Int, frac: Float, time: Float) {
        val cover = max(w / bmp.width.toFloat(), h / bmp.height.toFloat())
        val zoom = if (scene % 2 == 0) 1.05f + 0.08f * frac else 1.13f - 0.08f * frac
        val s = cover * zoom
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val overflowX = bw * s - w
        val overflowY = bh * s - h
        val dir = if (scene % 2 == 0) 1f else -1f
        val x = -overflowX * (0.5f + dir * (frac - 0.5f) * 0.5f)
        val y = -overflowY / 2f

        val amp = bw * 0.010f
        val breathe = 1f + 0.012f * sin(time * 2.2f)
        var k = 0
        for (j in 0..mh) {
            val yn = j / mh.toFloat()
            val yy = yn * bh
            val weight = 1f - yn
            for (i in 0..mw) {
                val xn = i / mw.toFloat()
                val dx = sin(time * 1.4f + yn * 2.5f) * amp * weight +
                    sin(time * 3.1f + xn * 4f + yn * 3f) * amp * 0.25f * weight
                val ny = bh - (bh - yy) * breathe + sin(time * 2.6f + xn * 3f) * amp * 0.15f * weight
                verts[k++] = xn * bw + dx
                verts[k++] = ny
            }
        }
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        c.drawBitmapMesh(bmp, mw, mh, verts, 0, null, 0, imgPaint)
        c.restore()
    }

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val t = x - 1f
        return 1f + c3 * t * t * t + c1 * t * t
    }

    /** Colores como esferas brillantes, objetos como figuras que "saltan" a la pantalla, con su nombre. */
    private fun drawItems(c: Canvas, list: List<Visual>, tSec: Float, time: Float, subtitleTop: Float) {
        val n = list.size
        if (n == 0) return
        val cols = if (w > h) n else if (n <= 2) 1 else 2
        val rows = ceil(n / cols.toFloat()).toInt()
        val top = h * 0.15f
        val bottom = min(subtitleTop - m * 0.05f, h * 0.7f)
        val cellW = w / cols.toFloat()
        val cellH = (bottom - top) / rows
        val r = min(min(cellW, cellH) * 0.34f, m * 0.26f)

        list.forEachIndexed { idx, v ->
            val row = idx / cols
            val inRow = if (row == rows - 1) n - row * cols else cols
            val offset = (cols - inRow) * cellW / 2f
            val cx = offset + cellW * (idx % cols + 0.5f)
            val cy = top + cellH * (row + 0.5f)

            val p = ((tSec - (0.3f + 0.35f * idx)) / 0.5f).coerceIn(0f, 1f)
            if (p <= 0f) return@forEachIndexed
            val sc = easeOutBack(p)
            val bob = sin(time * 3f + idx) * r * 0.06f

            c.save()
            c.translate(cx, cy + bob)
            c.rotate(sin(time * 2f + idx) * 4f)
            c.scale(sc, sc)

            if (v.color != null) {
                fillPaint.color = v.color
                c.drawCircle(0f, 0f, r, fillPaint)
                strokePaint.strokeWidth = r * 0.06f
                c.drawCircle(0f, 0f, r, strokePaint)
                fillPaint.color = Color.WHITE
                fillPaint.alpha = 80
                c.drawCircle(-r * 0.35f, -r * 0.4f, r * 0.22f, fillPaint)
                fillPaint.alpha = 255
            } else {
                fillPaint.color = Color.WHITE
                fillPaint.alpha = 90
                c.drawCircle(0f, 0f, r * 0.95f, fillPaint)
                fillPaint.alpha = 255
            }
            if (v.emoji != null) {
                emojiPaint.textSize = r * 1.15f
                c.drawText(v.emoji, 0f, r * 0.4f, emojiPaint)
            }
            labelPaint.textSize = r * 0.28f
            c.drawText(v.label, 0f, r + labelPaint.textSize * 1.1f, labelPaint)
            c.restore()
        }
    }
}
