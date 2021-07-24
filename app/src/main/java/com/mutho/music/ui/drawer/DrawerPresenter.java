package com.mutho.music.ui.drawer;

import android.app.Activity;
import android.support.annotation.NonNull;
import com.mutho.music.model.Playlist;
import com.mutho.music.model.PlaylistsModel;
import com.mutho.music.ui.presenters.Presenter;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.PermissionUtils;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

public class DrawerPresenter extends Presenter<DrawerView> {

    private static final String TAG = "DrawerPresenter";

    @Inject
    NavigationEventRelay navigationEventRelay;

    @Inject
    PlaylistsModel playlistsModel;

    @Inject
    public DrawerPresenter() {
        super();
    }

    @Override
    public void bindView(@NonNull DrawerView view) {
        super.bindView(view);

        loadData(view);

        addDisposable(navigationEventRelay.getEvents()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(drawerEvent -> {
                    DrawerView drawerView = getView();
                    switch (drawerEvent.type) {
                        case NavigationEventRelay.NavigationEvent.Type.LIBRARY_SELECTED:
                            if (drawerView != null) {
                                drawerView.setDrawerItemSelected(DrawerParent.Type.LIBRARY);
                            }
                            break;
                        case NavigationEventRelay.NavigationEvent.Type.FOLDERS_SELECTED:
                            if (drawerView != null) {
                                drawerView.setDrawerItemSelected(DrawerParent.Type.FOLDERS);
                            }
                            break;
                    }
                }));
    }

    private void loadData(@NonNull DrawerView drawerView) {
        PermissionUtils.RequestStoragePermissions(() ->
                addDisposable(playlistsModel.getPlaylistsObservable()
                        .observeOn(AndroidSchedulers.mainThread())
                        // Delay the subscription so we're not querying data while the app is launching
                        .delaySubscription(1500, TimeUnit.MILLISECONDS)
                        // Clear all playlist item to avoid memory leak in static var DrawerParent.playlistsParent
                        .doFinally(() -> drawerView.setPlaylistItems(Collections.emptyList()))
                        .subscribe(
                                drawerView::setPlaylistItems,
                                error -> LogUtils.logException(TAG, "Error refreshing DrawerFragment adapter items", error)
                        )));
    }

    void onDrawerItemClicked(DrawerParent drawerParent) {
        DrawerView drawerView = getView();
        if (drawerView != null && drawerParent.isSelectable()) {
            drawerView.setDrawerItemSelected(drawerParent.type);
        }

        closeDrawer();

        if (drawerParent.navigationEvent != null) {
            navigationEventRelay.sendEvent(drawerParent.navigationEvent);
        }
    }

    private void closeDrawer() {
        DrawerView drawerView = getView();
        if (drawerView != null) {
            drawerView.closeDrawer();
        }
    }

    public void onPlaylistClicked(Playlist playlist) {
        closeDrawer();
        navigationEventRelay.sendEvent(new NavigationEventRelay.NavigationEvent(NavigationEventRelay.NavigationEvent.Type.PLAYLIST_SELECTED, playlist));
    }
}