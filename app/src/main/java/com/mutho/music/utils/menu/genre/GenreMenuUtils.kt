package com.mutho.music.utils.menu.genre

import android.content.Context
import android.support.v7.widget.PopupMenu
import com.mutho.music.R
import com.mutho.music.model.Genre
import com.mutho.music.model.Playlist
import com.mutho.music.playback.MediaManager
import com.mutho.music.playback.MediaManager.Defs
import com.mutho.music.utils.PlaylistUtils
import com.mutho.music.utils.extensions.getSongs
import com.mutho.music.utils.menu.MenuUtils

object GenreMenuUtils {

    interface Callbacks {

        fun showToast(message: String)

        fun onPlaylistItemsInserted()

        fun onQueueItemsInserted(message: String)

        fun playNext(genre: Genre)
    }

    fun getGenreClickListener(context: Context, mediaManager: MediaManager, genre: Genre, callbacks: Callbacks): PopupMenu.OnMenuItemClickListener {
        return PopupMenu.OnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.play -> {
                    MenuUtils.play(mediaManager, genre.getSongs(), { callbacks.showToast(it) })
                    return@OnMenuItemClickListener true
                }
                R.id.playNext -> {
                    callbacks.playNext(genre)
                    return@OnMenuItemClickListener true
                }
                Defs.NEW_PLAYLIST -> {
                    MenuUtils.newPlaylist(context, genre.getSongs(), { callbacks.onPlaylistItemsInserted() })
                    return@OnMenuItemClickListener true
                }
                Defs.PLAYLIST_SELECTED -> {
                    MenuUtils.addToPlaylist(context, item.intent.getSerializableExtra(PlaylistUtils.ARG_PLAYLIST) as Playlist, genre.getSongs(), { callbacks.onPlaylistItemsInserted() })
                    return@OnMenuItemClickListener true
                }
                R.id.addToQueue -> {
                    MenuUtils.addToQueue(mediaManager, genre.getSongs(), { callbacks.onQueueItemsInserted(it) })
                    return@OnMenuItemClickListener true
                }
            }
            false
        }
    }
}