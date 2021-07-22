package com.mutho.music.ui.detail.album

import com.mutho.music.model.Song

interface AlbumDetailView {

    fun setData(data: MutableList<Song>)

    fun showToast(message: String)

    fun showTaggerDialog()

    fun showDeleteDialog()

    fun showArtworkDialog()

    fun showBioDialog()

    @JvmSuppressWildcards
    fun showCreatePlaylistDialog(songs: List<Song>)

    fun closeContextualToolbar()
}
