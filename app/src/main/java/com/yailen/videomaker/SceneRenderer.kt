package com.yailen.videomaker

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Dibuja cada fotograma: fondo animado (o tus fotos con zoom/paneo) + título + subtítulos. */
class SceneRenderer(
    private val w: Int,
    private val h: Int,
    private val bitmaps: List<Bitmap>,
    private val title: String,
    scenes: List<String>
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

    /**
     * @param scene índice de escena
     * @param tSec segundos dentro de la escena
     * @param frac avance 0..1 dentro de la escena
     * @param time segundos totales del video
     */
    fun draw(c: Canvas, scene: Int, tSec: Float, frac: Float, time: Float) {
        if (bitmaps.isNotEmpty()) drawPhoto(c, bitmaps[scene % bitmaps.size], scene, frac)
        else drawGradient(c, scene, time)

        // Título superior
        if (title.isNotBlank()) {
            val bob = sin(time * 2f) * m * 0.01f
            c.drawText(title, w / 2f, h * 0.06f + m * 0.055f + bob, titlePaint)
        }

        // Subtítulo con fade-in
        val layout = layouts[scene]
        val a = min(1f, tSec / 0.35f)
        val top = h - layout.height - h * 0.09f
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
        // Círculos flotantes = movimiento constante
        for (k in 0 until 8) {
            val cx = w * (0.5f + 0.42f * sin(time * (0.4f + 0.13f * k) + k * 1.7f))
            val cy = h * (0.5f + 0.42f * cos(time * (0.3f + 0.11f * k) + k * 2.3f))
            val r = m * (0.06f + 0.03f * (k % 4))
            c.drawCircle(cx, cy, r, circlePaint)
        }
    }

    private fun drawPhoto(c: Canvas, bmp: Bitmap, scene: Int, frac: Float) {
        val cover = max(w / bmp.width.toFloat(), h / bmp.height.toFloat())
        val zoom = if (scene % 2 == 0) 1f + 0.15f * frac else 1.15f - 0.15f * frac
        val s = cover * zoom
        val overflowX = bmp.width * s - w
        val overflowY = bmp.height * s - h
        val dir = if (scene % 2 == 0) 1f else -1f
        val x = -overflowX * (0.5f + dir * (frac - 0.5f) * 0.6f)
        val y = -overflowY / 2f
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        c.drawBitmap(bmp, 0f, 0f, imgPaint)
        c.restore()
    }
}
