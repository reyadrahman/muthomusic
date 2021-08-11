package com.mutho.music.dagger.component;

import com.mutho.music.dagger.module.FragmentModule;
import com.mutho.music.dagger.module.PresenterModule;
import com.mutho.music.dagger.scope.FragmentScope;
import com.mutho.music.search.SearchFragment;
import com.mutho.music.ui.drawer.DrawerFragment;
import com.mutho.music.ui.fragments.AlbumArtistFragment;
import com.mutho.music.ui.fragments.AlbumFragment;
import com.mutho.music.ui.fragments.BaseFragment;
import com.mutho.music.ui.fragments.LibraryController;
import com.mutho.music.ui.fragments.MainController;
import com.mutho.music.ui.fragments.MiniPlayerFragment;
import com.mutho.music.ui.fragments.PlayerFragment;
import com.mutho.music.ui.queue.QueueFragment;
import com.mutho.music.ui.fragments.QueuePagerFragment;
import com.mutho.music.ui.fragments.SuggestedFragment;
import com.mutho.music.ui.presenters.PlayerPresenter;
import com.mutho.music.ui.views.multisheet.CustomMultiSheetView;
import dagger.Subcomponent;

@FragmentScope
@Subcomponent(modules = {
        FragmentModule.class,
        PresenterModule.class
})

public interface FragmentComponent {

    void inject(BaseFragment target);

    void inject(PlayerFragment target);

    void inject(MiniPlayerFragment target);

    void inject(PlayerPresenter target);

    void inject(QueuePagerFragment target);

    void inject(QueueFragment target);

    void inject(AlbumArtistFragment target);

    void inject(AlbumFragment target);

    void inject(SuggestedFragment target);

    void inject(SearchFragment target);

    void inject(LibraryController target);

    void inject(CustomMultiSheetView target);

    void inject(DrawerFragment target);

    void inject(MainController target);
}
