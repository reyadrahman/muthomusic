package com.mutho.music.utils.menu.albumartist

import android.widget.Toast
import com.mutho.music.model.AlbumArtist
import com.mutho.music.model.Song
import com.mutho.music.tagger.TaggerDialog
import com.mutho.music.ui.dialog.BiographyDialog
import com.mutho.music.ui.dialog.DeleteDialog
import com.mutho.music.ui.fragments.BaseFragment
import com.mutho.music.utils.ArtworkDialog
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable

class AlbumArtistMenuCallbacksAdapter(val fragment: BaseFragment, val disposables: CompositeDisposable) : AlbumArtistMenuUtils.Callbacks {

    override fun onPlaylistItemsInserted() {

    }

    override fun onQueueItemsInserted(message: String) {

    }

    override fun playNext(songsSingle: Single<List<Song>>) {
        fragment.mediaManager.playNext(songsSingle) { message -> Toast.makeText(fragment.context, message, Toast.LENGTH_LONG).show() }
    }

    override fun showTagEditor(albumArtist: AlbumArtist) {
        TaggerDialog.newInstance(albumArtist).show(fragment.childFragmentManager)
    }

    override fun showDeleteDialog(albumArtist: AlbumArtist) {
        DeleteDialog.newInstance(DeleteDialog.ListArtistsRef { listOf(albumArtist) }).show(fragment.childFragmentManager)
    }

    override fun showDeleteDialog(albumArtists: List<AlbumArtist>) {
        DeleteDialog.newInstance(DeleteDialog.ListArtistsRef { albumArtists }).show(fragment.childFragmentManager)
    }

    override fun showDeleteDialog(albumArtists: Single<List<AlbumArtist>>) {
        disposables.add(albumArtists.subscribe { albumArtistsList ->
            DeleteDialog.newInstance(DeleteDialog.ListArtistsRef { albumArtistsList })
        })
    }

    override fun showAlbumArtistInfo(albumArtist: AlbumArtist) {
        BiographyDialog.getArtistBiographyDialog(fragment.context, albumArtist.name).show()
    }

    override fun showArtworkChooser(albumArtist: AlbumArtist) {
        ArtworkDialog.build(fragment.context, albumArtist).show()
    }

    override fun showToast(message: String) {
        Toast.makeText(fragment.context, message, Toast.LENGTH_LONG).show()
    }
}

