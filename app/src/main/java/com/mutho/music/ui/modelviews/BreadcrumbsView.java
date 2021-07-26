package com.mutho.music.ui.modelviews;

import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import com.mutho.music.R;
import com.mutho.music.interfaces.BreadcrumbListener;
import com.mutho.music.ui.adapters.ViewType;
import com.mutho.music.ui.views.BreadcrumbItem;
import com.mutho.music.ui.views.BreadcrumbView;
import com.muthomusicapps.recycler_adapter.model.BaseViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.BaseViewHolder;
import java.util.List;

public class BreadcrumbsView extends BaseViewModel<BreadcrumbsView.ViewHolder> {

    private String breadcrumbPath;

    public BreadcrumbsView(String breadcrumbPath) {
        this.breadcrumbPath = breadcrumbPath;
    }

    public void setBreadcrumbsPath(String path) {
        breadcrumbPath = path;
    }

    @Nullable
    private BreadcrumbListener listener;

    public void setListener(@Nullable BreadcrumbListener listener) {
        this.listener = listener;
    }

    @Override
    public int getViewType() {
        return ViewType.BREADCRUMBS;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.list_item_breadcrumbs;
    }

    @Override
    public void bindView(ViewHolder holder) {
        super.bindView(holder);

        if (!TextUtils.isEmpty(breadcrumbPath)) {
            holder.breadcrumbView.changeBreadcrumbPath(breadcrumbPath);
        }
    }

    @Override
    public void bindView(ViewHolder holder, int position, List payloads) {
        bindView(holder);
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        return new ViewHolder(createView(parent));
    }

    public void onBreadcrumbClick(BreadcrumbItem breadcrumbItem) {
        if (listener != null) {
            listener.onBreadcrumbItemClick(breadcrumbItem);
        }
    }

    public static class ViewHolder extends BaseViewHolder<BreadcrumbsView> {

        public BreadcrumbView breadcrumbView;

        public ViewHolder(View itemView) {
            super(itemView);

            breadcrumbView = itemView.findViewById(R.id.breadcrumbs);
            breadcrumbView.addBreadcrumbListener(item -> viewModel.onBreadcrumbClick(item));
        }

        @Override
        public String toString() {
            return "BreadcrumbsView.ViewHolder";
        }
    }
}
