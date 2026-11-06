package com.resonance.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.resonance.player.PlayerViewModel
import com.resonance.player.audio.EffectsController
import com.resonance.player.data.ArtLoader
import com.resonance.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    song: Song,
    onClose: () -> Unit,
    onMore: () -> Unit,
    onOpenEq: () -> Unit,
    onQueue: () -> Unit,
) {
    val ctx = LocalContext.current
    val play by vm.playState.collectAsStateWithLifecycle()
    val position by vm.position.collectAsStateWithLifecycle()
    val duration by vm.duration.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val eq by EffectsController.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val accent = LocalPalette.current.accent

    val bitmap by produceState<Bitmap?>(null, song.id, song.coverStamp) { value = ArtLoader.load(ctx, song, 800) }
    val colors by produceState(listOf(Color(0xFF3B3B5C), Color(0xFF15152B)), bitmap) {
        val b = bitmap ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val p = Palette.from(b).maximumColorCount(16).generate()
            listOf(
                Color(p.getVibrantColor(p.getMutedColor(0xFF3B3B5C.toInt()))),
                Color(p.getDarkMutedColor(0xFF15152B.toInt())),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors[0].copy(alpha = 0.85f), colors[1], Color.Black.copy(alpha = 0.92f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
                Spacer(Modifier.weight(1f))
                Text(
                    "Song", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (tab == 0) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { tab = 0 },
                )
                Text("  |  ", fontSize = 22.sp, color = Color.White.copy(alpha = 0.4f))
                Text(
                    "Lyrics", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (tab == 1) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { tab = 1 },
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.cycleTheme() }) { Icon(Icons.Rounded.Checkroom, "Theme") }
                IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, "More") }
            }

            Box(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (tab == 0) {
                    AlbumArt(song, 800, Modifier.fillMaxSize())
                } else {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No lyrics found for this song", fontSize = 22.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        song.title, fontSize = 38.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 42.sp,
                    )
                    Text(
                        song.artist, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                        val fav = song.id in favorites
                        Icon(
                            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite",
                            Modifier.size(32.dp), tint = if (fav) accent else Color.White,
                        )
                    }
                    IconButton(onClick = onOpenEq) {
                        Box {
                            Icon(Icons.Rounded.Tune, "Equalizer", Modifier.size(32.dp))
                            if (eq.enabled) {
                                Text(
                                    "ON", fontSize = 10.sp, color = Color.Black,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 10.dp, y = (-6).dp)
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 3.dp),
                                )
                            }
                        }
                    }
                    IconButton(onClick = onQueue) { Icon(Icons.Rounded.QueueMusic, "Queue", Modifier.size(32.dp)) }
                }
            }

            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.seekBack() }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds") }
                SeekBar(position, duration, onSeek = { vm.seekTo(it) }, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.seekForward() }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds") }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, "Shuffle", Modifier.size(30.dp), tint = if (play.shuffle) accent else Color.White)
                }
                IconButton(onClick = { vm.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(42.dp)) }
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(Color.White).clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (play.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        "Play/Pause", Modifier.size(48.dp), tint = Color.Black,
                    )
                }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(42.dp)) }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (play.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat", Modifier.size(30.dp),
                        tint = if (play.repeat == Player.REPEAT_MODE_OFF) Color.White else accent,
                    )
                }
            }
        }
    }
}

/** Seek bar whose thumb is a white pill showing "elapsed / total", like the reference design. */
@Composable
fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    var pillW by remember { mutableIntStateOf(0) }
    val frac = drag ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = (frac * duration).toLong()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(duration) {
                detectTapGestures { o ->
                    if (duration > 0) onSeek((o.x / size.width * duration).toLong().coerceIn(0L, duration))
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { f -> onSeek((f * duration).toLong()) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        drag = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val totalW = constraints.maxWidth
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val radius = CornerRadius(2.dp.toPx())
            val cx = pillW / 2f + (totalW - pillW) * frac
            drawRoundRect(Color.White.copy(alpha = 0.35f), cornerRadius = radius)
            drawRoundRect(Color.White, size = Size(cx, size.height), cornerRadius = radius)
        }
        Text(
            "${formatTime(shown)} / ${formatTime(duration)}",
            color = Color.Black,
            fontSize = 15.sp,
            modifier = Modifier
                .offset { IntOffset(((totalW - pillW) * frac).roundToInt(), 0) }
                .onSizeChanged { pillW = it.width }
                .background(Color.White, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
