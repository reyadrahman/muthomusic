package com.mutho.music.ui.modelviews;

import android.view.View;
import android.view.ViewGroup;
import com.muthomusicapps.recycler_adapter.model.BaseViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.BaseViewHolder;

import static com.mutho.music.R.layout.list_item_artwork_loading;
import static com.mutho.music.ui.adapters.ViewType.LOADING;

public class ArtworkLoadingView extends BaseViewModel<ArtworkLoadingView.ViewHolder> {

    @Override
    public int getViewType() {
        return LOADING;
    }

    @Override
    public int getLayoutResId() {
        return list_item_artwork_loading;
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        return new ViewHolder(createView(parent));
    }

    public static class ViewHolder extends BaseViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }

        @Override
        public String toString() {
            return "ArtworkLoadingView.ViewHolder";
        }
    }
}