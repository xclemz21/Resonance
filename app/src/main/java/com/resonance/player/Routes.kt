package com.resonance.player

sealed interface Route {
    data object Library : Route
    data object Equalizer : Route
    data object Favorites : Route
    data class Album(val name: String) : Route
    data class Folder(val path: String) : Route
    data class Playlist(val name: String) : Route
}
