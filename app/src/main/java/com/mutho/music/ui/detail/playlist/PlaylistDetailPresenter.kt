package com.mutho.music.ui.detail.playlist

import com.mutho.music.model.Album
import com.mutho.music.model.Playlist
import com.mutho.music.model.Song
import com.mutho.music.playback.MediaManager
import com.mutho.music.ui.presenters.Presenter
import com.mutho.music.utils.ComparisonUtils
import com.mutho.music.utils.LogUtils
import com.mutho.music.utils.Operators
import com.mutho.music.utils.PermissionUtils
import com.mutho.music.utils.sorting.SortManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.Random
import java.util.concurrent.TimeUnit

class PlaylistDetailPresenter constructor(private val mediaManager: MediaManager, private val playlist: Playlist) : Presenter<PlaylistDetailView>() {

    private var songs: MutableList<Song> = mutableListOf()

    private var currentSlideShowAlbum: Album? = null

    override fun bindView(view: PlaylistDetailView) {
        super.bindView(view)

        startSlideShow()
    }

    private fun sortSongs(songs: MutableList<Song>) {
        @SortManager.SongSort val songSort = SortManager.getInstance().getPlaylistDetailSongsSortOrder(playlist)

        val songsAscending = SortManager.getInstance().getPlaylistDetailSongsAscending(playlist)

        SortManager.getInstance().sortSongs(songs, songSort)
        if (!songsAscending) {
            songs.reverse()
        }

        if (songSort == SortManager.SongSort.DETAIL_DEFAULT) {
            when {
                playlist.type == Playlist.Type.MOST_PLAYED -> songs.sortWith(kotlin.Comparator { a, b -> ComparisonUtils.compareInt(b.playCount, a.playCount) })
                playlist.type == Playlist.Type.RECENTLY_ADDED -> songs.sortWith(kotlin.Comparator { a, b -> ComparisonUtils.compareInt(b.dateAdded, a.dateAdded) })
                playlist.type == Playlist.Type.RECENTLY_PLAYED -> songs.sortWith(kotlin.Comparator { a, b -> ComparisonUtils.compareLong(b.lastPlayed, a.lastPlayed) })
            }
            if (playlist.canEdit) {
                songs.sortWith(kotlin.Comparator { a, b -> ComparisonUtils.compareLong(a.playlistSongPlayOrder, b.playlistSongPlayOrder) })
            }
        }
    }

    fun loadData() {
        PermissionUtils.RequestStoragePermissions {
            addDisposable(
                playlist.songsObservable
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnNext { songs -> sortSongs(songs) }
                    .subscribe({ songs ->
                        this.songs = songs
                        view?.setData(songs)
                    }, { error ->
                        LogUtils.logException(TAG, "loadData error", error);
                    })
            )
        }
    }

    private fun startSlideShow() {
        val albumsObservable: Observable<List<Album>> = playlist.songsObservable
            .map { songs -> Operators.songsToAlbums(songs) }

        val timer: Observable<Long> = io.reactivex.Observable.interval(8, TimeUnit.SECONDS)
            // Load an image straight away
            .startWith(0L)
            // If we have a 'current slideshowAlbum' then we're coming back from onResume. Don't load a new one immediately.
            .delay(if (currentSlideShowAlbum == null) 0L else 8L, TimeUnit.SECONDS)

        addDisposable(Observable
            .combineLatest(albumsObservable, timer, BiFunction { albums: List<Album>, _: Long -> albums })
            .map { albums ->
                if (albums.isEmpty()) {
                    currentSlideShowAlbum
                } else {
                    albums[(Random().nextInt(albums.size))]
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ newAlbum ->
                newAlbum?.let {
                    view?.fadeInSlideShowAlbum(currentSlideShowAlbum, newAlbum)
                    currentSlideShowAlbum = newAlbum
                }
            }, { error ->
                LogUtils.logException(TAG, "startSlideShow threw error", error)
            })
        )
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

    fun newPlaylist() {
        view?.showCreatePlaylistDialog(songs.toList())
    }

    fun songClicked(song: Song) {
        mediaManager.playAll(songs, songs.indexOf(song), true) { message ->
            view?.showToast(message)
        }
    }

    companion object {
        const val TAG = "PlaylistDetailPresenter"
    }
}
