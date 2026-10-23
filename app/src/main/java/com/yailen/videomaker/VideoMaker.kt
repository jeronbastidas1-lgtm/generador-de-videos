package com.yailen.videomaker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class VideoConfig(
    val text: String,
    val title: String,
    val images: List<Uri>,
    val portrait: Boolean,
    val speed: Float,
    val pitch: Float
)

private class Pcm(val rate: Int, val channels: Int, val data: ByteArray)

object VideoMaker {
    private const val FPS = 30
    private const val PAD_MS = 700L

    suspend fun make(ctx: Context, cfg: VideoConfig, onProgress: (Float, String) -> Unit): Uri =
        withContext(Dispatchers.Default) {
            val work = File(ctx.cacheDir, "work").apply { deleteRecursively(); mkdirs() }
            val scenes = splitScenes(cfg.text)
            require(scenes.isNotEmpty()) { "Escribe un texto primero" }
            val w = if (cfg.portrait) 720 else 1280
            val h = if (cfg.portrait) 1280 else 720

            // ---------- 1) Voz en off (TextToSpeech nativo) ----------
            onProgress(0.02f, "Preparando la voz…")
            val pcms = ArrayList<Pcm>()
            val tts = initTts(ctx)
            try {
                tts.setSpeechRate(cfg.speed)
                tts.setPitch(cfg.pitch)
                scenes.forEachIndexed { i, s ->
                    val f = File(work, "s$i.wav")
                    synth(tts, s, f, "u$i")
                    pcms += readWav(f)
                    onProgress(0.05f + 0.20f * (i + 1) / scenes.size, "Generando voz ${i + 1}/${scenes.size}")
                }
            } finally {
                tts.shutdown()
            }

            val rate = pcms[0].rate
            val ch = pcms[0].channels
            val frameBytes = 2 * ch
            val audio = ByteArrayOutputStream()
            val boundariesMs = ArrayList<Long>() // fin de cada escena
            var cumMs = 0L
            for (p in pcms) {
                require(p.rate == rate && p.channels == ch) { "El motor de voz devolvió formatos distintos" }
                val voiceMs = p.data.size * 1000L / (rate * frameBytes)
                val sceneMs = max(voiceMs + PAD_MS, 1500L)
                var target = (sceneMs * rate / 1000L).toInt() * frameBytes
                if (target < p.data.size) target = p.data.size
                audio.write(p.data)
                audio.write(ByteArray(target - p.data.size))
                cumMs += target * 1000L / (rate * frameBytes)
                boundariesMs += cumMs
            }
            val pcmAll = audio.toByteArray()
            val totalFrames = (cumMs * FPS / 1000.0).roundToInt()
            val endFrames = boundariesMs.map { (it * FPS / 1000.0).roundToInt() }

            // ---------- 2) Imágenes opcionales ----------
            val bitmaps = cfg.images.mapNotNull { decodeCover(ctx, it, w, h) }
            val renderer = SceneRenderer(w, h, bitmaps, cfg.title, scenes)

            // ---------- 3) Video (MediaCodec H.264) ----------
            val videoFile = File(work, "video.mp4")
            try {
                var scene = 0
                encodeVideo(videoFile, w, h, totalFrames, { c: Canvas, f: Int ->
                    while (scene < endFrames.size - 1 && f >= endFrames[scene]) scene++
                    val start = if (scene == 0) 0 else endFrames[scene - 1]
                    val len = max(1, endFrames[scene] - start)
                    val local = f - start
                    renderer.draw(c, scene, local / FPS.toFloat(), min(1f, local / len.toFloat()), f / FPS.toFloat())
                }) { f ->
                    onProgress(0.25f + 0.55f * f / totalFrames, "Creando video… ${(100 * f / totalFrames)}%")
                }
            } finally {
                bitmaps.forEach { it.recycle() }
            }

            // ---------- 4) Audio (MediaCodec AAC) ----------
            onProgress(0.82f, "Codificando audio…")
            val audioFile = File(work, "audio.m4a")
            encodeAudio(audioFile, pcmAll, rate, ch)

            // ---------- 5) Unir y guardar ----------
            onProgress(0.92f, "Uniendo video y voz…")
            val finalFile = File(work, "final.mp4")
            mux(videoFile, audioFile, finalFile)
            val uri = saveToGallery(ctx, finalFile)
            work.deleteRecursively()
            onProgress(1f, "¡Listo! Guardado en Películas/YailenVideoMaker")
            uri
        }

    // ------------------------------------------------------------------
    fun splitScenes(text: String): List<String> {
        val out = ArrayList<String>()
        text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            line.split(Regex("(?<=[.!?…])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { out += it }
        }
        return out
    }

    private suspend fun initTts(ctx: Context): TextToSpeech {
        val ready = CompletableDeferred<Int>()
        val tts = TextToSpeech(ctx) { ready.complete(it) }
        if (ready.await() != TextToSpeech.SUCCESS) {
            tts.shutdown()
            throw IllegalStateException("No hay motor de voz (TTS). Instala 'Google Text-to-Speech' y la voz en español.")
        }
        val loc = listOf(
            Locale("es", "CO"), Locale("es", "MX"), Locale("es", "US"), Locale("es", "ES"), Locale("es")
        ).firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        tts.language = loc ?: Locale("es")
        return tts
    }

    private suspend fun synth(tts: TextToSpeech, text: String, file: File, id: String) {
        val done = CompletableDeferred<Boolean>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(true) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) done.complete(false) }
            override fun onError(utteranceId: String?, errorCode: Int) { if (utteranceId == id) done.complete(false) }
        })
        if (tts.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS)
            throw IllegalStateException("Falló la síntesis de voz")
        if (!done.await()) throw IllegalStateException("Falló la síntesis de voz")
    }

    private fun readWav(f: File): Pcm {
        val b = f.readBytes()
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        var rate = 22050
        var ch = 1
        var start = -1
        var len = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            if (id == "fmt ") {
                ch = bb.getShort(pos + 10).toInt()
                rate = bb.getInt(pos + 12)
            } else if (id == "data") {
                start = pos + 8
                len = if (size <= 0 || start + size > b.size) b.size - start else size
                break
            }
            if (size < 0) break
            pos += 8 + size + (size and 1)
        }
        if (start < 0) throw IllegalStateException("Audio de voz inválido")
        len -= len % (2 * ch)
        return Pcm(rate, ch, b.copyOfRange(start, start + len))
    }

    private fun decodeCover(ctx: Context, uri: Uri, w: Int, h: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= w * 1.3 && o.outHeight / (s * 2) >= h * 1.3) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
    } catch (e: Exception) { null }

    // ------------------------------------------------------------------
    private fun encodeVideo(
        out: File, w: Int, h: Int, totalFrames: Int,
        drawFrame: (Canvas, Int) -> Unit, progress: (Int) -> Unit
    ) {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        var outCount = 0L
        val info = MediaCodec.BufferInfo()

        fun drain(end: Boolean) {
            while (true) {
                val i = codec.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!end) return
                } else if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (i >= 0) {
                    val buf = codec.getOutputBuffer(i)!!
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                    if (info.size > 0 && started) {
                        // Reescribimos el tiempo: exactamente FPS fotogramas por segundo
                        info.presentationTimeUs = outCount * 1_000_000L / FPS
                        outCount++
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buf, info)
                    }
                    codec.releaseOutputBuffer(i, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }

        try {
            for (f in 0 until totalFrames) {
                val c = surface.lockCanvas(null)
                try { drawFrame(c, f) } finally { surface.unlockCanvasAndPost(c) }
                drain(false)
                if (f % 10 == 0) progress(f)
            }
            codec.signalEndOfInputStream()
            drain(true)
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            surface.release()
            try { if (started) muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun encodeAudio(out: File, pcm: ByteArray, rate: Int, ch: Int) {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        var offset = 0
        var inputDone = false
        val info = MediaCodec.BufferInfo()
        val frameBytes = 2 * ch
        try {
            while (true) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        var n = min(buf.remaining(), pcm.size - offset)
                        n -= n % frameBytes
                        val pts = offset * 1_000_000L / (rate.toLong() * frameBytes)
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.put(pcm, offset, n)
                            codec.queueInputBuffer(i, 0, n, pts, 0)
                            offset += n
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (o >= 0) {
                    val ob = codec.getOutputBuffer(o)!!
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                    if (info.size > 0 && started) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        muxer.writeSampleData(track, ob, info)
                    }
                    codec.releaseOutputBuffer(o, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            try { if (started) muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun pickTrack(ex: MediaExtractor): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") || mime.startsWith("audio/")) return i
        }
        throw IllegalStateException("Pista no encontrada")
    }

    private fun mux(video: File, audio: File, out: File) {
        val ve = MediaExtractor().apply { setDataSource(video.path) }
        val ae = MediaExtractor().apply { setDataSource(audio.path) }
        val vt = pickTrack(ve)
        val at = pickTrack(ae)
        ve.selectTrack(vt)
        ae.selectTrack(at)
        val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vi = m.addTrack(ve.getTrackFormat(vt))
        val ai = m.addTrack(ae.getTrackFormat(at))
        m.start()
        val buf = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        var vDone = false
        var aDone = false
        try {
            while (!vDone || !aDone) {
                val useVideo = !vDone && (aDone || ve.sampleTime <= ae.sampleTime)
                val ex = if (useVideo) ve else ae
                val idx = if (useVideo) vi else ai
                buf.clear()
                val size = ex.readSampleData(buf, 0)
                if (size < 0) {
                    if (useVideo) vDone = true else aDone = true
                    continue
                }
                val flags = if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, ex.sampleTime, flags)
                m.writeSampleData(idx, buf, info)
                ex.advance()
            }
        } finally {
            try { m.stop() } catch (_: Exception) {}
            m.release()
            ve.release()
            ae.release()
        }
    }

    private fun saveToGallery(ctx: Context, f: File): Uri {
        val name = "Yailen_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/YailenVideoMaker")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("No se pudo guardar el video")
        ctx.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } }
        return uri
    }
}
