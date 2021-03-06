package com.mutho.music.ui.modelviews;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.mutho.music.model.Header;
import com.muthomusicapps.recycler_adapter.model.BaseViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.BaseViewHolder;

import static com.mutho.music.R.id.line_one;
import static com.mutho.music.R.layout.list_item_section_separator;
import static com.mutho.music.ui.adapters.ViewType.SEARCH_HEADER;

public class SearchHeaderView extends BaseViewModel<SearchHeaderView.ViewHolder> {

    private Header header;

    public SearchHeaderView(Header header) {
        this.header = header;
    }

    @Override
    public int getViewType() {
        return SEARCH_HEADER;
    }

    @Override
    public int getLayoutResId() {
        return list_item_section_separator;
    }

    @Override
    public void bindView(ViewHolder holder) {
        super.bindView(holder);

        holder.lineOne.setText(header.title);
    }

    @Override
    public ViewHolder createViewHolder(ViewGroup parent) {
        return new ViewHolder(createView(parent));
    }

    public static class ViewHolder extends BaseViewHolder {

        TextView lineOne;

        public ViewHolder(View itemView) {
            super(itemView);

            lineOne = itemView.findViewById(line_one);
        }

        @Override
        public String toString() {
            return "SearchHeaderView.ViewHolder";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchHeaderView that = (SearchHeaderView) o;

        return header != null ? header.equals(that.header) : that.header == null;
    }

    @Override
    public int hashCode() {
        return header != null ? header.hashCode() : 0;
    }
}
