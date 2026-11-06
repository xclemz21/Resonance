package com.resonance.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TagOverride(val title: String?, val artist: String?, val album: String?)

/** Small SharedPreferences-backed store for playlists, favourites, hidden songs, tag edits and theme. */
class LocalStore(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("resonance", Context.MODE_PRIVATE)
    private val coversDir = File(ctx.applicationContext.filesDir, "covers").apply { mkdirs() }

    fun coverFile(songId: Long) = File(coversDir, "$songId.jpg")

    private fun longSet(key: String): Set<Long> =
        (p.getStringSet(key, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    private fun saveLongSet(key: String, s: Set<Long>) =
        p.edit().putStringSet(key, s.map { it.toString() }.toSet()).apply()

    fun hidden(): Set<Long> = longSet("hidden")
    fun saveHidden(s: Set<Long>) = saveLongSet("hidden", s)

    fun favorites(): Set<Long> = longSet("favorites")
    fun saveFavorites(s: Set<Long>) = saveLongSet("favorites", s)

    fun playlists(): Map<String, List<Long>> {
        val out = LinkedHashMap<String, List<Long>>()
        runCatching {
            val o = JSONObject(p.getString("playlists", "{}") ?: "{}")
            o.keys().forEach { name ->
                val a = o.getJSONArray(name)
                out[name] = List(a.length()) { a.getLong(it) }
            }
        }
        return out
    }

    fun savePlaylists(m: Map<String, List<Long>>) {
        val o = JSONObject()
        m.forEach { (name, ids) -> o.put(name, JSONArray(ids)) }
        p.edit().putString("playlists", o.toString()).apply()
    }

    fun tags(): Map<Long, TagOverride> {
        val out = HashMap<Long, TagOverride>()
        runCatching {
            val o = JSONObject(p.getString("tags", "{}") ?: "{}")
            o.keys().forEach { k ->
                val t = o.getJSONObject(k)
                out[k.toLong()] = TagOverride(
                    t.optString("title").ifBlank { null },
                    t.optString("artist").ifBlank { null },
                    t.optString("album").ifBlank { null },
                )
            }
        }
        return out
    }

    fun saveTags(m: Map<Long, TagOverride>) {
        val o = JSONObject()
        m.forEach { (id, t) ->
            o.put(
                id.toString(),
                JSONObject().put("title", t.title ?: "").put("artist", t.artist ?: "").put("album", t.album ?: ""),
            )
        }
        p.edit().putString("tags", o.toString()).apply()
    }

    fun theme(): Int = p.getInt("theme", 0)
    fun saveTheme(i: Int) = p.edit().putInt("theme", i).apply()
}
