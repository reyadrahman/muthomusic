package com.mutho.music.dagger.component;

import com.mutho.music.dagger.module.*;
import com.mutho.music.ui.dialog.DeleteDialog;
import com.mutho.music.ui.views.multisheet.CustomMultiSheetView;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = {
        AppModule.class,
        ModelsModule.class,
        DrawerModule.class
})

public interface AppComponent {

    ActivityComponent plus(ActivityModule module);

    void inject(CustomMultiSheetView target);

    void inject(DeleteDialog target);

}
