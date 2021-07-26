package com.mutho.music.ui.modelviews;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mutho.music.R;
import com.mutho.music.model.Song;
import com.mutho.music.ui.adapters.ViewType;
import com.mutho.music.utils.PlaceholderProvider;
import com.muthomusicapps.recycler_adapter.model.BaseViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.BaseViewHolder;

public class QueuePagerItemView extends BaseViewModel<QueuePagerItemView.ViewHolder> {

    public Song song;
    private RequestManager requestManager;

    public QueuePagerItemView(Song song, RequestManager requestManager) {
        this.song = song;
        this.requestManager = requestManager;
    }

    @Override
    public int getViewType() {
        return ViewType.QUEUE_PAGER_ITEM;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.list_item_queue_pager;
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        return new ViewHolder(createView(parent));
    }

    @Override
    public void bindView(ViewHolder holder) {
        super.bindView(holder);

        requestManager
                .load(song)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .error(PlaceholderProvider.getInstance().getPlaceHolderDrawable(song.name, true))
                .into(holder.imageView);
    }

    public static class ViewHolder extends BaseViewHolder<QueuePagerItemView> {

        ImageView imageView;

        public ViewHolder(View itemView) {
            super(itemView);

            imageView = itemView.findViewById(R.id.imageView);
        }

        @Override
        public void recycle() {
            super.recycle();

            Glide.clear(imageView);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        QueuePagerItemView that = (QueuePagerItemView) o;

        return song != null ? song.equals(that.song) : that.song == null;
    }

    @Override
    public int hashCode() {
        return song != null ? song.hashCode() : 0;
    }
}
