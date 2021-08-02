package com.mutho.music.ui.views;

import android.content.res.Resources;
import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import com.mutho.music.ui.adapters.ViewType;
import com.mutho.music.utils.ResourceUtils;

public class SuggestedDividerDecoration extends RecyclerView.ItemDecoration {

    private int spacing;

    public SuggestedDividerDecoration(Resources res) {
        this.spacing = ResourceUtils.toPixels(4);
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {

        int spanIndex = ((GridLayoutManager) parent.getLayoutManager()).getSpanSizeLookup().getSpanIndex(
                parent.getChildAdapterPosition(view), ((GridLayoutManager) parent.getLayoutManager()).getSpanCount()
        );

        switch (parent.getChildViewHolder(view).getItemViewType()) {
            case ViewType.ALBUM_LIST_SMALL:
                outRect.left = spacing;
                outRect.right = spacing;
                break;
            case ViewType.ALBUM_CARD_LARGE:
                if (spanIndex == 0) {
                    outRect.left = spacing;
                } else if (spanIndex == 3) {
                    outRect.right = spacing;
                }
                break;
        }
    }
}