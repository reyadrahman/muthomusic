package com.mutho.music.ui.modelviews;

import com.bumptech.glide.RequestManager;
import com.mutho.music.R;
import com.mutho.music.model.Album;
import com.mutho.music.ui.adapters.ViewType;

public class HorizontalAlbumView extends AlbumView {

    public HorizontalAlbumView(Album album, RequestManager requestManager) {
        super(album, ViewType.ALBUM_CARD, requestManager);
    }

    @Override
    public int getLayoutResId() {
        return R.layout.grid_item_horizontal;
    }
}