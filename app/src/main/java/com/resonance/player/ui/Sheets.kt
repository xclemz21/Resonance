package com.resonance.player.ui

import android.content.Intent
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resonance.player.PlayerViewModel
import com.resonance.player.data.Song
import kotlin.math.roundToInt

private enum class SheetDialog { Playlist, Sleep, Speed, Tags, Info, Settings }

private val SheetColor = Color(0xFF1B1A44)
private val TileColor = Color.White.copy(alpha = 0.08f)

private fun speedLabel(s: Float) = "%.2f".format(s).trimEnd('0').trimEnd('.') + "x"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OptionsSheet(
    vm: PlayerViewModel,
    song: Song,
    onDismiss: () -> Unit,
    onGoAlbum: () -> Unit,
    onOpenEq: () -> Unit,
) {
    val ctx = LocalContext.current
    val accent = LocalPalette.current.accent
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keepOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val play by vm.playState.collectAsStateWithLifecycle()
    val sleepOn by vm.sleepActive.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<SheetDialog?>(null) }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setCover(song, uri)
    }

    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SheetColor) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(song, 160, Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            song.artist, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = TextDecoration.Underline, color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        val meta = buildString {
                            append("  |  ").append(formatTime(song.durationMs))
                            if (song.bitrateKbps > 0) append("  |  ").append(song.bitrateKbps).append("kbps")
                        }
                        Text(meta, fontSize = 15.sp, maxLines = 1, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                IconButton(onClick = { dialog = SheetDialog.Info }) { Icon(Icons.Rounded.Info, "Info") }
                IconButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/mpeg"
                        putExtra(Intent.EXTRA_STREAM, song.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(i, "Share song"))
                }) { Icon(Icons.Rounded.Share, "Share") }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Rounded.Notifications, "Set as ringtone", Modifier.weight(1f)) { vm.setRingtone(song) }
                Tile(Icons.Rounded.Image, "Change cover", Modifier.weight(1f)) {
                    coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                Tile(Icons.Rounded.LocalOffer, "Edit tags", Modifier.weight(1f)) { dialog = SheetDialog.Tags }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Icons.Rounded.Album, "Go to album", Modifier.weight(1f)) { onGoAlbum() }
                Tile(Icons.Rounded.VisibilityOff, "Hide song", Modifier.weight(1f)) { vm.hideSong(song.id); onDismiss() }
                Tile(Icons.Rounded.Delete, "Delete from device", Modifier.weight(1f)) { vm.deleteFromDevice(song); onDismiss() }
            }

            Divider()
            SettingRow(Icons.Rounded.PlaylistAdd, "Add to playlist") { dialog = SheetDialog.Playlist }
            Divider()
            SettingRow(Icons.Rounded.Alarm, "Sleep timer", if (sleepOn) "On" else "Off") { dialog = SheetDialog.Sleep }
            SettingRow(Icons.Rounded.Speed, "Speed", speedLabel(play.speed)) { dialog = SheetDialog.Speed }
            SettingRow(Icons.Rounded.Tune, "Playback settings") { onDismiss(); onOpenEq() }
            Divider()
            SettingRow(Icons.Rounded.WbSunny, "Keep screen on", trailing = {
                Switch(checked = keepOn, onCheckedChange = { vm.keepScreenOn.value = it })
            }) { vm.keepScreenOn.value = !keepOn }
            SettingRow(Icons.Rounded.Settings, "Settings") { dialog = SheetDialog.Settings }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeUp, null, Modifier.padding(horizontal = 8.dp))
                Slider(
                    value = vol,
                    onValueChange = {
                        vol = it
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * maxVol).roundToInt(), 0)
                    },
                    modifier = Modifier.weight(1f),
                )
                Text("${(vol * 100).roundToInt()}%", fontSize = 20.sp, modifier = Modifier.padding(start = 10.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                    val selected = play.speed == s
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, if (selected) accent else Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .background(if (selected) accent.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { vm.setSpeed(if (selected) 1f else s) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("${(s * 100).roundToInt()}%", fontSize = 18.sp, color = Color.White.copy(alpha = 0.8f)) }
                }
            }
        }
    }

    when (dialog) {
        SheetDialog.Playlist -> PlaylistDialog(vm, song) { dialog = null }
        SheetDialog.Sleep -> InfoDialog("Sleep timer", { dialog = null }) {
            Column {
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    Text(
                        "$m minutes", fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth().clickable { vm.setSleepTimer(m); dialog = null }.padding(vertical = 10.dp),
                    )
                }
                if (sleepOn) {
                    Text(
                        "Turn off timer", fontSize = 20.sp, color = accent,
                        modifier = Modifier.fillMaxWidth().clickable { vm.setSleepTimer(null); dialog = null }.padding(vertical = 10.dp),
                    )
                }
            }
        }
        SheetDialog.Speed -> InfoDialog("Playback speed", { dialog = null }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                    FilterChip(selected = play.speed == s, onClick = { vm.setSpeed(s); dialog = null }, label = { Text(speedLabel(s)) })
                }
            }
        }
        SheetDialog.Tags -> TagsDialog(vm, song) { dialog = null }
        SheetDialog.Info -> InfoDialog("Song details", { dialog = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("Title", song.title)
                DetailLine("Artist", song.artist)
                DetailLine("Album", song.album)
                if (song.year > 0) DetailLine("Year", song.year.toString())
                if (song.track > 0) DetailLine("Track", song.track.toString())
                DetailLine("Duration", formatTime(song.durationMs))
                DetailLine("Format", "MP3" + if (song.bitrateKbps > 0) " · ${song.bitrateKbps} kbps" else "")
                DetailLine("Size", "%.1f MB".format(song.sizeBytes / 1_048_576.0))
                DetailLine("Path", song.path)
            }
        }
        SheetDialog.Settings -> SettingsDialog(vm) { dialog = null }
        null -> {}
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(16.dp)).background(TileColor),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(26.dp)) }
        Text(label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(TileColor), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 22.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 17.sp, color = Color.White.copy(alpha = 0.55f))
        trailing?.invoke()
    }
}

@Composable
private fun DetailLine(k: String, v: String) {
    Column {
        Text(k, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
        Text(v, fontSize = 17.sp)
    }
}

@Composable
private fun InfoDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF22214F),
        title = { Text(title) },
        text = { Box(Modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PlaylistDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    InfoDialog("Add to playlist", onDismiss) {
        Column {
            playlists.keys.forEach { n ->
                Text(
                    n, fontSize = 20.sp,
                    modifier = Modifier.fillMaxWidth().clickable { vm.addToPlaylist(n, song.id); onDismiss() }.padding(vertical = 10.dp),
                )
            }
            OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("New playlist") })
            TextButton(onClick = { if (newName.isNotBlank()) { vm.createPlaylist(newName, song.id); onDismiss() } }) {
                Text("Create and add")
            }
        }
    }
}

@Composable
private fun TagsDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF22214F),
        title = { Text("Edit tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("Title") })
                OutlinedTextField(artist, { artist = it }, singleLine = true, label = { Text("Artist") })
                OutlinedTextField(album, { album = it }, singleLine = true, label = { Text("Album") })
                Text("Changes are saved inside this app; the MP3 file itself is not modified.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
            }
        },
        confirmButton = { TextButton(onClick = { vm.saveTags(song, title, artist, album); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    InfoDialog("Settings", onDismiss) {
        Column {
            Text(
                "Rescan library", fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth().clickable { vm.loadLibrary(); onDismiss() }.padding(vertical = 10.dp),
            )
            Text(
                "Restore hidden songs (${hidden.size})", fontSize = 20.sp,
                modifier = Modifier.fillMaxWidth().clickable { vm.unhideAll(); onDismiss() }.padding(vertical = 10.dp),
            )
            Text("Only MP3 music files are shown. Voice recordings and WAV files are ignored.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val songs by vm.songs.collectAsStateWithLifecycle()
    val play by vm.playState.collectAsStateWithLifecycle()
    val byId = remember(songs) { songs.associateBy { it.id } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor) {
        Text("Now playing queue (${queue.size})", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxHeight(0.7f).navigationBarsPadding()) {
            itemsIndexed(queue) { i, id ->
                val s = byId[id] ?: return@itemsIndexed
                SongRow(s, s.id == play.songId, onClick = { vm.skipTo(i) }, onMore = {})
            }
        }
    }
}
