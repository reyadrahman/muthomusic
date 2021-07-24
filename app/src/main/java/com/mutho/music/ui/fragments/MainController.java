package com.mutho.music.ui.fragments;

import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.Pair;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.cantrowitz.rxbroadcast.RxBroadcast;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.dagger.module.ActivityModule;
import com.mutho.music.dagger.module.FragmentModule;
import com.mutho.music.model.Album;
import com.mutho.music.model.AlbumArtist;
import com.mutho.music.model.Genre;
import com.mutho.music.model.Playlist;
import com.mutho.music.playback.MediaManager;
import com.mutho.music.playback.constants.InternalIntents;
import com.mutho.music.rx.UnsafeAction;
import com.mutho.music.ui.detail.album.AlbumDetailFragment;
import com.mutho.music.ui.detail.artist.ArtistDetailFragment;
import com.mutho.music.ui.detail.genre.GenreDetailFragment;
import com.mutho.music.ui.detail.playlist.PlaylistDetailFragment;
import com.mutho.music.ui.drawer.DrawerLockController;
import com.mutho.music.ui.drawer.DrawerLockManager;
import com.mutho.music.ui.drawer.DrawerProvider;
import com.mutho.music.ui.drawer.MiniPlayerLockManager;
import com.mutho.music.ui.drawer.NavigationEventRelay;
import com.mutho.music.ui.presenters.PlayerPresenter;
import com.mutho.music.ui.queue.QueueFragment;
import com.mutho.music.ui.settings.SettingsParentFragment;
import com.mutho.music.ui.upnext.UpNextView;
import com.mutho.music.ui.views.multisheet.CustomMultiSheetView;
import com.mutho.music.ui.views.multisheet.MultiSheetEventRelay;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.SleepTimer;
import com.muthomusic.multisheetview.ui.view.MultiSheetView;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import test.com.androidnavigation.fragment.BackPressHandler;
import test.com.androidnavigation.fragment.BaseNavigationController;
import test.com.androidnavigation.fragment.FragmentInfo;

public class MainController extends BaseNavigationController implements BackPressHandler, DrawerLockController {

    private static final String TAG = "MainController";

    public static final String STATE_CURRENT_SHEET = "current_sheet";

    @Inject
    NavigationEventRelay navigationEventRelay;

    @Inject
    MultiSheetEventRelay multiSheetEventRelay;

    @Inject
    MediaManager mediaManager;

    @Inject
    PlayerPresenter playerPresenter;

    private Handler delayHandler;

    @BindView(R.id.multiSheetView)
    CustomMultiSheetView multiSheetView;

    private CompositeDisposable disposables = new CompositeDisposable();

    public static MainController newInstance() {
        Bundle args = new Bundle();
        MainController fragment = new MainController();
        fragment.setArguments(args);
        return fragment;
    }

    public MainController() {

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        ButterKnife.bind(this, rootView);

        MuthoMusicApplication.getInstance().getAppComponent()
                .plus(new ActivityModule(getActivity()))
                .plus(new FragmentModule(this))
                .inject(this);

        if (savedInstanceState == null) {
            getChildFragmentManager()
                    .beginTransaction()
                    .add(multiSheetView.getSheetContainerViewResId(MultiSheetView.Sheet.FIRST), PlayerFragment.newInstance())
                    .add(multiSheetView.getSheetPeekViewResId(MultiSheetView.Sheet.FIRST), MiniPlayerFragment.newInstance())
                    .add(multiSheetView.getSheetContainerViewResId(MultiSheetView.Sheet.SECOND), QueueFragment.Companion.newInstance())
                    .commit();
        } else {
            multiSheetView.restoreSheet(savedInstanceState.getInt(STATE_CURRENT_SHEET));
        }

        ((ViewGroup) multiSheetView.findViewById(multiSheetView.getSheetPeekViewResId(MultiSheetView.Sheet.SECOND))).addView(UpNextView.Companion.newInstance(getContext(), playerPresenter));

        toggleBottomSheetVisibility(false, false);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (delayHandler != null) {
            delayHandler.removeCallbacksAndMessages(null);
        }
        delayHandler = new Handler();

        disposables.add(navigationEventRelay.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .filter(NavigationEventRelay.NavigationEvent::isActionable)
                .subscribe(navigationEvent -> {
                    switch (navigationEvent.type) {
                        case NavigationEventRelay.NavigationEvent.Type.LIBRARY_SELECTED:
                            popToRootViewController();
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.FOLDERS_SELECTED:
                            delayHandler.postDelayed(() -> pushViewController(FolderFragment.newInstance(getString(R.string.folders_title), false), "FolderFragment"), 250);
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.SLEEP_TIMER_SELECTED:
                            UnsafeAction showToast = () -> Toast.makeText(getContext(), R.string.sleep_timer_started, Toast.LENGTH_SHORT).show();
                            SleepTimer.getInstance().getDialog(
                                    getContext(),
                                    () -> SleepTimer.getInstance().showMinutesDialog(getContext(), showToast),
                                    showToast
                            ).show();
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.EQUALIZER_SELECTED:
                            delayHandler.postDelayed(
                                    () -> multiSheetEventRelay.sendEvent(new MultiSheetEventRelay.MultiSheetEvent(MultiSheetEventRelay.MultiSheetEvent.Action.HIDE, MultiSheetView.Sheet.FIRST)), 100);
                            delayHandler.postDelayed(() -> pushViewController(EqualizerFragment.newInstance(), "EqualizerFragment"), 250);
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.SETTINGS_SELECTED:
                            delayHandler.postDelayed(
                                    () -> multiSheetEventRelay.sendEvent(new MultiSheetEventRelay.MultiSheetEvent(MultiSheetEventRelay.MultiSheetEvent.Action.HIDE, MultiSheetView.Sheet.FIRST)), 100);
                            delayHandler.postDelayed(() -> pushViewController(SettingsParentFragment.newInstance(R.xml.settings_headers, R.string.settings), "Settings Fragment"), 250);
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.SUPPORT_SELECTED:
                            delayHandler.postDelayed(
                                    () -> multiSheetEventRelay.sendEvent(new MultiSheetEventRelay.MultiSheetEvent(MultiSheetEventRelay.MultiSheetEvent.Action.HIDE, MultiSheetView.Sheet.FIRST)), 100);
                            delayHandler.postDelayed(() -> pushViewController(SettingsParentFragment.newInstance(R.xml.settings_support, R.string.pref_title_support), "Support Fragment"), 250);
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.PLAYLIST_SELECTED:
                            delayHandler.postDelayed(() -> pushViewController(PlaylistDetailFragment.newInstance((Playlist) navigationEvent.data), "PlaylistDetailFragment"), 250);
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.GO_TO_ARTIST:
                            multiSheetView.goToSheet(MultiSheetView.Sheet.NONE);
                            AlbumArtist albumArtist = (AlbumArtist) navigationEvent.data;
                            delayHandler.postDelayed(() -> {
                                popToRootViewController();
                                pushViewController(ArtistDetailFragment.newInstance(albumArtist, null), "ArtistDetailFragment");
                            }, 250);
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.GO_TO_ALBUM:
                            multiSheetView.goToSheet(MultiSheetView.Sheet.NONE);
                            Album album = (Album) navigationEvent.data;
                            delayHandler.postDelayed(() -> {
                                popToRootViewController();
                                pushViewController(AlbumDetailFragment.newInstance(album, null), "AlbumDetailFragment");
                            }, 250);
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.GO_TO_GENRE:
                            multiSheetView.goToSheet(MultiSheetView.Sheet.NONE);
                            Genre genre = (Genre) navigationEvent.data;
                            delayHandler.postDelayed(() -> {
                                popToRootViewController();
                                pushViewController(GenreDetailFragment.newInstance(genre), "GenreDetailFragment");
                            }, 250);
                            break;
                    }
                }));

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(InternalIntents.SERVICE_CONNECTED);
        intentFilter.addAction(InternalIntents.QUEUE_CHANGED);
        disposables.add(
                RxBroadcast.fromBroadcast(getContext(), intentFilter)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(intent -> {
                            toggleBottomSheetVisibility(true, true);
                        })
        );

        DrawerLockManager.getInstance().setDrawerLockController(this);
    }

    @Override
    public void onPause() {
        delayHandler.removeCallbacksAndMessages(null);
        delayHandler = null;

        disposables.clear();

        DrawerLockManager.getInstance().setDrawerLockController(null);

        super.onPause();
    }

    /**
     * Hide/show the bottom sheet, depending on whether the queue is empty.
     */
    private void toggleBottomSheetVisibility(boolean collapse, boolean animate) {
        if (!mediaManager.getQueueReloading() && mediaManager.getQueue().isEmpty()) {
            multiSheetView.hide(collapse, false);
        } else if (MiniPlayerLockManager.getInstance().canShowMiniPlayer()) {
            multiSheetView.unhide(animate);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CURRENT_SHEET, multiSheetView.getCurrentSheet());
        super.onSaveInstanceState(outState);
    }

    @Override
    public FragmentInfo getRootViewControllerInfo() {
        return LibraryController.fragmentInfo();
    }

    @Override
    public boolean consumeBackPress() {
        if (multiSheetView.consumeBackPress()) {
            return true;
        }

        return super.consumeBackPress();
    }

    @Override
    public void lockDrawer() {
        ((DrawerProvider) getActivity()).getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    }

    @Override
    public void unlockDrawer() {
        // Don't unlock the drawer if one of the sheets is expanded
        if (multiSheetView.getCurrentSheet() == MultiSheetView.Sheet.FIRST || multiSheetView.getCurrentSheet() == MultiSheetView.Sheet.SECOND) {
            return;
        }

        ((DrawerProvider) getActivity()).getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    }

    // Todo:  Remove once cause of shared element crash is understood.
    // This is a copy of the superclass method of the same name/signature, with some additional logging
    // to help ascertain the cause of a crash.
    @Override
    public void pushViewController(@NonNull Fragment fragment, @Nullable String tag, @Nullable List<Pair<View, String>> sharedElements) {
        FragmentTransaction fragmentTransaction = getChildFragmentManager()
                .beginTransaction();

        if (sharedElements != null) {
            for (Pair<View, String> pair : sharedElements) {
                try {
                    fragmentTransaction.addSharedElement(pair.first, pair.second);
                } catch (IllegalArgumentException e) {
                    LogUtils.logException(TAG, String.format("Error adding shared element transition.. key: %s, value: %s", pair.first, pair.second), e);
                }
            }
        }

        fragmentTransaction.addToBackStack(null)
                .replace(test.com.androidnavigation.R.id.mainContainer, fragment, tag)
                .commit();
    }
}