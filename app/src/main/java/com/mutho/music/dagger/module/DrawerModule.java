package com.mutho.music.dagger.module;

import com.mutho.music.ui.drawer.NavigationEventRelay;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class DrawerModule {

    @Provides
    @Singleton
    NavigationEventRelay provideDrawerEventRelay() {
        return new NavigationEventRelay();
    }
}