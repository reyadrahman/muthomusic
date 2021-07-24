package com.mutho.music.ui.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.annimon.stream.Stream;
import com.bumptech.glide.RequestManager;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.dagger.module.ActivityModule;
import com.mutho.music.dagger.module.FragmentModule;
import com.mutho.music.model.Album;
import com.mutho.music.model.Song;
import com.mutho.music.playback.QueueManager;
import com.mutho.music.ui.adapters.SectionedAdapter;
import com.mutho.music.ui.adapters.ViewType;
import com.mutho.music.ui.modelviews.AlbumView;
import com.mutho.music.ui.modelviews.EmptyView;
import com.mutho.music.ui.modelviews.SelectableViewModel;
import com.mutho.music.ui.modelviews.ShuffleView;
import com.mutho.music.ui.recyclerview.GridDividerDecoration;
import com.mutho.music.ui.views.ContextualToolbar;
import com.mutho.music.utils.AnalyticsManager;
import com.mutho.music.utils.ContextualToolbarHelper;
import com.mutho.music.utils.DataManager;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.Operators;
import com.mutho.music.utils.PermissionUtils;
import com.mutho.music.utils.PlaylistUtils;
import com.mutho.music.utils.SettingsManager;
import com.mutho.music.utils.menu.album.AlbumMenuCallbacksAdapter;
import com.mutho.music.utils.menu.album.AlbumMenuUtils;
import com.mutho.music.utils.sorting.SortManager;
import com.muthomusicapps.recycler_adapter.model.ViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.RecyclerListener;
import com.muthomusicapps.recycler_adapter.recyclerview.SpanSizeLookup;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import kotlin.Unit;

public class AlbumFragment extends BaseFragment implements
        AlbumView.ClickListener,
        ShuffleView.ShuffleClickListener {

    interface AlbumClickListener {

        void onAlbumClicked(Album album, View transitionView);
    }

    private final static String TAG = "AlbumFragment";

    private static final String ARG_PAGE_TITLE = "page_title";

    private static final int MENU_GRID_SIZE = 100;
    private static final int MENU_GROUP_GRID = 1;

    @Nullable
    private AlbumClickListener albumClickListener;

    private FastScrollRecyclerView recyclerView;

    private GridLayoutManager layoutManager;

    SectionedAdapter adapter;

    private SpanSizeLookup spanSizeLookup;

    private boolean sortOrderChanged = false;

    private ShuffleView shuffleView;

    private Disposable subscription;

    @Inject
    RequestManager requestManager;

    private ContextualToolbarHelper<Album> contextualToolbarHelper;

    @Nullable
    private Disposable playlistMenuDisposable;

    private CompositeDisposable menuDisposables = new CompositeDisposable();

    private AlbumMenuCallbacksAdapter albumMenuCallbacksAdapter = new AlbumMenuCallbacksAdapter(this, menuDisposables);

    public AlbumFragment() {

    }

    public static AlbumFragment newInstance(String pageTitle) {
        AlbumFragment albumFragment = new AlbumFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PAGE_TITLE, pageTitle);
        albumFragment.setArguments(args);
        return albumFragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Fragment parentFragment = getParentFragment();
        if (parentFragment instanceof AlbumClickListener) {
            albumClickListener = (AlbumClickListener) parentFragment;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MuthoMusicApplication.getInstance().getAppComponent()
                .plus(new ActivityModule(getActivity()))
                .plus(new FragmentModule(this))
                .inject(this);

        setHasOptionsMenu(true);

        adapter = new SectionedAdapter();
    }

    @SuppressLint("NewApi")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (recyclerView == null) {
            int spanCount = SettingsManager.getInstance().getAlbumColumnCount(getResources());
            layoutManager = new GridLayoutManager(getContext(), spanCount);
            spanSizeLookup = new SpanSizeLookup(adapter, spanCount);
            spanSizeLookup.setSpanIndexCacheEnabled(true);
            layoutManager.setSpanSizeLookup(spanSizeLookup);

            recyclerView = (FastScrollRecyclerView) inflater.inflate(R.layout.fragment_recycler, container, false);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.addItemDecoration(new GridDividerDecoration(getResources(), 4, true));
            recyclerView.setRecyclerListener(new RecyclerListener());
        }
        if (recyclerView.getAdapter() != adapter) {
            recyclerView.setAdapter(adapter);
        }

        shuffleView = new ShuffleView();
        shuffleView.setTitleResId(R.string.shuffle_albums);
        shuffleView.setClickListener(this);

        return recyclerView;
    }

    @Override
    public void onPause() {

        if (subscription != null) {
            subscription.dispose();
        }

        if (playlistMenuDisposable != null) {
            playlistMenuDisposable.dispose();
        }

        menuDisposables.clear();

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshAdapterItems(false);

        if (getUserVisibleHint()) {
            setupContextualToolbar();
        }
    }

    void refreshAdapterItems(boolean force) {
        PermissionUtils.RequestStoragePermissions(() -> {
            if (getActivity() != null && isAdded()) {

                int albumDisplayType = SettingsManager.getInstance().getAlbumDisplayType();

                boolean ascending = SortManager.getInstance().getAlbumsAscending();

                subscription = DataManager.getInstance().getAlbumsRelay()
                        .skipWhile(albums -> !force && Stream.of(adapter.items).filter(viewModel -> viewModel instanceof AlbumView).count() == albums.size())
                        .debounce(150, TimeUnit.MILLISECONDS)
                        .flatMapSingle(albums -> {
                            //Sort
                            SortManager.getInstance().sortAlbums(albums);
                            //Reverse if required
                            if (!ascending) {
                                Collections.reverse(albums);
                            }
                            return Observable.fromIterable(albums)
                                    .map(album -> {

                                        // Look for an existing AlbumView wrapping the album, we'll reuse it if it exists.
                                        AlbumView albumView = (AlbumView) Stream.of(adapter.items)
                                                .filter(viewModel -> viewModel instanceof AlbumView && (((AlbumView) viewModel).album.equals(album)))
                                                .findFirst()
                                                .orElse(null);

                                        if (albumView == null) {
                                            albumView = new AlbumView(album, albumDisplayType, requestManager);
                                            albumView.setClickListener(this);
                                        }

                                        return (ViewModel) albumView;
                                    })
                                    .toList();
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                items -> {

                                    if (items.isEmpty()) {
                                        AnalyticsManager.dropBreadcrumb(TAG, "setItems() (empty)");
                                        adapter.setItems(Collections.singletonList(new EmptyView(R.string.empty_albums)));
                                    } else {
                                        items.add(0, shuffleView);
                                        AnalyticsManager.dropBreadcrumb(TAG, "setItems()");
                                        adapter.setItems(items);
                                    }

                                    //Move the RV back to the top if we've had a sort order change.
                                    if (sortOrderChanged) {
                                        recyclerView.scrollToPosition(0);
                                    }

                                    sortOrderChanged = false;
                                },
                                error -> LogUtils.logException(TAG, "Error refreshing adapter items", error)
                        );
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.menu_sort_albums, menu);
        inflater.inflate(R.menu.menu_view_as, menu);

        menu.addSubMenu(0, MENU_GRID_SIZE, 0, R.string.menu_grid_size);
        SubMenu subMenu = menu.findItem(MENU_GRID_SIZE).getSubMenu();

        int[] spanCountArray = getResources().getIntArray(R.array.span_count);
        for (int i = 0; i < spanCountArray.length; i++) {
            subMenu.add(MENU_GROUP_GRID, spanCountArray[i], i, String.valueOf(spanCountArray[i]));
        }
        subMenu.setGroupCheckable(MENU_GROUP_GRID, true, true);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        int sortOrder = SortManager.getInstance().getAlbumsSortOrder();

        switch (sortOrder) {
            case SortManager.AlbumSort.DEFAULT:
                menu.findItem(R.id.sort_album_default).setChecked(true);
                break;
            case SortManager.AlbumSort.NAME:
                menu.findItem(R.id.sort_album_name).setChecked(true);
                break;
            case SortManager.AlbumSort.YEAR:
                menu.findItem(R.id.sort_album_year).setChecked(true);
                break;
            case SortManager.AlbumSort.ARTIST_NAME:
                menu.findItem(R.id.sort_album_artist_name).setChecked(true);
                break;
        }

        menu.findItem(R.id.sort_album_ascending).setChecked(SortManager.getInstance().getAlbumsAscending());

        int displayType = SettingsManager.getInstance().getAlbumDisplayType();
        switch (displayType) {
            case ViewType.ALBUM_LIST:
                menu.findItem(R.id.view_as_list).setChecked(true);
                break;
            case ViewType.ALBUM_GRID:
                menu.findItem(R.id.view_as_grid).setChecked(true);
                break;
            case ViewType.ALBUM_CARD:
                menu.findItem(R.id.view_as_grid_card).setChecked(true);
                break;
            case ViewType.ALBUM_PALETTE:
                menu.findItem(R.id.view_as_grid_palette).setChecked(true);
                break;
        }

        MenuItem gridMenuItem = menu.findItem(MENU_GRID_SIZE);
        if (displayType == ViewType.ALBUM_LIST) {
            gridMenuItem.setVisible(false);
        } else {
            gridMenuItem.setVisible(true);
            SubMenu subMenu = gridMenuItem.getSubMenu();
            if (subMenu != null) {
                subMenu.findItem(SettingsManager.getInstance().getAlbumColumnCount(getResources()))
                        .setChecked(true);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.sort_album_default:
                SortManager.getInstance().setAlbumsSortOrder(SortManager.AlbumSort.DEFAULT);
                sortOrderChanged = true;
                refreshAdapterItems(true);
                break;
            case R.id.sort_album_name:
                SortManager.getInstance().setAlbumsSortOrder(SortManager.AlbumSort.NAME);
                sortOrderChanged = true;
                refreshAdapterItems(true);
                break;
            case R.id.sort_album_year:
                SortManager.getInstance().setAlbumsSortOrder(SortManager.AlbumSort.YEAR);
                sortOrderChanged = true;
                refreshAdapterItems(true);
                break;
            case R.id.sort_album_artist_name:
                SortManager.getInstance().setAlbumsSortOrder(SortManager.AlbumSort.ARTIST_NAME);
                sortOrderChanged = true;
                refreshAdapterItems(true);
                break;
            case R.id.sort_album_ascending:
                SortManager.getInstance().setAlbumsAscending(!item.isChecked());
                sortOrderChanged = true;
                refreshAdapterItems(true);
                break;
            case R.id.view_as_list:
                int viewType = ViewType.ALBUM_LIST;
                SettingsManager.getInstance().setAlbumDisplayType(viewType);
                setupListSpan();
                updateViewType(viewType);
                break;
            case R.id.view_as_grid:
                viewType = ViewType.ALBUM_GRID;
                SettingsManager.getInstance().setAlbumDisplayType(viewType);
                setupGridSpan();
                updateViewType(viewType);
                break;
            case R.id.view_as_grid_card:
                viewType = ViewType.ALBUM_CARD;
                SettingsManager.getInstance().setAlbumDisplayType(viewType);
                setupGridSpan();
                updateViewType(viewType);
                break;
            case R.id.view_as_grid_palette:
                viewType = ViewType.ALBUM_PALETTE;
                SettingsManager.getInstance().setAlbumDisplayType(viewType);
                setupGridSpan();
                updateViewType(viewType);
                break;
        }

        if (item.getGroupId() == MENU_GROUP_GRID) {
            SettingsManager.getInstance().setAlbumColumnCount(item.getItemId());
            spanSizeLookup.setSpanCount(item.getItemId());
            ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanCount(SettingsManager.getInstance().getAlbumColumnCount(getResources()));
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        getActivity().invalidateOptionsMenu();

        return super.onOptionsItemSelected(item);
    }

    private void setupGridSpan() {
        int spanCount = SettingsManager.getInstance().getAlbumColumnCount(getResources());
        spanSizeLookup.setSpanCount(spanCount);
        layoutManager.setSpanCount(spanCount);
    }

    private void setupListSpan() {
        int spanCount = getResources().getInteger(R.integer.list_num_columns);
        spanSizeLookup.setSpanCount(spanCount);
        layoutManager.setSpanCount(spanCount);
    }

    void updateViewType(@ViewType int viewType) {
        Stream.of(adapter.items)
                .filter(viewModel -> viewModel instanceof AlbumView)
                .forEach(viewModel -> ((AlbumView) viewModel).setViewType(viewType));
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
    }

    @Override
    public void onAlbumClick(int position, AlbumView albumView, AlbumView.ViewHolder viewHolder) {
        if (!contextualToolbarHelper.handleClick(albumView, albumView.album)) {
            if (albumClickListener != null) {
                albumClickListener.onAlbumClicked(albumView.album, viewHolder.imageOne);
            }
        }
    }

    @Override
    public boolean onAlbumLongClick(int position, AlbumView albumView) {
        return contextualToolbarHelper.handleLongClick(albumView, albumView.album);
    }

    @Override
    public void onAlbumOverflowClicked(View view, Album album) {
        PopupMenu menu = new PopupMenu(getContext(), view);
        menu.inflate(R.menu.menu_album);
        SubMenu sub = menu.getMenu().findItem(R.id.addToPlaylist).getSubMenu();
        PlaylistUtils.createPlaylistMenu(sub);
        menu.setOnMenuItemClickListener(AlbumMenuUtils.INSTANCE.getAlbumMenuClickListener(getContext(), mediaManager, album, albumMenuCallbacksAdapter));
        menu.show();
    }

    @Override
    public void onShuffleItemClick() {
        // Note: For album-shuffle mode, we don't actually turn shuffle on.
        mediaManager.setShuffleMode(QueueManager.ShuffleMode.OFF);

        Single<List<Song>> aa = DataManager.getInstance()
                .getSongsRelay()
                .firstOrError()
                .map(Operators::albumShuffleSongs);

        mediaManager.playAll(DataManager.getInstance()
                .getSongsRelay()
                .firstOrError()
                .map(Operators::albumShuffleSongs), message -> {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            return Unit.INSTANCE;
        });
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            setupContextualToolbar();
        } else {
            if (contextualToolbarHelper != null) {
                contextualToolbarHelper.finish();
            }
        }
    }

    private void setupContextualToolbar() {
        ContextualToolbar contextualToolbar = ContextualToolbar.findContextualToolbar(this);
        if (contextualToolbar != null) {
            contextualToolbar.getMenu().clear();
            contextualToolbar.inflateMenu(R.menu.context_menu_general);
            SubMenu sub = contextualToolbar.getMenu().findItem(R.id.addToPlaylist).getSubMenu();
            if (playlistMenuDisposable != null) {
                playlistMenuDisposable.dispose();
            }
            playlistMenuDisposable = PlaylistUtils.createUpdatingPlaylistMenu(sub)
                    .doOnError(throwable -> LogUtils.logException(TAG, "setupContextualToolbar error", throwable))
                    .onErrorComplete()
                    .subscribe();

            contextualToolbar.setOnMenuItemClickListener(
                    AlbumMenuUtils.INSTANCE.getAlbumMenuClickListener(
                            getContext(),
                            mediaManager,
                            Single.defer(() -> Single.just(contextualToolbarHelper.getItems())),
                            albumMenuCallbacksAdapter)
            );

            contextualToolbarHelper = new ContextualToolbarHelper<>(contextualToolbar, new ContextualToolbarHelper.Callback() {

                @Override
                public void notifyItemChanged(SelectableViewModel viewModel) {
                    int index = adapter.items.indexOf(viewModel);
                    if (index >= 0) {
                        adapter.notifyItemChanged(index, 0);
                    }
                }

                @Override
                public void notifyDatasetChanged() {
                    adapter.notifyItemRangeChanged(0, adapter.items.size(), 0);
                }
            });
        }
    }

    @Override
    protected String screenName() {
        return TAG;
    }
}
