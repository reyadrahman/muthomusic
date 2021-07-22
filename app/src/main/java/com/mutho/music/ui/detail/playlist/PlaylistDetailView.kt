package com.mutho.music.ui.detail.playlist

import com.mutho.music.model.Album
import com.mutho.music.model.Song

interface PlaylistDetailView {

    fun setData(data: MutableList<Song>)

    fun showToast(message: String)

    @JvmSuppressWildcards
    fun showCreatePlaylistDialog(songs: List<Song>)

    fun closeContextualToolbar()

    fun fadeInSlideShowAlbum(previousAlbum: Album?, newAlbum: Album)
}