package com.mutho.music.ui.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.bumptech.glide.RequestManager;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.dagger.module.ActivityModule;
import com.mutho.music.dagger.module.FragmentModule;
import com.mutho.music.model.Album;
import com.mutho.music.model.AlbumArtist;
import com.mutho.music.model.Playlist;
import com.mutho.music.model.Song;
import com.mutho.music.model.SuggestedHeader;
import com.mutho.music.ui.adapters.ViewType;
import com.mutho.music.ui.detail.playlist.PlaylistDetailFragment;
import com.mutho.music.ui.modelviews.AlbumView;
import com.mutho.music.ui.modelviews.EmptyView;
import com.mutho.music.ui.modelviews.HorizontalRecyclerView;
import com.mutho.music.ui.modelviews.SuggestedHeaderView;
import com.mutho.music.ui.modelviews.SuggestedSongView;
import com.mutho.music.ui.views.SuggestedDividerDecoration;
import com.mutho.music.utils.AnalyticsManager;
import com.mutho.music.utils.ComparisonUtils;
import com.mutho.music.utils.DataManager;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.Operators;
import com.mutho.music.utils.PermissionUtils;
import com.mutho.music.utils.ShuttleUtils;
import com.mutho.music.utils.menu.album.AlbumMenuCallbacksAdapter;
import com.mutho.music.utils.menu.album.AlbumMenuUtils;
import com.mutho.music.utils.menu.song.SongMenuCallbacksAdapter;
import com.mutho.music.utils.menu.song.SongMenuUtils;
import com.muthomusicapps.recycler_adapter.adapter.ViewModelAdapter;
import com.muthomusicapps.recycler_adapter.model.ViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.RecyclerListener;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import kotlin.Unit;

public class SuggestedFragment extends BaseFragment implements
        SuggestedHeaderView.ClickListener,
        AlbumView.ClickListener {

    private CompositeDisposable disposables = new CompositeDisposable();

    private SongMenuCallbacksAdapter songMenuCallbacksAdapter = new SongMenuCallbacksAdapter(this, disposables);
    private AlbumMenuCallbacksAdapter albumMenuCallbacksAdapter = new AlbumMenuCallbacksAdapter(this, disposables);

    public interface SuggestedClickListener {

        void onAlbumArtistClicked(AlbumArtist albumArtist, View transitionView);

        void onAlbumClicked(Album album, View transitionView);
    }

    public class SongClickListener implements SuggestedSongView.ClickListener {

        List<Song> songs;

        public SongClickListener(List<Song> songs) {
            this.songs = songs;
        }

        @Override
        public void onSongClick(Song song, SuggestedSongView.ViewHolder holder) {
            mediaManager.playAll(songs, songs.indexOf(song), true, message -> {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                return Unit.INSTANCE;
            });
        }

        @Override
        public void onSongOverflowClicked(View v, int position, Song song) {
            PopupMenu popupMenu = new PopupMenu(getContext(), v);
            SongMenuUtils.INSTANCE.setupSongMenu(popupMenu, false);
            popupMenu.setOnMenuItemClickListener(SongMenuUtils.INSTANCE.getSongMenuClickListener(song, songMenuCallbacksAdapter));
            popupMenu.show();
        }
    }

    private static final String TAG = "SuggestedFragment";

    private static final String ARG_PAGE_TITLE = "page_title";

    private RecyclerView recyclerView;

    ViewModelAdapter adapter;

    private CompositeDisposable refreshDisposables = new CompositeDisposable();

    @Nullable
    private Disposable setItemsDisposable;

    @Inject
    RequestManager requestManager;

    private HorizontalRecyclerView favoriteRecyclerView;
    private HorizontalRecyclerView mostPlayedRecyclerView;

    @Nullable
    private SuggestedClickListener suggestedClickListener;

    public SuggestedFragment() {
    }

    public static SuggestedFragment newInstance(String pageTitle) {
        SuggestedFragment fragment = new SuggestedFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PAGE_TITLE, pageTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (getParentFragment() instanceof SuggestedClickListener) {
            suggestedClickListener = (SuggestedClickListener) getParentFragment();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        suggestedClickListener = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MuthoMusicApplication.getInstance().getAppComponent()
                .plus(new ActivityModule(getActivity()))
                .plus(new FragmentModule(this))
                .inject(this);

        adapter = new ViewModelAdapter();
        mostPlayedRecyclerView = new HorizontalRecyclerView("SuggestedFragment - mostPlayed");
        favoriteRecyclerView = new HorizontalRecyclerView("SuggestedFragment - favorite");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (recyclerView == null) {
            recyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_suggested, container, false);
            recyclerView.addItemDecoration(new SuggestedDividerDecoration(getResources()));
            recyclerView.setRecyclerListener(new RecyclerListener());

            int spanCount = ShuttleUtils.isTablet() ? 12 : 6;

            GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (!adapter.items.isEmpty() && position >= 0) {
                        ViewModel item = adapter.items.get(position);
                        if (item instanceof HorizontalRecyclerView
                                || item instanceof SuggestedHeaderView
                                || (item instanceof AlbumView && item.getViewType() == ViewType.ALBUM_LIST)
                                || (item instanceof AlbumView && item.getViewType() == ViewType.ALBUM_LIST_SMALL)
                                || item instanceof EmptyView) {
                            return spanCount;
                        }
                        if (item instanceof AlbumView && item.getViewType() == ViewType.ALBUM_CARD_LARGE) {
                            return 3;
                        }
                    }

                    return 2;
                }
            });

            recyclerView.setLayoutManager(gridLayoutManager);
        }
        if (recyclerView.getAdapter() != adapter) {
            recyclerView.setAdapter(adapter);
        }

        return recyclerView;
    }

    @Override
    public void onPause() {

        disposables.clear();

        if (refreshDisposables != null) {
            refreshDisposables.clear();
        }

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshAdapterItems();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (setItemsDisposable != null) {
            setItemsDisposable.dispose();
        }
    }

    Observable<List<ViewModel>> getMostPlayedViewModels() {
        return Playlist.mostPlayedPlaylist
                .getSongsObservable()
                .map(songs -> {
                    if (!songs.isEmpty()) {
                        List<ViewModel> viewModels = new ArrayList<>();

                        SuggestedHeader mostPlayedHeader = new SuggestedHeader(getString(R.string.mostplayed), getString(R.string.suggested_most_played_songs_subtitle), Playlist.mostPlayedPlaylist);
                        SuggestedHeaderView mostPlayedHeaderView = new SuggestedHeaderView(mostPlayedHeader);
                        mostPlayedHeaderView.setClickListener(this);
                        viewModels.add(mostPlayedHeaderView);

                        viewModels.add(mostPlayedRecyclerView);

                        Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(b.playCount, a.playCount));
                        SongClickListener songClickListener = new SongClickListener(songs);

                        AnalyticsManager.dropBreadcrumb(TAG, "mostPlayedRecyclerView.setItems()");
                        mostPlayedRecyclerView.viewModelAdapter.setItems(Stream.of(songs)
                                .map(song -> {
                                    SuggestedSongView suggestedSongView = new SuggestedSongView(song, requestManager);
                                    suggestedSongView.setClickListener(songClickListener);
                                    return (ViewModel) suggestedSongView;
                                })
                                .limit(20)
                                .toList());

                        return viewModels;
                    } else {
                        return Collections.emptyList();
                    }
                });
    }

    Observable<List<ViewModel>> getRecentlyPlayedViewModels() {
        return Playlist.recentlyPlayedPlaylist
                .getSongsObservable()
                .flatMap(songs -> Observable.just(Operators.songsToAlbums(songs)))
                .flatMapSingle(albums -> Observable.fromIterable(albums)
                        .sorted((a, b) -> ComparisonUtils.compareLong(b.lastPlayed, a.lastPlayed))
                        .flatMapSingle(album ->
                                // We need to populate the song count
                                album.getSongsSingle()
                                        .map(songs -> {
                                            album.numSongs = songs.size();
                                            return album;
                                        })
                                        .filter(a -> a.numSongs > 0)
                                        .toSingle()
                        )
                        .sorted((a, b) -> ComparisonUtils.compareLong(b.lastPlayed, a.lastPlayed))
                        .take(6)
                        .toList()
                )
                .map(albums -> {
                    if (!albums.isEmpty()) {
                        List<ViewModel> viewModels = new ArrayList<>();

                        SuggestedHeader recentlyPlayedHeader =
                                new SuggestedHeader(getString(R.string.suggested_recent_title), getString(R.string.suggested_recent_subtitle), Playlist.recentlyPlayedPlaylist);
                        SuggestedHeaderView recentlyPlayedHeaderView = new SuggestedHeaderView(recentlyPlayedHeader);
                        recentlyPlayedHeaderView.setClickListener(this);
                        viewModels.add(recentlyPlayedHeaderView);

                        viewModels.addAll(Stream.of(albums)
                                .map(album -> {
                                    AlbumView albumView = new AlbumView(album, ViewType.ALBUM_LIST_SMALL, requestManager);
                                    albumView.setClickListener(this);
                                    return albumView;
                                }).toList());

                        return viewModels;
                    } else {
                        return Collections.emptyList();
                    }
                });
    }

    @SuppressLint("CheckResult")
    Observable<List<ViewModel>> getFavoriteSongViewModels() {

        Observable<List<Song>> favoritesSongs = DataManager.getInstance().getFavoriteSongsRelay()
                .take(20);

        return Observable.combineLatest(
                favoritesSongs,
                Playlist.favoritesPlaylist()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toObservable(),
                (songs, playlist) -> {
                    if (!songs.isEmpty()) {
                        List<ViewModel> viewModels = new ArrayList<>();

                        SuggestedHeader favoriteHeader = new SuggestedHeader(getString(R.string.fav_title), getString(R.string.suggested_favorite_subtitle), playlist);
                        SuggestedHeaderView favoriteHeaderView = new SuggestedHeaderView(favoriteHeader);
                        favoriteHeaderView.setClickListener(SuggestedFragment.this);
                        viewModels.add(favoriteHeaderView);

                        viewModels.add(favoriteRecyclerView);

                        SongClickListener songClickListener = new SongClickListener(songs);
                        AnalyticsManager.dropBreadcrumb(TAG, "favoriteRecyclerView.setItems()");
                        favoriteRecyclerView.viewModelAdapter.setItems(Stream.of(songs).map(song -> {
                            SuggestedSongView suggestedSongView = new SuggestedSongView(song, requestManager);
                            suggestedSongView.setClickListener(songClickListener);
                            return (ViewModel) suggestedSongView;
                        }).toList());

                        return viewModels;
                    } else {
                        return Collections.emptyList();
                    }
                });
    }

    Observable<List<ViewModel>> getRecentlyAddedViewModels() {
        return Playlist.recentlyAddedPlaylist
                .getSongsObservable()
                .flatMap(songs -> Observable.just(Operators.songsToAlbums(songs)))
                .flatMapSingle(source -> Observable.fromIterable(source)
                        .sorted((a, b) -> ComparisonUtils.compareLong(b.dateAdded, a.dateAdded))
                        .take(20)
                        .toList())
                .map(albums -> {
                    if (!albums.isEmpty()) {
                        List<ViewModel> viewModels = new ArrayList<>();

                        SuggestedHeader recentlyAddedHeader =
                                new SuggestedHeader(getString(R.string.recentlyadded), getString(R.string.suggested_recently_added_subtitle), Playlist.recentlyAddedPlaylist);
                        SuggestedHeaderView recentlyAddedHeaderView = new SuggestedHeaderView(recentlyAddedHeader);
                        recentlyAddedHeaderView.setClickListener(this);
                        viewModels.add(recentlyAddedHeaderView);

                        viewModels.addAll(Stream.of(albums).map(album -> {
                            AlbumView albumView = new AlbumView(album, ViewType.ALBUM_CARD, requestManager);
                            albumView.setClickListener(this);
                            return albumView;
                        }).toList());

                        return viewModels;
                    } else {
                        return Collections.emptyList();
                    }
                });
    }

    void refreshAdapterItems() {
        PermissionUtils.RequestStoragePermissions(() -> {
            if (getActivity() != null && isAdded()) {
                refreshDisposables.add(Observable.combineLatest(
                        getMostPlayedViewModels(),
                        getRecentlyPlayedViewModels(),
                        getFavoriteSongViewModels().switchIfEmpty(Observable.just(Collections.emptyList())),
                        getRecentlyAddedViewModels(),
                        (mostPlayedSongs1, recentlyPlayedAlbums1, favoriteSongs1, recentlyAddedAlbums1) -> {
                            List<ViewModel> items = new ArrayList<>();
                            items.addAll(mostPlayedSongs1);
                            items.addAll(recentlyPlayedAlbums1);
                            items.addAll(favoriteSongs1);
                            items.addAll(recentlyAddedAlbums1);
                            return items;
                        })
                        .debounce(200, TimeUnit.MILLISECONDS)
                        .switchIfEmpty(Observable.just(Collections.emptyList()))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                adaptableItems -> {
                                    if (adaptableItems.isEmpty()) {
                                        AnalyticsManager.dropBreadcrumb(TAG, "refreshAdapterItems() (empty)");
                                        setItemsDisposable = adapter.setItems(Collections.singletonList((new EmptyView(R.string.empty_suggested))));
                                    } else {
                                        AnalyticsManager.dropBreadcrumb(TAG, "refreshAdapterItems()");
                                        setItemsDisposable = adapter.setItems(adaptableItems);
                                    }
                                },
                                error -> LogUtils.logException(TAG, "Error setting items", error))
                );
            }
        });
    }

    @Override
    public void onAlbumClick(int position, AlbumView albumView, AlbumView.ViewHolder viewHolder) {
        if (suggestedClickListener != null) {
            suggestedClickListener.onAlbumClicked(albumView.album, viewHolder.imageOne);
        }
    }

    @Override
    public boolean onAlbumLongClick(int position, AlbumView albumView) {
        return false;
    }

    @Override
    public void onAlbumOverflowClicked(View v, Album album) {
        PopupMenu menu = new PopupMenu(getContext(), v);
        AlbumMenuUtils.INSTANCE.setupAlbumMenu(menu);
        menu.setOnMenuItemClickListener(AlbumMenuUtils.INSTANCE.getAlbumMenuClickListener(getContext(), mediaManager, album, albumMenuCallbacksAdapter));
        menu.show();
    }

    @Override
    public void onSuggestedHeaderClick(SuggestedHeader suggestedHeader) {
        getNavigationController().pushViewController(PlaylistDetailFragment.newInstance(suggestedHeader.playlist), "PlaylistFragment");
    }

    @Override
    protected String screenName() {
        return TAG;
    }
}
