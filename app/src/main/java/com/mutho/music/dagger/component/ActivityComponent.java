package com.mutho.music.dagger.component;

import com.mutho.music.dagger.module.ActivityModule;
import com.mutho.music.dagger.module.FragmentModule;
import com.mutho.music.dagger.module.PresenterModule;
import com.mutho.music.dagger.scope.ActivityScope;
import com.mutho.music.search.VoiceSearchActivity;
import com.mutho.music.ui.activities.MainActivity;
import com.mutho.music.ui.activities.QCircleActivity;
import com.mutho.music.ui.appwidget.BaseWidgetConfigure;
import com.mutho.music.ui.settings.SettingsParentFragment;
import dagger.Subcomponent;

@ActivityScope
@Subcomponent(modules = {
        ActivityModule.class,
        PresenterModule.class
})

public interface ActivityComponent {

    FragmentComponent plus(FragmentModule module);

    void inject(MainActivity target);

    void inject(BaseWidgetConfigure target);

    void inject(QCircleActivity target);

    void inject(VoiceSearchActivity target);

    void inject(SettingsParentFragment.SettingsFragment target);
}