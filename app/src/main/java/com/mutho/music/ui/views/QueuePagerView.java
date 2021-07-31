package com.mutho.music.ui.views;

import com.muthomusicapps.recycler_adapter.model.ViewModel;
import java.util.List;

public interface QueuePagerView {

    void loadData(List<ViewModel> items, int position);

    void updateQueuePosition(int position);
}