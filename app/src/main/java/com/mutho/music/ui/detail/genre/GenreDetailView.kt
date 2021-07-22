package com.mutho.music.ui.detail.genre

import android.support.v4.util.Pair

import com.mutho.music.model.Album
import com.mutho.music.model.Song

interface GenreDetailView {

    fun setData(data: Pair<MutableList<Album>, MutableList<Song>>)

    fun showToast(message: String)

    @JvmSuppressWildcards
    fun showCreatePlaylistDialog(songs: List<Song>)

    fun closeContextualToolbar()

    fun fadeInSlideShowAlbum(previousAlbum: Album?, newAlbum: Album)

}