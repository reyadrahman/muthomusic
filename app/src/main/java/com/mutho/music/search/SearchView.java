package com.mutho.music.search;

import android.support.annotation.NonNull;
import android.view.View;
import com.mutho.music.model.Album;
import com.mutho.music.model.AlbumArtist;
import com.mutho.music.tagger.TaggerDialog;
import com.mutho.music.ui.dialog.DeleteDialog;

public interface SearchView {

    void setLoading(boolean loading);

    void setData(SearchResult searchResult);

    void setFilterFuzzyChecked(boolean checked);

    void setFilterArtistsChecked(boolean checked);

    void setFilterAlbumsChecked(boolean checked);

    void showToast(String message);

    void showTaggerDialog(@NonNull TaggerDialog taggerDialog);

    void showDeleteDialog(@NonNull DeleteDialog deleteDialog);

    void goToArtist(AlbumArtist albumArtist, View transitionView);

    void goToAlbum(Album album, View transitionView);
}