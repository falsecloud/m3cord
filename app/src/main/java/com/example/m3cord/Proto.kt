package com.example.m3cord

import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * A GIF as Discord stores it in a user's favorites (FrecencyUserSettings.favorite_gifs).
 * [url] is the provider page (the map key, and what gets posted when you send it), [src] the media URL.
 * [thumb] is only used for display of search results and is never stored.
 */
data class FavGif(
    val url: String, val src: String, val w: Int, val h: Int, val video: Boolean,
    val order: Int = 0, val thumb: String? = null
)

/** Something Coil can draw. Video favorites get their GIF twin when we can derive it. */
fun gifImageUrl(src: String): String {
    val lower = src.substringBefore('?').lowercase()
    if (!(lower.endsWith(".mp4") || lower.endsWith(".webm"))) return src
    return when {
        "media.tenor.com" in src ->
            src.replace(Regex("""(media\.tenor\.com/[^/]{6,})[^/]{5}/([^/?]+)\.(mp4|webm).*$""")) {
                it.groupValues[1] + "AAAAC/" + it.groupValues[2] + ".gif"
            }
        "giphy.com" in src -> src.replace(Regex("""\.(mp4|webm)(\?.*)?$"""), ".gif")
        else -> src
    }
}

/**
 * Minimal protobuf reader/writer for just the part of FrecencyUserSettings we need:
 *   FrecencyUserSettings { FavoriteGIFs favorite_gifs = 2; }
 *   FavoriteGIFs { map<string, FavoriteGIF> gifs = 1; }
 *   FavoriteGIF { format = 1 (1 image, 2 video); src = 2; width = 3; height = 4; order = 5; }
 * Discord requires the whole top-level field on update (see discord-userdoccers), which is exactly what encode() builds.
 */
object FavGifProto {
    private class Reader(val b: ByteArray) {
        var p = 0
        fun more() = p < b.size
        fun varint(): Long {
            var r = 0L
            var s = 0
            while (true) {
                val x = b[p++].toInt()
                r = r or ((x and 0x7F).toLong() shl s)
                if ((x and 0x80) == 0) return r
                s += 7
            }
        }
        fun bytes(): ByteArray {
            val n = varint().toInt()
            val o = b.copyOfRange(p, p + n)
            p += n
            return o
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> p += 8
                2 -> { val n = varint().toInt(); p += n }
                5 -> p += 4
                else -> throw IllegalStateException("wire type $wire")
            }
        }
    }

    fun decode(b64: String): List<FavGif> {
        val out = ArrayList<FavGif>()
        try {
            val root = Reader(Base64.decode(b64, Base64.DEFAULT))
            while (root.more()) {
                val tag = root.varint().toInt()
                if ((tag shr 3) == 2 && (tag and 7) == 2) readGifs(Reader(root.bytes()), out) else root.skip(tag and 7)
            }
        } catch (e: Exception) {
            // a malformed blob just means "no favorites we can read"
        }
        return out.sortedByDescending { it.order }
    }

    private fun readGifs(r: Reader, out: MutableList<FavGif>) {
        while (r.more()) {
            val tag = r.varint().toInt()
            if ((tag shr 3) == 1 && (tag and 7) == 2) readEntry(Reader(r.bytes()), out) else r.skip(tag and 7)
        }
    }

    private fun readEntry(r: Reader, out: MutableList<FavGif>) {
        var key = ""
        var value: ByteArray? = null
        while (r.more()) {
            val tag = r.varint().toInt()
            val f = tag shr 3
            val w = tag and 7
            when {
                f == 1 && w == 2 -> key = String(r.bytes(), Charsets.UTF_8)
                f == 2 && w == 2 -> value = r.bytes()
                else -> r.skip(w)
            }
        }
        val v = value ?: return
        var format = 0L; var src = ""; var width = 0L; var height = 0L; var order = 0L
        val vr = Reader(v)
        while (vr.more()) {
            val tag = vr.varint().toInt()
            val f = tag shr 3
            val w = tag and 7
            when {
                f == 1 && w == 0 -> format = vr.varint()
                f == 2 && w == 2 -> src = String(vr.bytes(), Charsets.UTF_8)
                f == 3 && w == 0 -> width = vr.varint()
                f == 4 && w == 0 -> height = vr.varint()
                f == 5 && w == 0 -> order = vr.varint()
                else -> vr.skip(w)
            }
        }
        if (key.isNotEmpty() && src.isNotEmpty()) out += FavGif(key, src, width.toInt(), height.toInt(), format == 2L, order.toInt())
    }

    private fun ByteArrayOutputStream.varint(v: Long) {
        var x = v
        while (x >= 0x80) { write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }
        write(x.toInt())
    }

    private fun ByteArrayOutputStream.num(field: Int, v: Long) { varint((field shl 3).toLong()); varint(v) }

    private fun ByteArrayOutputStream.blob(field: Int, bytes: ByteArray) {
        varint(((field shl 3) or 2).toLong()); varint(bytes.size.toLong()); write(bytes)
    }

    fun encode(list: List<FavGif>): String {
        val gifs = ByteArrayOutputStream()
        for (g in list) {
            val value = ByteArrayOutputStream().apply {
                num(1, if (g.video) 2 else 1)
                blob(2, g.src.toByteArray(Charsets.UTF_8))
                num(3, g.w.toLong()); num(4, g.h.toLong()); num(5, g.order.toLong())
            }
            val entry = ByteArrayOutputStream().apply {
                blob(1, g.url.toByteArray(Charsets.UTF_8))
                blob(2, value.toByteArray())
            }
            gifs.blob(1, entry.toByteArray())
        }
        val root = ByteArrayOutputStream().apply { blob(2, gifs.toByteArray()) }
        return Base64.encodeToString(root.toByteArray(), Base64.NO_WRAP)
    }
}
