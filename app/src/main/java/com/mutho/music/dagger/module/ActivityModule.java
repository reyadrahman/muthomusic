package com.mutho.music.dagger.module;

import android.app.Activity;
import com.mutho.music.dagger.scope.ActivityScope;
import dagger.Module;
import dagger.Provides;

@Module
public class ActivityModule {

    private Activity activity;

    public ActivityModule(Activity activity) {
        this.activity = activity;
    }

    @Provides
    @ActivityScope
    Activity provideActivity() {
        return activity;
    }
}