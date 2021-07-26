package com.mutho.music.ui.modelviews;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.muthomusicapps.recycler_adapter.model.BaseViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.BaseViewHolder;

import static com.mutho.music.R.id;
import static com.mutho.music.R.layout.list_item_disc_number;
import static com.mutho.music.R.string.disc_number_label;
import static com.mutho.music.ui.adapters.ViewType.DISC_NUMBER;

public class DiscNumberView extends BaseViewModel<DiscNumberView.ViewHolder> {

    private int discNumber = 0;

    public DiscNumberView(int discNumber) {
        this.discNumber = discNumber;
    }

    @Override
    public int getViewType() {
        return DISC_NUMBER;
    }

    @Override
    public int getLayoutResId() {
        return list_item_disc_number;
    }

    @Override
    public void bindView(ViewHolder holder) {
        super.bindView(holder);
        holder.textView.setText(holder.itemView.getContext().getString(disc_number_label, discNumber));
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        return new ViewHolder(createView(parent));
    }

    public static class ViewHolder extends BaseViewHolder {

        TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);

            textView = itemView.findViewById(id.textView);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DiscNumberView that = (DiscNumberView) o;

        return discNumber == that.discNumber;
    }

    @Override
    public int hashCode() {
        return discNumber;
    }

    @Override
    public boolean areContentsEqual(Object other) {
        return equals(other);
    }
}
