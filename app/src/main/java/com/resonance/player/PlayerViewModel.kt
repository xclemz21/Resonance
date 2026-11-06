package com.resonance.player

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.resonance.player.audio.EffectsController
import com.resonance.player.data.LocalStore
import com.resonance.player.data.Mp3Repository
import com.resonance.player.data.Song
import com.resonance.player.data.TagOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface UiEvent {
    class Delete(val sender: IntentSender) : UiEvent
    class Toast(val message: String) : UiEvent
}

data class PlayState(
    val songId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val store = LocalStore(ctx)

    private var raw: List<Song> = emptyList()
    private var loadJob: Job? = null
    private var sleepJob: Job? = null
    private var controller: MediaController? = null
    private val tags: MutableMap<Long, TagOverride> = store.tags().toMutableMap()

    // ---- library state ----
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _hidden = MutableStateFlow(store.hidden())
    val hidden: StateFlow<Set<Long>> = _hidden.asStateFlow()

    private val _favorites = MutableStateFlow(store.favorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow(store.playlists())
    val playlists: StateFlow<Map<String, List<Long>>> = _playlists.asStateFlow()

    private val _themeIndex = MutableStateFlow(store.theme())
    val themeIndex: StateFlow<Int> = _themeIndex.asStateFlow()

    val keepScreenOn = MutableStateFlow(false)

    private val _sleepActive = MutableStateFlow(false)
    val sleepActive: StateFlow<Boolean> = _sleepActive.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    // ---- playback state ----
    private val _playState = MutableStateFlow(PlayState())
    val playState: StateFlow<PlayState> = _playState.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<Long>>(emptyList())
    val queue: StateFlow<List<Long>> = _queue.asStateFlow()

    val currentSong: StateFlow<Song?> = combine(_songs, _playState) { list, st ->
        list.firstOrNull { it.id == st.songId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        EffectsController.init(ctx)
        connect()
        viewModelScope.launch {
            while (isActive) {
                controller?.let { if (it.isPlaying) _position.value = it.currentPosition }
                delay(250)
            }
        }
    }

    // ---------------- library ----------------

    fun loadLibrary() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            raw = try { Mp3Repository.load(ctx) } catch (_: Exception) { emptyList() }
            rebuild()
            _loaded.value = true
        }
    }

    private fun rebuild() {
        val hidden = _hidden.value
        _songs.value = raw.filter { it.id !in hidden }.map { s ->
            val t = tags[s.id]
            val cover = store.coverFile(s.id)
            s.copy(
                title = t?.title ?: s.title,
                artist = t?.artist ?: s.artist,
                album = t?.album ?: s.album,
                coverStamp = if (cover.exists()) cover.lastModified() else 0L,
            )
        }
    }

    fun hideSong(id: Long) {
        _hidden.value = _hidden.value + id
        store.saveHidden(_hidden.value)
        rebuild()
        toast("Song hidden")
    }

    fun unhideAll() {
        _hidden.value = emptySet()
        store.saveHidden(emptySet())
        rebuild()
    }

    fun saveTags(song: Song, title: String, artist: String, album: String) {
        tags[song.id] = TagOverride(title.trim().ifBlank { null }, artist.trim().ifBlank { null }, album.trim().ifBlank { null })
        store.saveTags(tags)
        rebuild()
    }

    fun setCover(song: Song, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 800 && bounds.outHeight / (sample * 2) >= 800) sample *= 2
                val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return@launch
                store.coverFile(song.id).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                rebuild()
                toast("Cover updated")
            } catch (_: Exception) {
                toast("Couldn't use that image")
            }
        }
    }

    fun deleteFromDevice(song: Song) {
        val r = ctx.contentResolver
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(r, listOf(song.uri))
                _events.tryEmit(UiEvent.Delete(pi.intentSender))
            } else {
                r.delete(song.uri, null, null)
                loadLibrary()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                _events.tryEmit(UiEvent.Delete(e.userAction.actionIntent.intentSender))
            } else {
                toast("Couldn't delete this file")
            }
        }
    }

    fun setRingtone(song: Song) {
        if (!Settings.System.canWrite(ctx)) {
            val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            toast("Allow \"Modify system settings\", then try again")
            return
        }
        try {
            RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, song.uri)
            toast("Ringtone set")
        } catch (_: Exception) {
            toast("Couldn't set ringtone")
        }
    }

    // ---------------- favourites & playlists ----------------

    fun toggleFavorite(id: Long) {
        _favorites.value = if (id in _favorites.value) _favorites.value - id else _favorites.value + id
        store.saveFavorites(_favorites.value)
    }

    fun createPlaylist(name: String, firstSong: Long? = null) {
        val n = name.trim()
        if (n.isEmpty()) return
        val m = _playlists.value.toMutableMap()
        if (n !in m) m[n] = if (firstSong != null) listOf(firstSong) else emptyList()
        else if (firstSong != null && firstSong !in m.getValue(n)) m[n] = m.getValue(n) + firstSong
        _playlists.value = m
        store.savePlaylists(m)
    }

    fun addToPlaylist(name: String, id: Long) {
        val m = _playlists.value.toMutableMap()
        val l = m[name].orEmpty()
        if (id !in l) m[name] = l + id
        _playlists.value = m
        store.savePlaylists(m)
        toast("Added to $name")
    }

    fun deletePlaylist(name: String) {
        val m = _playlists.value.toMutableMap()
        m.remove(name)
        _playlists.value = m
        store.savePlaylists(m)
    }

    // ---------------- playback ----------------

    private fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, com.resonance.player.player.PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    sync(events.contains(Player.EVENT_TIMELINE_CHANGED))
                }
            })
            sync(true)
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync(updateQueue: Boolean) {
        val c = controller ?: return
        _playState.value = PlayState(
            songId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode,
            speed = c.playbackParameters.speed,
        )
        val d = c.duration
        _duration.value = if (d == C.TIME_UNSET) 0L else d
        _position.value = c.currentPosition
        if (updateQueue) {
            _queue.value = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        }
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build(),
        )
        .build()

    fun playSongs(list: List<Song>, index: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (list.isEmpty() || index !in list.indices) return
        c.shuffleModeEnabled = shuffle
        if (_queue.value == list.map { it.id }) {
            c.seekTo(index, 0)
        } else {
            c.setMediaItems(list.map { it.toMediaItem() }, index, 0)
            c.prepare()
        }
        c.play()
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isNotEmpty()) playSongs(list, Random.nextInt(list.size), shuffle = true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }
    fun previous() { controller?.seekToPrevious() }
    fun seekBack() { controller?.seekBack() }
    fun seekForward() { controller?.seekForward() }
    fun skipTo(index: Int) { controller?.apply { seekTo(index, 0); play() } }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        _position.value = ms
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed) }

    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepActive.value = false
            return
        }
        _sleepActive.value = true
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _sleepActive.value = false
            toast("Sleep timer finished")
        }
    }

    fun cycleTheme() {
        _themeIndex.value = _themeIndex.value + 1
        store.saveTheme(_themeIndex.value)
    }

    private fun toast(msg: String) { _events.tryEmit(UiEvent.Toast(msg)) }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}
