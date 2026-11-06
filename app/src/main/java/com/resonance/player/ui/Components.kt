package com.resonance.player.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resonance.player.data.ArtLoader
import com.resonance.player.data.Song

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun AlbumArt(song: Song?, px: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, song?.id, song?.coverStamp, px) {
        value = if (song == null) null else ArtLoader.load(ctx, song, px)
    }
    Box(modifier.background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}

@Composable
fun QualityBadge(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier
            .padding(start = 8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
fun SongRow(song: Song, isCurrent: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    val accent = LocalPalette.current.accent
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(song, 160, Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) accent else Color.White,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${song.artist} - ${song.album}",
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.bitrateKbps >= 320) QualityBadge("320K")
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Rounded.MoreVert, "More", tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onQueue: () -> Unit,
    onOpen: () -> Unit,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(12_000, easing = LinearEasing))
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .graphicsLayer { rotationZ = rotation.value }
                .clip(CircleShape)
                .background(Color(0xFF14141C))
                .padding(5.dp),
        ) {
            AlbumArt(song, 160, Modifier.fillMaxSize().clip(CircleShape))
            Box(Modifier.size(12.dp).align(Alignment.Center).clip(CircleShape).background(Color(0xFF14141C)))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${song.artist} - ${song.album}",
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .border(2.dp, LocalPalette.current.accent, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(30.dp))
        }
        IconButton(onClick = onQueue, modifier = Modifier.padding(start = 6.dp)) {
            Icon(Icons.Rounded.QueueMusic, "Queue", Modifier.size(30.dp))
        }
    }
}

@Composable
fun EmptyState(loaded: Boolean) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            if (loaded) "No MP3 files found.\nDownload some music and it will appear here." else "Scanning for MP3 files…",
            fontSize = 20.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}
