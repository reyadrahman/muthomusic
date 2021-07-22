package com.mutho.music.ui.detail.album

import android.content.Context
import android.view.MenuItem
import com.mutho.music.model.Album
import com.mutho.music.model.Playlist
import com.mutho.music.model.Song
import com.mutho.music.playback.MediaManager
import com.mutho.music.rx.UnsafeAction
import com.mutho.music.ui.presenters.Presenter
import com.mutho.music.utils.PermissionUtils
import com.mutho.music.utils.PlaylistUtils
import com.mutho.music.utils.sorting.SortManager
import io.reactivex.android.schedulers.AndroidSchedulers

class AlbumDetailPresenter constructor(private val mediaManager: MediaManager, private val album: Album) : Presenter<AlbumDetailView>() {

    private var songs: MutableList<Song> = mutableListOf()

    private fun sortSongs(songs: MutableList<Song>) {
        @SortManager.SongSort val songSort = SortManager.getInstance().albumDetailSongsSortOrder

        val songsAscending = SortManager.getInstance().albumDetailSongsAscending

        SortManager.getInstance().sortSongs(songs, songSort)
        if (!songsAscending) {
            songs.reverse()
        }
    }

    fun loadData() {
        PermissionUtils.RequestStoragePermissions {
            addDisposable(
                album.songsSingle
                    .doOnSuccess { sortSongs(it) }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { songs ->
                        this.songs = songs
                        view?.setData(songs)
                    }
            )
        }
    }

    fun closeContextualToolbar() {
        view?.closeContextualToolbar()
    }

    fun fabClicked() {
        mediaManager.shuffleAll(songs) { message ->
            view?.showToast(message)
        }
    }

    fun playAll() {
        mediaManager.playAll(songs, 0, true) { message ->
            view?.showToast(message)
        }
    }

    fun playNext() {
        mediaManager.playNext(songs) { message ->
            view?.showToast(message)
        }
    }

    fun addToQueue() {
        mediaManager.addToQueue(songs) { message ->
            view?.showToast(message)
        }
    }

    fun editTags() {
        view?.showTaggerDialog()
    }

    fun editArtwork() {
        view?.showArtworkDialog()
    }

    fun showBio() {
        view?.showBioDialog()
    }

    fun newPlaylist() {
        view?.showCreatePlaylistDialog(songs.toList())
    }

    fun playlistSelected(context: Context, item: MenuItem, insertCallback: UnsafeAction) {
        val playlist = item.intent.getSerializableExtra(PlaylistUtils.ARG_PLAYLIST) as Playlist
        PermissionUtils.RequestStoragePermissions {
            PlaylistUtils.addToPlaylist(context, playlist, songs, insertCallback)
        }
    }

    fun songClicked(song: Song) {
        mediaManager.playAll(songs, songs.indexOf(song), true) { view?.showToast(it) }
    }
}
