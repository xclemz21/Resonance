package com.resonance.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media as AudioMedia
import java.io.File

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val bitrateKbps: Int,
    val path: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val track: Int,
    val year: Int,
    /** Changes whenever a custom cover is set, so cached art is refreshed. */
    val coverStamp: Long = 0L,
) {
    val folderPath: String get() = File(path).parent ?: ""
    val folderName: String get() = File(folderPath).name.ifBlank { "Music" }
}

/**
 * Reads the device's MediaStore and returns ONLY real MP3 music files:
 *  - mime type audio/mpeg (so no WAV, FLAC, AAC, AMR, OGG ...)
 *  - file name ends in .mp3
 *  - not a ringtone / alarm / notification
 *  - not flagged as a recording (Android 12+) and not stored in a recordings-style folder
 * Album, artist, year, track number and bitrate come from the ID3 tags the system scanner already parsed.
 */
object Mp3Repository {

    private val EXCLUDED_PATH_PARTS = listOf(
        "/recordings/", "/recording/", "/record/", "/voice recorder", "/voicerecorder",
        "/voice_recorder", "/voice recordings", "/call recordings", "/callrecordings",
        "/call_recordings", "/sounds/", "/ringtones/", "/notifications/", "/alarms/",
    )

    @Suppress("DEPRECATION")
    fun load(ctx: Context): List<Song> {
        val collection: Uri =
            if (Build.VERSION.SDK_INT >= 29) AudioMedia.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else AudioMedia.EXTERNAL_CONTENT_URI

        val projection = buildList {
            add(AudioMedia._ID)
            add(AudioMedia.TITLE)
            add(AudioMedia.ARTIST)
            add(AudioMedia.ALBUM)
            add(AudioMedia.ALBUM_ID)
            add(AudioMedia.DURATION)
            add(AudioMedia.DISPLAY_NAME)
            add(AudioMedia.DATA)
            add(AudioMedia.SIZE)
            add(AudioMedia.DATE_ADDED)
            add(AudioMedia.TRACK)
            add(AudioMedia.YEAR)
            if (Build.VERSION.SDK_INT >= 30) add(AudioMedia.BITRATE)
            if (Build.VERSION.SDK_INT >= 31) add(AudioMedia.IS_RECORDING)
        }.toTypedArray()

        val selection = "${AudioMedia.MIME_TYPE} IN (?, ?) AND " +
            "${AudioMedia.DISPLAY_NAME} LIKE ? AND " +
            "${AudioMedia.IS_RINGTONE} = 0 AND " +
            "${AudioMedia.IS_NOTIFICATION} = 0 AND " +
            "${AudioMedia.IS_ALARM} = 0 AND " +
            "${AudioMedia.DURATION} >= 1000"
        val args = arrayOf("audio/mpeg", "audio/mp3", "%.mp3")

        val out = ArrayList<Song>()
        ctx.contentResolver.query(
            collection, projection, selection, args,
            "${AudioMedia.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(AudioMedia._ID)
            val iTitle = c.getColumnIndexOrThrow(AudioMedia.TITLE)
            val iArtist = c.getColumnIndexOrThrow(AudioMedia.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(AudioMedia.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(AudioMedia.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(AudioMedia.DURATION)
            val iName = c.getColumnIndexOrThrow(AudioMedia.DISPLAY_NAME)
            val iData = c.getColumnIndexOrThrow(AudioMedia.DATA)
            val iSize = c.getColumnIndexOrThrow(AudioMedia.SIZE)
            val iAdded = c.getColumnIndexOrThrow(AudioMedia.DATE_ADDED)
            val iTrack = c.getColumnIndexOrThrow(AudioMedia.TRACK)
            val iYear = c.getColumnIndexOrThrow(AudioMedia.YEAR)
            val iBitrate = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(AudioMedia.BITRATE) else -1
            val iRec = if (Build.VERSION.SDK_INT >= 31) c.getColumnIndex(AudioMedia.IS_RECORDING) else -1

            while (c.moveToNext()) {
                val path = c.getString(iData).orEmpty()
                val lower = path.lowercase()
                if (EXCLUDED_PATH_PARTS.any { lower.contains(it) }) continue
                if (iRec >= 0 && c.getInt(iRec) == 1) continue

                val id = c.getLong(iId)
                val fileName = c.getString(iName).orEmpty()
                out += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                    artist = c.getString(iArtist)?.takeIf { it.isNotBlank() } ?: "<unknown>",
                    album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "<unknown>",
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDur),
                    bitrateKbps = if (iBitrate >= 0) (c.getInt(iBitrate) / 1000) else 0,
                    path = path,
                    sizeBytes = c.getLong(iSize),
                    dateAdded = c.getLong(iAdded),
                    track = c.getInt(iTrack) % 1000,
                    year = c.getInt(iYear),
                )
            }
        }
        return out
    }
}
