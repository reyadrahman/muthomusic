package com.mutho.music.ui.appwidget;

import com.mutho.music.R;
import com.mutho.music.ui.widgets.WidgetProviderLarge;

public class WidgetConfigureLarge extends BaseWidgetConfigure {

    private static final String TAG = "WidgetConfigureLarge";

    @Override
    int[] getWidgetLayouts() {
        return new int[] { R.layout.widget_layout_large, R.layout.widget_layout_large_alt };
    }

    @Override
    String getLayoutIdString() {
        return WidgetProviderLarge.ARG_LARGE_LAYOUT_ID;
    }

    @Override
    String getUpdateCommandString() {
        return WidgetProviderLarge.CMDAPPWIDGETUPDATE;
    }

    @Override
    int getRootViewId() {
        return R.id.widget_layout_large;
    }

    @Override
    protected String screenName() {
        return TAG;
    }
}
