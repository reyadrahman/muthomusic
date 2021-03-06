package com.mutho.music.ui.fragments;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import com.google.android.libraries.cast.companionlibrary.cast.BaseCastManager;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.dagger.module.ActivityModule;
import com.mutho.music.dagger.module.FragmentModule;
import com.mutho.music.playback.MediaManager;
import com.mutho.music.ui.activities.BaseCastActivity;
import com.mutho.music.ui.views.CustomMediaRouteActionProvider;
import com.mutho.music.ui.views.multisheet.MultiSheetEventRelay;
import com.mutho.music.utils.AnalyticsManager;
import com.squareup.leakcanary.RefWatcher;
import java.lang.reflect.Field;
import javax.inject.Inject;
import test.com.androidnavigation.fragment.BaseController;

public abstract class BaseFragment extends BaseController {

    private static final String TAG = "BaseFragment";

    // Arbitrary value; set it to some reasonable default
    private static final int DEFAULT_CHILD_ANIMATION_DURATION = 250;

    @Inject
    MultiSheetEventRelay multiSheetEventRelay;

    @Inject
    protected MediaManager mediaManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MuthoMusicApplication.getInstance().getAppComponent()
                .plus(new ActivityModule(getActivity()))
                .plus(new FragmentModule(this))
                .inject(this);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();

        AnalyticsManager.logScreenName(getActivity(), screenName());
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {

        /*
         * When a fragment transaction is performed on a parent fragment containing nested children,
         * the children disappear as soon as the transaction begins.. So if you're relying on some
         * fancy animation for the parent, we need to ensure the child waits before performing its
         * own animation.
         * see https://code.google.com/p/android/issues/detail?id=55228
         */
        final Fragment parent = getParentFragment();

        // Apply the workaround only if this is a child fragment, and the parent
        // is being removed.
        if (!enter && parent != null && parent.isRemoving()) {
            // This is a workaround for the bug where child fragments disappear when
            // the parent is removed (as all children are first removed from the parent)
            // See https://code.google.com/p/android/issues/detail?id=55228
            Animation doNothingAnim = new AlphaAnimation(1, 1);
            doNothingAnim.setDuration(getNextAnimationDuration(parent, DEFAULT_CHILD_ANIMATION_DURATION));
            return doNothingAnim;
        } else {
            return super.onCreateAnimation(transit, enter, nextAnim);
        }
    }

    /**
     * Attempt to get the resource ID of the next animation that
     * will be applied to the given fragment.
     *
     * @param fragment the parent fragment
     * @param defValue default animation value
     * @return the duration of the parent fragment's animation
     */
    private static long getNextAnimationDuration(Fragment fragment, long defValue) {
        try {
            // Attempt to get the resource ID of the next animation that
            // will be applied to the given fragment.
            Field nextAnimField = Fragment.class.getDeclaredField("mNextAnim");
            nextAnimField.setAccessible(true);
            int nextAnimResource = nextAnimField.getInt(fragment);
            Animation nextAnim = AnimationUtils.loadAnimation(fragment.getActivity(), nextAnimResource);

            // ...and if it can be loaded, return that animation's duration
            return (nextAnim == null) ? defValue : nextAnim.getDuration();
        } catch (NoSuchFieldException | IllegalAccessException | Resources.NotFoundException ignored) {
            return defValue;
        }
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
        RefWatcher refWatcher = MuthoMusicApplication.getInstance().getRefWatcher();
        refWatcher.watch(this);
    }

    protected void setupCastMenu(Menu menu) {
        if (getActivity() instanceof BaseCastActivity) {
            BaseCastManager castManager = ((BaseCastActivity) getActivity()).castManager;
            castManager.addMediaRouterButton(menu, R.id.media_route_menu_item);

            MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
            ((CustomMediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem)).setActivity(getActivity());
        }
    }

    public MediaManager getMediaManager() {
        return mediaManager;
    }

    protected abstract String screenName();
}
