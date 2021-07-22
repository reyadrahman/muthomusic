package com.mutho.music.ui.detail.artist

import android.content.Context
import android.support.v4.util.Pair
import android.view.MenuItem
import com.mutho.music.model.Album
import com.mutho.music.model.AlbumArtist
import com.mutho.music.model.Playlist
import com.mutho.music.model.Song
import com.mutho.music.playback.MediaManager
import com.mutho.music.rx.UnsafeAction
import com.mutho.music.ui.presenters.Presenter
import com.mutho.music.utils.Operators
import com.mutho.music.utils.PermissionUtils
import com.mutho.music.utils.PlaylistUtils
import com.mutho.music.utils.sorting.SortManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers

class ArtistDetailPresenter constructor(private val mediaManager: MediaManager, private val albumArtist: AlbumArtist) : Presenter<ArtistDetailView>() {

    private var songs: MutableList<Song> = mutableListOf()

    private fun sortSongs(songs: MutableList<Song>) {
        @SortManager.SongSort val songSort = SortManager.getInstance().artistDetailSongsSortOrder

        val songsAscending = SortManager.getInstance().artistDetailSongsAscending

        SortManager.getInstance().sortSongs(songs, songSort)
        if (!songsAscending) {
            songs.reverse()
        }
    }

    private fun sortAlbums(albums: MutableList<Album>) {
        @SortManager.AlbumSort val albumSort = SortManager.getInstance().artistDetailAlbumsSortOrder

        val albumsAscending = SortManager.getInstance().artistDetailAlbumsAscending

        SortManager.getInstance().sortAlbums(albums, albumSort)
        if (!albumsAscending) {
            albums.reverse()
        }
    }

    fun loadData() {
        PermissionUtils.RequestStoragePermissions {
            addDisposable(
                albumArtist.songsSingle
                    .zipWith<MutableList<Album>, Pair<MutableList<Album>, MutableList<Song>>>(
                        albumArtist.songsSingle.map { songs -> Operators.songsToAlbums(songs) },
                        BiFunction { songs, albums -> Pair(albums, songs) }).subscribeOn(Schedulers.io())
                    .doOnSuccess { pair ->
                        sortAlbums(pair.first!!)
                        sortSongs(pair.second!!)
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { pair ->
                        this.songs = pair.second!!

                        view?.setData(pair)
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
        mediaManager.playAll(songs, songs.indexOf(song), true) { message ->
            view?.showToast(message)
        }
    }
}
