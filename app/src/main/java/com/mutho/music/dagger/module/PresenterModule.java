package com.mutho.music.dagger.module;

import com.mutho.music.search.SearchPresenter;
import com.mutho.music.search.SearchView;
import com.mutho.music.ui.presenters.PlayerPresenter;
import com.mutho.music.ui.presenters.Presenter;
import com.mutho.music.ui.presenters.QueuePagerPresenter;
import com.mutho.music.ui.queue.QueueContract;
import com.mutho.music.ui.queue.QueuePresenter;
import com.mutho.music.ui.views.PlayerView;
import com.mutho.music.ui.views.QueuePagerView;
import dagger.Binds;
import dagger.Module;

@Module
public abstract class PresenterModule {

    @Binds
    abstract Presenter<PlayerView> bindPlayerPresenter(PlayerPresenter playerPresenter);

    @Binds
    abstract Presenter<QueuePagerView> bindQueuePagerPresenter(QueuePagerPresenter queuePagerPresenter);

    @Binds
    abstract Presenter<QueueContract.View> bindQueuePresenter(QueuePresenter queuePresenter);

    @Binds
    abstract Presenter<SearchView> bindSearchPresenter(SearchPresenter queuePresenter);
}

