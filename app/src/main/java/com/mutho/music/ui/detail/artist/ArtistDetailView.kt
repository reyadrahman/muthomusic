package com.mutho.music.ui.detail.artist

import android.support.v4.util.Pair

import com.mutho.music.model.Album
import com.mutho.music.model.Song

interface ArtistDetailView {

    fun setData(data: Pair<MutableList<Album>, MutableList<Song>>)

    fun showToast(message: String)

    fun showTaggerDialog()

    fun showDeleteDialog()

    fun showArtworkDialog()

    fun showBioDialog()

    @JvmSuppressWildcards
    fun showCreatePlaylistDialog(songs: List<Song>)

    fun closeContextualToolbar()
}
