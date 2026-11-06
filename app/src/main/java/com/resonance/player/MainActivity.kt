package com.resonance.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.resonance.player.data.Song
import com.resonance.player.ui.DetailRoute
import com.resonance.player.ui.EqualizerScreen
import com.resonance.player.ui.LibraryScreen
import com.resonance.player.ui.MiniPlayer
import com.resonance.player.ui.NowPlayingScreen
import com.resonance.player.ui.OptionsSheet
import com.resonance.player.ui.Palettes
import com.resonance.player.ui.QueueSheet
import com.resonance.player.ui.ResonanceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[PlayerViewModel::class.java]
    }

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.loadLibrary()
    }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.loadLibrary()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { e ->
                    when (e) {
                        is UiEvent.Delete -> deleteLauncher.launch(IntentSenderRequest.Builder(e.sender).build())
                        is UiEvent.Toast -> Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())

        setContent { ResonanceApp(vm) }
    }

    override fun onStart() {
        super.onStart()
        if (hasAudioPermission()) vm.loadLibrary()
    }

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasAudioPermission(): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun ResonanceApp(vm: PlayerViewModel) {
    val themeIdx by vm.themeIndex.collectAsStateWithLifecycle()
    val palette = Palettes[themeIdx.mod(Palettes.size)]
    val keepOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    val current by vm.currentSong.collectAsStateWithLifecycle()
    val play by vm.playState.collectAsStateWithLifecycle()

    val view = LocalView.current
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    ResonanceTheme(palette) {
        var stack by remember { mutableStateOf<List<Route>>(listOf(Route.Library)) }
        var showPlayer by remember { mutableStateOf(false) }
        var optionsSong by remember { mutableStateOf<Song?>(null) }
        var showQueue by remember { mutableStateOf(false) }

        LaunchedEffect(current == null) { if (current == null) showPlayer = false }
        BackHandler(enabled = showPlayer) { showPlayer = false }
        BackHandler(enabled = !showPlayer && stack.size > 1) { stack = stack.dropLast(1) }

        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(palette.top, palette.bottom)))) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f)) {
                    when (val r = stack.last()) {
                        Route.Library -> LibraryScreen(vm, onOpen = { stack = stack + it }, onMore = { optionsSong = it })
                        Route.Equalizer -> EqualizerScreen(onBack = { stack = stack.dropLast(1) })
                        else -> DetailRoute(vm, r, onBack = { stack = stack.dropLast(1) }, onMore = { optionsSong = it })
                    }
                }
                current?.let { song ->
                    MiniPlayer(
                        song = song,
                        isPlaying = play.isPlaying,
                        onToggle = { vm.togglePlay() },
                        onQueue = { showQueue = true },
                        onOpen = { showPlayer = true },
                    )
                }
                Box(Modifier.navigationBarsPadding())
            }

            AnimatedVisibility(
                visible = showPlayer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                current?.let { song ->
                    NowPlayingScreen(
                        vm = vm,
                        song = song,
                        onClose = { showPlayer = false },
                        onMore = { optionsSong = song },
                        onOpenEq = {
                            showPlayer = false
                            if (stack.last() != Route.Equalizer) stack = stack + Route.Equalizer
                        },
                        onQueue = { showQueue = true },
                    )
                }
            }

            optionsSong?.let { song ->
                OptionsSheet(
                    vm = vm,
                    song = song,
                    onDismiss = { optionsSong = null },
                    onGoAlbum = {
                        optionsSong = null
                        showPlayer = false
                        stack = stack + Route.Album(song.album)
                    },
                    onOpenEq = {
                        showPlayer = false
                        if (stack.last() != Route.Equalizer) stack = stack + Route.Equalizer
                    },
                )
            }
            if (showQueue) QueueSheet(vm, onDismiss = { showQueue = false })
        }
    }
}
