package com.resonance.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resonance.player.PlayerViewModel
import com.resonance.player.Route
import com.resonance.player.data.Song
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(vm: PlayerViewModel, onOpen: (Route) -> Unit, onMore: (Song) -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val play by vm.playState.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    val tabs = listOf("For you", "Songs", "Playlists", "Folders", "Albums")
    val pager = rememberPagerState(initialPage = 1) { tabs.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEachIndexed { i, label ->
                val selected = pager.currentPage == i
                Column(
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { scope.launch { pager.animateScrollToPage(i) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        fontSize = if (selected) 32.sp else 25.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .width(38.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                    }
                }
            }
        }

        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            when (page) {
                0 -> ForYouTab(
                    songs = songs,
                    favoritesCount = songs.count { it.id in favorites },
                    onShuffle = { vm.shuffleAll(songs) },
                    onPlay = { list, i -> vm.playSongs(list, i) },
                    onFavorites = { onOpen(Route.Favorites) },
                )
                1 -> if (songs.isEmpty()) EmptyState(loaded) else LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                    itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                        SongRow(s, s.id == play.songId, onClick = { vm.playSongs(songs, i) }, onMore = { onMore(s) })
                    }
                }
                2 -> PlaylistsTab(
                    playlists = playlists,
                    favoritesCount = songs.count { it.id in favorites },
                    onOpen = onOpen,
                    onCreate = { vm.createPlaylist(it) },
                )
                3 -> FoldersTab(songs, onOpen)
                else -> AlbumsTab(songs, onOpen)
            }
        }
    }
}

@Composable
private fun ForYouTab(
    songs: List<Song>,
    favoritesCount: Int,
    onShuffle: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onFavorites: () -> Unit,
) {
    val accent = LocalPalette.current.accent
    val recent = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(20) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Button(
            onClick = onShuffle,
            enabled = songs.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Shuffle, null)
            Spacer(Modifier.width(8.dp))
            Text("Shuffle all  (${songs.size} songs)", fontSize = 20.sp)
        }
        Spacer(Modifier.height(20.dp))
        if (recent.isNotEmpty()) {
            Text("Recently added", fontSize = 26.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(recent) { i, s ->
                    Column(Modifier.width(124.dp).clickable { onPlay(recent, i) }) {
                        AlbumArt(s, 300, Modifier.size(124.dp).clip(RoundedCornerShape(14.dp)))
                        Text(s.title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(s.artist, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.55f))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onFavorites).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Favorite, null, tint = accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Favorites", fontSize = 22.sp)
                Text("$favoritesCount songs", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: Map<String, List<Long>>,
    favoritesCount: Int,
    onOpen: (Route) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        item { ListRow(Icons.Rounded.Add, "New playlist", "") { creating = true } }
        item { ListRow(Icons.Rounded.Favorite, "Favorites", "$favoritesCount songs") { onOpen(Route.Favorites) } }
        items(playlists.keys.toList()) { n ->
            ListRow(Icons.Rounded.QueueMusic, n, "${playlists[n].orEmpty().size} songs") { onOpen(Route.Playlist(n)) }
        }
    }
    if (creating) NameDialog("New playlist", onDismiss = { creating = false }) { onCreate(it); creating = false }
}

@Composable
private fun FoldersTab(songs: List<Song>, onOpen: (Route) -> Unit) {
    val folders = remember(songs) { songs.groupBy { it.folderPath }.toList().sortedBy { it.first.lowercase() } }
    if (folders.isEmpty()) { EmptyState(true); return }
    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
        items(folders) { (path, list) ->
            ListRow(Icons.Rounded.Folder, list.first().folderName, "${list.size} songs  ·  $path") { onOpen(Route.Folder(path)) }
        }
    }
}

@Composable
private fun AlbumsTab(songs: List<Song>, onOpen: (Route) -> Unit) {
    val albums = remember(songs) { songs.groupBy { it.album }.toList().sortedBy { it.first.lowercase() } }
    if (albums.isEmpty()) { EmptyState(true); return }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        gridItems(albums) { (name, list) ->
            Column(Modifier.clickable { onOpen(Route.Album(name)) }) {
                AlbumArt(list.first(), 400, Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)))
                Text(name, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "${list.first().artist} · ${list.size} songs",
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun ListRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = LocalPalette.current.accent) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF22214F),
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Album / folder / playlist / favourites detail page. */
@Composable
fun DetailRoute(vm: PlayerViewModel, route: Route, onBack: () -> Unit, onMore: (Song) -> Unit) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val play by vm.playState.collectAsStateWithLifecycle()

    val byId = remember(songs) { songs.associateBy { it.id } }
    val (title, subtitle, list) = when (route) {
        is Route.Album -> Triple(route.name, "Album", songs.filter { it.album == route.name })
        is Route.Folder -> Triple(route.path.substringAfterLast('/').ifBlank { "Folder" }, route.path, songs.filter { it.folderPath == route.path })
        is Route.Playlist -> Triple(route.name, "Playlist", playlists[route.name].orEmpty().mapNotNull { byId[it] })
        else -> Triple("Favorites", "Songs you've hearted", songs.filter { it.id in favorites })
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$subtitle · ${list.size} songs", fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.55f),
                )
            }
            if (route is Route.Playlist) {
                IconButton(onClick = { vm.deletePlaylist(route.name); onBack() }) { Icon(Icons.Rounded.Delete, "Delete playlist") }
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val accent = LocalPalette.current.accent
            Button(
                onClick = { vm.playSongs(list, 0) }, enabled = list.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
            ) { Icon(Icons.Rounded.PlayArrow, null); Text(" Play", fontSize = 18.sp) }
            Button(
                onClick = { vm.shuffleAll(list) }, enabled = list.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White),
            ) { Icon(Icons.Rounded.Shuffle, null); Text(" Shuffle", fontSize = 18.sp) }
        }
        if (list.isEmpty()) {
            EmptyState(true)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, s.id == play.songId, onClick = { vm.playSongs(list, i) }, onMore = { onMore(s) })
                }
            }
        }
    }
}
