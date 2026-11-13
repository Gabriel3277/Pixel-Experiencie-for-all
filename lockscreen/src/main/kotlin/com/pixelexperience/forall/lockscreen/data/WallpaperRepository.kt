package com.pixelexperience.forall.lockscreen.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Cores derivadas do papel de parede (cores dinâmicas). */
data class LockPalette(val accent: Color, val onAccent: Color, val clockTint: Color, val cardTint: Color) {
    companion object {
        val Default = LockPalette(
            accent = Color(0xFF7FDDB0), onAccent = Color(0xFF00391F),
            clockTint = Color(0xFFD8F5E6), cardTint = Color(0xFF0C1F17),
        )
    }
}

class WallpaperUi(val image: ImageBitmap, val bitmap: Bitmap, val palette: LockPalette)

object BuiltInWallpapers {
    private class Blob(val x: Float, val y: Float, val r: Float, val color: Int)
    private class Def(val label: String, val top: Int, val bottom: Int, val blobs: List<Blob>)

    private val defs = linkedMapOf(
        "aurora" to Def("Aurora", 0xFF06231D.toInt(), 0xFF0E4F3F.toInt(), listOf(
            Blob(.20f, .25f, .90f, 0xFF2EE6A6.toInt()), Blob(.85f, .60f, .80f, 0xFF1B8F6B.toInt()), Blob(.40f, .95f, .70f, 0xFF7CF2C9.toInt()))),
        "dusk" to Def("Entardecer", 0xFF1B1038.toInt(), 0xFF4A1F5C.toInt(), listOf(
            Blob(.80f, .20f, .90f, 0xFFFF7BAC.toInt()), Blob(.15f, .65f, .80f, 0xFF7B5CFF.toInt()), Blob(.60f, .95f, .70f, 0xFFFFB36B.toInt()))),
        "ocean" to Def("Oceano", 0xFF041B2D.toInt(), 0xFF0B4A6F.toInt(), listOf(
            Blob(.25f, .20f, .90f, 0xFF3DB8FF.toInt()), Blob(.90f, .55f, .80f, 0xFF1E6FD9.toInt()), Blob(.35f, .90f, .70f, 0xFF6FE7E0.toInt()))),
        "peach" to Def("Pêssego", 0xFF3A1A16.toInt(), 0xFF7A3B2E.toInt(), listOf(
            Blob(.75f, .25f, .90f, 0xFFFFA48A.toInt()), Blob(.20f, .70f, .80f, 0xFFFF6F61.toInt()), Blob(.65f, .95f, .70f, 0xFFFFD08A.toInt()))),
    )

    val ids: List<String> get() = defs.keys.toList()
    fun label(id: String): String = defs[id]?.label ?: id

    fun render(id: String, w: Int, h: Int): Bitmap {
        val def = defs[id] ?: defs.values.first()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), def.top, def.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        for (b in def.blobs) {
            p.shader = RadialGradient(
                b.x * w, b.y * h, b.r * w,
                ColorUtils.setAlphaComponent(b.color, 170), ColorUtils.setAlphaComponent(b.color, 0),
                Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        return bmp
    }
}

object WallpaperRepository {
    private const val FILE = "lock_wallpaper.jpg"
    @Volatile private var cacheKey: String? = null
    @Volatile private var cache: WallpaperUi? = null

    suspend fun load(ctx: Context, id: String, version: Int = 0): WallpaperUi = withContext(Dispatchers.Default) {
        val key = "$id#$version"
        cache?.let { if (cacheKey == key) return@withContext it }
        val bmp = if (id == "custom") {
            decodeCustom(ctx) ?: BuiltInWallpapers.render("aurora", 720, 1560)
        } else {
            BuiltInWallpapers.render(id.removePrefix("builtin:"), 720, 1560)
        }
        val ui = WallpaperUi(bmp.asImageBitmap(), bmp, paletteOf(bmp))
        cache = ui
        cacheKey = key
        ui
    }

    /** Copia a imagem escolhida (Photo Picker, sem permissão de armazenamento) para o armazenamento do app. */
    suspend fun saveCustom(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val side = max(info.size.width, info.size.height)
                if (side > 2400) {
                    val f = 2400f / side
                    d.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
            }
            File(ctx.filesDir, FILE).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        }.getOrDefault(false)
    }

    private fun decodeCustom(ctx: Context): Bitmap? {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1920) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.path, opts)
    }

    private fun paletteOf(bmp: Bitmap): LockPalette {
        val p = Palette.from(bmp).maximumColorCount(12).generate()
        val rgb = p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb ?: p.dominantSwatch?.rgb
            ?: return LockPalette.Default
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        fun c(s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(s, 0.9f), l)))
        return LockPalette(
            accent = c(0.35f, 0.72f),
            onAccent = c(0.30f, 0.14f),
            clockTint = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(0.25f, 0.6f), 0.90f))),
            cardTint = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1].coerceIn(0.2f, 0.5f), 0.12f))),
        )
    }
}

/** "Blur" barato para o fundo dos cards: miniatura do papel de parede com box blur (CPU, ~100x200 px). */
object BackdropBlur {
    fun make(src: Bitmap, level: Float): Bitmap {
        val tw = 96
        val th = (tw.toFloat() * src.height / src.width).toInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(src, tw, th, true).copy(Bitmap.Config.ARGB_8888, true)
        val radius = (1 + level.coerceIn(0f, 1f) * 7).toInt()
        var a = IntArray(tw * th)
        small.getPixels(a, 0, tw, 0, 0, tw, th)
        var b = IntArray(tw * th)
        repeat(2) {
            pass(a, b, tw, th, radius, true)
            pass(b, a, tw, th, radius, false)
        }
        small.setPixels(a, 0, tw, 0, 0, tw, th)
        return small
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        for (y in 0 until h) for (x in 0 until w) {
            var sr = 0; var sg = 0; var sb = 0; var n = 0
            for (k in -r..r) {
                val xx = if (horizontal) (x + k).coerceIn(0, w - 1) else x
                val yy = if (horizontal) y else (y + k).coerceIn(0, h - 1)
                val p = src[yy * w + xx]
                sr += (p shr 16) and 0xFF; sg += (p shr 8) and 0xFF; sb += p and 0xFF; n++
            }
            dst[y * w + x] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
        }
    }
}
