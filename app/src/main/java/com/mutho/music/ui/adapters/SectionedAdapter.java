package com.mutho.music.ui.adapters;

import android.support.annotation.NonNull;
import com.mutho.music.ui.modelviews.SectionedView;
import com.muthomusicapps.recycler_adapter.adapter.ViewModelAdapter;
import com.muthomusicapps.recycler_adapter.model.ViewModel;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

public class SectionedAdapter extends ViewModelAdapter implements FastScrollRecyclerView.SectionedAdapter {

    @NonNull
    @Override
    public String getSectionName(int position) {

        ViewModel viewModel = items.get(position);

        if (viewModel instanceof SectionedView) {
            return ((SectionedView) viewModel).getSectionName();
        }

        return "";
    }
}