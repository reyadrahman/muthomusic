package com.mutho.music.dagger.module;

import android.content.Context;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.playback.MediaManager;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class AppModule {

    private MuthoMusicApplication application;

    public AppModule(MuthoMusicApplication application) {
        this.application = application;
    }

    @Provides
    @Singleton
    public Context provideContext() {
        return application;
    }

    @Provides
    @Singleton
    public MediaManager provideMediaManager() {
        return new MediaManager();
    }
}