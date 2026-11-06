package com.resonance.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads album art: custom cover > system thumbnail > art embedded in the MP3's ID3 tag. */
object ArtLoader {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(ctx: Context, song: Song, px: Int): Bitmap? {
        val key = "${song.id}_${song.coverStamp}_$px"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { loadInternal(ctx.applicationContext, song, px) }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    private fun loadInternal(ctx: Context, song: Song, px: Int): Bitmap? {
        val custom = File(File(ctx.filesDir, "covers"), "${song.id}.jpg")
        if (custom.exists()) decodeFile(custom.path, px)?.let { return it }

        if (Build.VERSION.SDK_INT >= 29) {
            try {
                return ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
            } catch (_: Exception) {
                // no thumbnail available, fall back to the embedded picture
            }
        }
        return embedded(ctx, song, px)
    }

    private fun embedded(ctx: Context, song: Song, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, song.uri)
            r.embeddedPicture?.let { decodeBytes(it, px) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun sampleFor(w: Int, h: Int, px: Int): Int {
        var s = 1
        while (w / (s * 2) >= px && h / (s * 2) >= px) s *= 2
        return s
    }

    private fun decodeBytes(b: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(o.outWidth, o.outHeight, px) }
        return BitmapFactory.decodeByteArray(b, 0, b.size, opts)
    }

    private fun decodeFile(path: String, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(o.outWidth, o.outHeight, px) }
        return BitmapFactory.decodeFile(path, opts)
    }
}
