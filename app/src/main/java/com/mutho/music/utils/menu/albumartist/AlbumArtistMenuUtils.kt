package com.mutho.music.utils.menu.albumartist

import android.content.Context
import android.support.v7.widget.PopupMenu
import android.support.v7.widget.Toolbar
import com.mutho.music.R
import com.mutho.music.model.AlbumArtist
import com.mutho.music.model.Playlist
import com.mutho.music.model.Song
import com.mutho.music.playback.MediaManager
import com.mutho.music.playback.MediaManager.Defs
import com.mutho.music.utils.Operators
import com.mutho.music.utils.PlaylistUtils
import com.mutho.music.utils.extensions.getSongs
import com.mutho.music.utils.menu.MenuUtils
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers

object AlbumArtistMenuUtils {

    interface Callbacks {

        fun onPlaylistItemsInserted()

        fun onQueueItemsInserted(message: String)

        fun playNext(songsSingle: Single<List<Song>>)

        fun showTagEditor(albumArtist: AlbumArtist)

        fun showDeleteDialog(albumArtist: AlbumArtist)

        fun showDeleteDialog(albumArtists: List<AlbumArtist>)

        fun showDeleteDialog(albumArtists: Single<List<AlbumArtist>>)

        fun showAlbumArtistInfo(albumArtist: AlbumArtist)

        fun showArtworkChooser(albumArtist: AlbumArtist)

        fun showToast(message: String)
    }

    fun getAlbumArtistMenuClickListener(context: Context, mediaManager: MediaManager, selectedAlbumArtists: Single<List<AlbumArtist>>, callbacks: Callbacks): Toolbar.OnMenuItemClickListener {
        return Toolbar.OnMenuItemClickListener { item ->
            when (item.itemId) {
                Defs.NEW_PLAYLIST -> {
                    MenuUtils.newPlaylist(context, selectedAlbumArtists.getSongs()) { callbacks.onPlaylistItemsInserted() }
                    return@OnMenuItemClickListener true
                }
                Defs.PLAYLIST_SELECTED -> {
                    MenuUtils.addToPlaylist(context, item.intent.getSerializableExtra(PlaylistUtils.ARG_PLAYLIST) as Playlist, selectedAlbumArtists.getSongs()) { callbacks.onPlaylistItemsInserted() }
                    return@OnMenuItemClickListener true
                }
                R.id.playNext -> {
                    callbacks.playNext(selectedAlbumArtists.getSongs())
                    return@OnMenuItemClickListener true
                }
                R.id.addToQueue -> {
                    MenuUtils.addToQueue(mediaManager, selectedAlbumArtists.getSongs()) { callbacks.onQueueItemsInserted(it) }
                    return@OnMenuItemClickListener true
                }
                R.id.delete -> {
                    callbacks.showDeleteDialog(selectedAlbumArtists)
                    return@OnMenuItemClickListener true
                }
            }
            false
        }
    }

    fun getAlbumArtistClickListener(context: Context, mediaManager: MediaManager, albumArtist: AlbumArtist, callbacks: Callbacks): PopupMenu.OnMenuItemClickListener {
        return PopupMenu.OnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.play -> {
                    MenuUtils.play(mediaManager, albumArtist.songsSingle) { callbacks.showToast(it) }
                    return@OnMenuItemClickListener true
                }
                R.id.playNext -> {
                    callbacks.playNext(albumArtist.songsSingle)
                    albumArtist.songsSingle
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { songs -> MenuUtils.playNext(mediaManager, songs) { callbacks.showToast(it) } }
                    return@OnMenuItemClickListener true
                }
                R.id.albumShuffle -> {
                    MenuUtils.play(
                        mediaManager,
                        albumArtist.songsSingle.map { Operators.albumShuffleSongs(it) }
                    ) { callbacks.showToast(it) }
                    return@OnMenuItemClickListener true
                }
                Defs.NEW_PLAYLIST -> {
                    MenuUtils.newPlaylist(context, albumArtist.songsSingle) { callbacks.onPlaylistItemsInserted() }
                    return@OnMenuItemClickListener true
                }
                Defs.PLAYLIST_SELECTED -> {
                    MenuUtils.addToPlaylist(context, item.intent.getSerializableExtra(PlaylistUtils.ARG_PLAYLIST) as Playlist, albumArtist.songsSingle) { callbacks.onPlaylistItemsInserted() }
                    return@OnMenuItemClickListener true
                }
                R.id.addToQueue -> {
                    MenuUtils.addToQueue(mediaManager, albumArtist.songsSingle) { callbacks.onQueueItemsInserted(it) }
                    return@OnMenuItemClickListener true
                }
                R.id.editTags -> {
                    callbacks.showTagEditor(albumArtist)
                    return@OnMenuItemClickListener true
                }
                R.id.info -> {
                    callbacks.showAlbumArtistInfo(albumArtist)
                    return@OnMenuItemClickListener true
                }
                R.id.artwork -> {
                    callbacks.showArtworkChooser(albumArtist)
                    return@OnMenuItemClickListener true
                }
                R.id.blacklist -> {
                    MenuUtils.blacklist(albumArtist.songsSingle)
                    return@OnMenuItemClickListener true
                }
                R.id.delete -> {
                    callbacks.showDeleteDialog(albumArtist)
                    return@OnMenuItemClickListener true
                }
            }
            false
        }
    }
}
