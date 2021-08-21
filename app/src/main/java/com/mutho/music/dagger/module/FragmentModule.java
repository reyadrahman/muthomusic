package com.mutho.music.dagger.module;

import android.support.v4.app.Fragment;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.mutho.music.dagger.scope.FragmentScope;
import com.mutho.music.format.PrefixHighlighter;
import dagger.Module;
import dagger.Provides;

@Module
public class FragmentModule {

    private Fragment fragment;

    public FragmentModule(Fragment fragment) {
        this.fragment = fragment;
    }

    @Provides
    @FragmentScope
    Fragment provideFragment() {
        return fragment;
    }

    @Provides
    @FragmentScope
    RequestManager provideRequestManager() {
        return Glide.with(fragment);
    }

    @Provides
    @FragmentScope
    PrefixHighlighter providePrefixHighlighter() {
        return new PrefixHighlighter();
    }
}
