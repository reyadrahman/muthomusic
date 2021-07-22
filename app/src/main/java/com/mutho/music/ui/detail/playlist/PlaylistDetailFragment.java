package com.mutho.music.ui.detail.playlist;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CustomCollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.SharedElementCallback;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.transition.Transition;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import com.afollestad.aesthetic.Aesthetic;
import com.annimon.stream.IntStream;
import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.glide.utils.AlwaysCrossFade;
import com.mutho.music.model.Album;
import com.mutho.music.model.ArtworkProvider;
import com.mutho.music.model.Playlist;
import com.mutho.music.model.Song;
import com.mutho.music.ui.drawer.DrawerLockManager;
import com.mutho.music.ui.fragments.BaseFragment;
import com.mutho.music.ui.fragments.TransitionListenerAdapter;
import com.mutho.music.ui.modelviews.EmptyView;
import com.mutho.music.ui.modelviews.SelectableViewModel;
import com.mutho.music.ui.modelviews.SongView;
import com.mutho.music.ui.modelviews.SubheaderView;
import com.mutho.music.ui.recyclerview.ItemTouchHelperCallback;
import com.mutho.music.ui.views.ContextualToolbar;
import com.mutho.music.ui.views.ContextualToolbarHost;
import com.mutho.music.utils.ActionBarUtils;
import com.mutho.music.utils.ContextualToolbarHelper;
import com.mutho.music.utils.Operators;
import com.mutho.music.utils.PlaceholderProvider;
import com.mutho.music.utils.PlaylistUtils;
import com.mutho.music.utils.ResourceUtils;
import com.mutho.music.utils.ShuttleUtils;
import com.mutho.music.utils.StringUtils;
import com.mutho.music.utils.TypefaceManager;
import com.mutho.music.utils.menu.playlist.PlaylistMenuCallbacksAdapter;
import com.mutho.music.utils.menu.playlist.PlaylistMenuUtils;
import com.mutho.music.utils.menu.song.SongMenuCallbacksAdapter;
import com.mutho.music.utils.menu.song.SongMenuUtils;
import com.mutho.music.utils.sorting.AlbumSortHelper;
import com.mutho.music.utils.sorting.SongSortHelper;
import com.mutho.music.utils.sorting.SortManager;
import com.muthomusicapps.recycler_adapter.adapter.CompletionListUpdateCallbackAdapter;
import com.muthomusicapps.recycler_adapter.adapter.ViewModelAdapter;
import com.muthomusicapps.recycler_adapter.model.ViewModel;
import com.muthomusicapps.recycler_adapter.recyclerview.RecyclerListener;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

import static com.afollestad.aesthetic.Rx.distinctToMainThread;

public class PlaylistDetailFragment extends BaseFragment implements
        PlaylistDetailView,
        Toolbar.OnMenuItemClickListener,
        DrawerLockManager.DrawerLock,
        ContextualToolbarHost {

    private static final String TAG = "BaseDetailFragment";

    private static final String ARG_TRANSITION_NAME = "transition_name";

    public static String ARG_PLAYLIST = "playlist";

    private Playlist playlist;

    protected PlaylistDetailPresenter presenter;

    protected ViewModelAdapter adapter = new ViewModelAdapter();

    private RequestManager requestManager;

    protected CompositeDisposable disposables = new CompositeDisposable();

    private PlaylistMenuCallbacksAdapter playlistMenuCallbacksAdapter;

    private SongMenuCallbacksAdapter songMenuCallbacksAdapter;

    @Nullable
    private ColorStateList collapsingToolbarTextColor;
    @Nullable
    private ColorStateList collapsingToolbarSubTextColor;

    private EmptyView emptyView = new EmptyView(R.string.empty_songlist);

    @Nullable
    private Disposable setItemsDisposable = null;

    private ContextualToolbarHelper<Single<List<Song>>> contextualToolbarHelper;

    private Unbinder unbinder;

    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;

    @BindView(R.id.toolbar_layout)
    CustomCollapsingToolbarLayout toolbarLayout;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.textProtectionScrim)
    View textProtectionScrim;

    @BindView(R.id.textProtectionScrim2)
    View textProtectionScrim2;

    @BindView(R.id.fab)
    FloatingActionButton fab;

    @BindView(R.id.background)
    ImageView headerImageView;

    @BindView(R.id.contextualToolbar)
    ContextualToolbar contextualToolbar;

    private boolean isFirstLoad = true;

    public PlaylistDetailFragment() {

    }

    public static PlaylistDetailFragment newInstance(Playlist playlist) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_PLAYLIST, playlist);
        PlaylistDetailFragment fragment = new PlaylistDetailFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        //noinspection ConstantConditions
        playlist = (Playlist) getArguments().getSerializable(ARG_PLAYLIST);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        presenter = new PlaylistDetailPresenter(mediaManager, playlist);

        requestManager = Glide.with(this);

        setHasOptionsMenu(true);

        setEnterSharedElementCallback(enterSharedElementCallback);

        isFirstLoad = true;

        playlistMenuCallbacksAdapter = new PlaylistMenuCallbacksAdapter(this, disposables) {
            @Override
            public void onPlaylistDeleted() {
                super.onPlaylistDeleted();

                Toast.makeText(getContext(), R.string.playlist_deleted_message, Toast.LENGTH_SHORT).show();
                getNavigationController().popViewController();
            }
        };

        songMenuCallbacksAdapter = new SongMenuCallbacksAdapter(this, disposables) {

            @Override
            public void removeSong(@NotNull Song song) {
                ViewModel songView = Stream.of(adapter.items).filter(value -> value instanceof SongView && ((SongView) value).song == song).findFirst().orElse(null);
                int index = adapter.items.indexOf(songView);
                playlist.removeSong(song, success -> {
                    if (!success) {
                        // Playlist removal failed, re-insert adapter item
                        adapter.addItem(index, songView);
                    }
                });
            }
        };
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        unbinder = ButterKnife.bind(this, view);

        toolbar.setNavigationOnClickListener(v -> getNavigationController().popViewController());

        if (ShuttleUtils.canDrawBehindStatusBar()) {
            toolbar.getLayoutParams().height = (int) (ActionBarUtils.getActionBarHeight(getContext()) + ActionBarUtils.getStatusBarHeight(getContext()));
            toolbar.setPadding(toolbar.getPaddingLeft(), (int) (toolbar.getPaddingTop() + ActionBarUtils.getStatusBarHeight(getContext())), toolbar.getPaddingRight(), toolbar.getPaddingBottom());
        }

        setupToolbarMenu(toolbar);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setRecyclerListener(new RecyclerListener());
        recyclerView.setAdapter(adapter);

        if (isFirstLoad) {
            recyclerView.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(getContext(), R.anim.layout_animation_from_bottom));
        }

        toolbarLayout.setTitle(playlist.name);
        toolbarLayout.setSubtitle(null);
        toolbarLayout.setExpandedTitleTypeface(TypefaceManager.getInstance().getTypeface(TypefaceManager.SANS_SERIF_LIGHT));
        toolbarLayout.setCollapsedTitleTypeface(TypefaceManager.getInstance().getTypeface(TypefaceManager.SANS_SERIF));

        setupContextualToolbar();

        String transitionName = getArguments().getString(ARG_TRANSITION_NAME);
        ViewCompat.setTransitionName(headerImageView, transitionName);

        if (isFirstLoad) {
            fab.setVisibility(View.GONE);
        }

        if (transitionName == null) {
            fadeInUi();
        }

        loadBackgroundImage();

        disposables.add(Aesthetic.get(getContext())
                .colorPrimary()
                .compose(distinctToMainThread())
                .subscribe(primaryColor -> {
                    toolbarLayout.setContentScrimColor(primaryColor);
                    toolbarLayout.setBackgroundColor(primaryColor);
                }));

        itemTouchHelper.attachToRecyclerView(recyclerView);

        presenter.bindView(this);
    }

    @Override
    public void onPause() {

        DrawerLockManager.getInstance().removeDrawerLock(this);

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        presenter.loadData();

        DrawerLockManager.getInstance().addDrawerLock(this);
    }

    @Override
    public void onDestroyView() {

        if (setItemsDisposable != null) {
            setItemsDisposable.dispose();
        }

        disposables.clear();

        presenter.unbindView(this);

        unbinder.unbind();

        isFirstLoad = false;

        super.onDestroyView();
    }

    private void setupToolbarMenu(Toolbar toolbar) {
        toolbar.inflateMenu(R.menu.menu_detail_sort);

        setupCastMenu(toolbar.getMenu());

        toolbar.setOnMenuItemClickListener(this);

        // Inflate sorting menus
        MenuItem item = toolbar.getMenu().findItem(R.id.sorting);
        getActivity().getMenuInflater().inflate(R.menu.menu_detail_sort_albums, item.getSubMenu());
        getActivity().getMenuInflater().inflate(R.menu.menu_detail_sort_songs, item.getSubMenu());

        PlaylistMenuUtils.INSTANCE.setupPlaylistMenu(toolbar, playlist);

        toolbar.getMenu().findItem(R.id.editTags).setVisible(true);
        toolbar.getMenu().findItem(R.id.info).setVisible(true);
        toolbar.getMenu().findItem(R.id.artwork).setVisible(true);
        toolbar.getMenu().findItem(R.id.playPlaylist).setVisible(false);

        AlbumSortHelper.updateAlbumSortMenuItems(toolbar.getMenu(), SortManager.getInstance().getPlaylistDetailAlbumsSortOrder(playlist),
                SortManager.getInstance().getPlaylistDetailAlbumsAscending(playlist));
        SongSortHelper.updateSongSortMenuItems(toolbar.getMenu(), SortManager.getInstance().getPlaylistDetailSongsSortOrder(playlist),
                SortManager.getInstance().getPlaylistDetailSongsAscending(playlist));
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (PlaylistMenuUtils.INSTANCE.handleMenuItemClicks(item, mediaManager, playlist, playlistMenuCallbacksAdapter)) {
            return true;
        }

        Integer albumSortOrder = AlbumSortHelper.handleAlbumDetailMenuSortOrderClicks(item);
        if (albumSortOrder != null) {
            SortManager.getInstance().setPlaylistDetailAlbumsSortOrder(playlist, albumSortOrder);
            presenter.loadData();
        }
        Boolean albumsAsc = AlbumSortHelper.handleAlbumDetailMenuSortOrderAscClicks(item);
        if (albumsAsc != null) {
            SortManager.getInstance().setPlaylistDetailAlbumsAscending(playlist, albumsAsc);
            presenter.loadData();
        }
        Integer songSortOrder = SongSortHelper.handleSongMenuSortOrderClicks(item);
        if (songSortOrder != null) {
            SortManager.getInstance().setPlaylistDetailSongsSortOrder(playlist, songSortOrder);
            presenter.loadData();
        }
        Boolean songsAsc = SongSortHelper.handleSongDetailMenuSortOrderAscClicks(item);
        if (songsAsc != null) {
            SortManager.getInstance().setPlaylistDetailSongsAscending(playlist, songsAsc);
            presenter.loadData();
        }

        AlbumSortHelper.updateAlbumSortMenuItems(toolbar.getMenu(), SortManager.getInstance().getPlaylistDetailAlbumsSortOrder(playlist),
                SortManager.getInstance().getPlaylistDetailAlbumsAscending(playlist));
        SongSortHelper.updateSongSortMenuItems(toolbar.getMenu(), SortManager.getInstance().getPlaylistDetailSongsSortOrder(playlist),
                SortManager.getInstance().getPlaylistDetailSongsAscending(playlist));

        return super.onOptionsItemSelected(item);
    }

    void loadBackgroundImage() {

        int width = ResourceUtils.getScreenSize().width + ResourceUtils.toPixels(60);
        int height = getResources().getDimensionPixelSize(R.dimen.header_view_height);

        requestManager.load((ArtworkProvider) null)
                // Need to override the height/width, as the shared element transition tricks Glide into thinking this ImageView has
                // the same dimensions as the ImageView that the transition starts with.
                // So we'll set it to screen width (plus a little extra, which might fix an issue on some devices..)
                .override(width, height)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .priority(Priority.HIGH)
                .placeholder(PlaceholderProvider.getInstance().getPlaceHolderDrawable(playlist.name, true))
                .centerCrop()
                .animate(new AlwaysCrossFade(false))
                .into(headerImageView);
    }

    @Override
    public void fadeInSlideShowAlbum(@Nullable Album previousAlbum, @NotNull Album newAlbum) {
        //This crazy business is what's required to have a smooth Glide crossfade with no 'white flicker'
        requestManager.load(newAlbum)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .priority(Priority.HIGH)
                .error(PlaceholderProvider.getInstance().getPlaceHolderDrawable(newAlbum.name, true))
                .centerCrop()
                .thumbnail(Glide
                        .with(this)
                        .load(previousAlbum)
                        .centerCrop())
                .crossFade(600)
                .into(headerImageView);
    }

    @OnClick(R.id.fab)
    void onFabClicked() {
        presenter.fabClicked();
    }

    @Override
    public void setSharedElementEnterTransition(Object transition) {
        super.setSharedElementEnterTransition(transition);
        ((Transition) transition).addListener(getSharedElementEnterTransitionListenerAdapter());
    }

    private TransitionListenerAdapter getSharedElementEnterTransitionListenerAdapter() {
        return new TransitionListenerAdapter() {
            @Override
            public void onTransitionEnd(Transition transition) {
                transition.removeListener(this);
                fadeInUi();
            }
        };
    }

    void fadeInUi() {

        if (textProtectionScrim == null || textProtectionScrim2 == null || fab == null) {
            return;
        }

        //Fade in the text protection scrim
        textProtectionScrim.setAlpha(0f);
        textProtectionScrim.setVisibility(View.VISIBLE);
        ObjectAnimator fadeAnimator = ObjectAnimator.ofFloat(textProtectionScrim, View.ALPHA, 0f, 1f);
        fadeAnimator.setDuration(600);
        fadeAnimator.start();

        textProtectionScrim2.setAlpha(0f);
        textProtectionScrim2.setVisibility(View.VISIBLE);
        fadeAnimator = ObjectAnimator.ofFloat(textProtectionScrim2, View.ALPHA, 0f, 1f);
        fadeAnimator.setDuration(600);
        fadeAnimator.start();

        //Fade & grow the FAB
        fab.setAlpha(0f);
        fab.setVisibility(View.VISIBLE);

        fadeAnimator = ObjectAnimator.ofFloat(fab, View.ALPHA, 0.5f, 1f);
        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(fab, View.SCALE_X, 0f, 1f);
        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(fab, View.SCALE_Y, 0f, 1f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(fadeAnimator, scaleXAnimator, scaleYAnimator);
        animatorSet.setDuration(250);
        animatorSet.start();
    }

    @Override
    public void setData(@NonNull List<Song> data) {
        List<ViewModel> viewModels = new ArrayList<>();

        if (!data.isEmpty()) {
            List<ViewModel> items = new ArrayList<>();

            items.add(new SubheaderView(StringUtils.makeSongsAndTimeLabel(MuthoMusicApplication.getInstance(), data.size(), Stream.of(data).mapToLong(song -> song.duration / 1000).sum())));

            items.addAll(Stream.of(data)
                    .map(song -> {
                        SongView songView = new SongView(song, requestManager);
                        songView.showPlayCount(playlist.type == Playlist.Type.MOST_PLAYED);
                        if (playlist.canEdit && SortManager.getInstance().getPlaylistDetailSongsSortOrder(playlist) == SortManager.SongSort.DETAIL_DEFAULT) {
                            songView.setEditable(true);
                        }
                        songView.setClickListener(songClickListener);
                        return songView;
                    }).toList());

            viewModels.addAll(items);
        }
        if (viewModels.isEmpty()) {
            viewModels.add(emptyView);
        }

        setItemsDisposable = adapter.setItems(viewModels, new CompletionListUpdateCallbackAdapter() {
            @Override
            public void onComplete() {
                if (recyclerView != null) {
                    recyclerView.scheduleLayoutAnimation();
                }
            }
        });
    }

    @Override
    public ContextualToolbar getContextualToolbar() {
        return contextualToolbar;
    }

    private void setupContextualToolbar() {

        ContextualToolbar contextualToolbar = ContextualToolbar.findContextualToolbar(this);
        if (contextualToolbar != null) {

            contextualToolbar.setTransparentBackground(true);

            contextualToolbar.getMenu().clear();
            contextualToolbar.inflateMenu(R.menu.context_menu_general);
            SubMenu sub = contextualToolbar.getMenu().findItem(R.id.addToPlaylist).getSubMenu();
            disposables.add(PlaylistUtils.createUpdatingPlaylistMenu(sub).subscribe());

            contextualToolbar.setOnMenuItemClickListener(
                    SongMenuUtils.INSTANCE.getSongMenuClickListener(Single.defer(() -> Operators.reduceSongSingles(contextualToolbarHelper.getItems())), songMenuCallbacksAdapter)
            );

            contextualToolbarHelper = new ContextualToolbarHelper<Single<List<Song>>>(contextualToolbar, new ContextualToolbarHelper.Callback() {

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
            }) {
                @Override
                public void start() {
                    super.start();
                    // Need to hide the collapsed text, as it overlaps the contextual toolbar
                    collapsingToolbarTextColor = toolbarLayout.getCollapsedTitleTextColor();
                    collapsingToolbarSubTextColor = toolbarLayout.getCollapsedSubTextColor();
                    toolbarLayout.setCollapsedTitleTextColor(0x01FFFFFF);
                    toolbarLayout.setCollapsedSubTextColor(0x01FFFFFF);

                    toolbar.setVisibility(View.GONE);
                }

                @Override
                public void finish() {
                    if (toolbarLayout != null && collapsingToolbarTextColor != null && collapsingToolbarSubTextColor != null) {
                        toolbarLayout.setCollapsedTitleTextColor(collapsingToolbarTextColor);
                        toolbarLayout.setCollapsedSubTextColor(collapsingToolbarSubTextColor);
                    }
                    if (toolbar != null) {
                        toolbar.setVisibility(View.VISIBLE);
                    }
                    super.finish();
                }
            };
        }
    }

    private SharedElementCallback enterSharedElementCallback = new SharedElementCallback() {
        @Override
        public void onSharedElementStart(List<String> sharedElementNames, List<View> sharedElements, List<View> sharedElementSnapshots) {
            super.onSharedElementStart(sharedElementNames, sharedElements, sharedElementSnapshots);

            if (fab != null) {
                fab.setVisibility(View.GONE);
            }
        }
    };

    ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelperCallback(
            (fromPosition, toPosition) -> adapter.moveItem(fromPosition, toPosition),
            (fromPosition, toPosition) -> {
                SongView from = (SongView) adapter.items.get(fromPosition);
                SongView to = (SongView) adapter.items.get(toPosition);

                List<SongView> songViews = Stream.of(adapter.items)
                        .filter(itemView -> itemView instanceof SongView)
                        .map(itemView -> ((SongView) itemView))
                        .toList();

                int adjustedFrom = IntStream.range(0, songViews.size())
                        .filter(i -> from.equals(songViews.get(i)))
                        .findFirst()
                        .orElse(-1);

                int adjustedTo = IntStream.range(0, songViews.size())
                        .filter(i -> to.equals(songViews.get(i)))
                        .findFirst()
                        .orElse(-1);

                if (adjustedFrom != -1 && adjustedTo != -1) {
                    playlist.moveSong(adjustedFrom, adjustedTo);
                }
            },
            () -> {
                // Nothing to do
            }) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            if (viewHolder.getItemViewType() == target.getItemViewType()) {
                return super.onMove(recyclerView, viewHolder, target);
            }
            return false;
        }
    });

    @Override
    protected String screenName() {
        return "PlaylistDetailFragment";
    }

    public SongView.ClickListener songClickListener = new SongView.ClickListener() {
        @Override
        public void onSongClick(int position, SongView songView) {
            if (!contextualToolbarHelper.handleClick(songView, Single.just(Collections.singletonList(songView.song)))) {
                presenter.songClicked(songView.song);
            }
        }

        @Override
        public boolean onSongLongClick(int position, SongView songView) {
            return contextualToolbarHelper.handleLongClick(songView, Single.just(Collections.singletonList(songView.song)));
        }

        @Override
        public void onSongOverflowClick(int position, View v, Song song) {
            PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
            SongMenuUtils.INSTANCE.setupSongMenu(popupMenu, playlist.canEdit);
            popupMenu.setOnMenuItemClickListener(SongMenuUtils.INSTANCE.getSongMenuClickListener(song, songMenuCallbacksAdapter));
            popupMenu.show();
        }

        @Override
        public void onStartDrag(SongView.ViewHolder holder) {
            itemTouchHelper.startDrag(holder);
        }
    };

    // PlaylistDetailView implementation

    @Override
    public void showToast(@NonNull String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showCreatePlaylistDialog(@NonNull List<Song> songs) {
        PlaylistUtils.createPlaylistDialog(getContext(), songs, () -> presenter.closeContextualToolbar());
    }

    @Override
    public void closeContextualToolbar() {
        if (contextualToolbarHelper != null) {
            contextualToolbarHelper.finish();
        }
    }
}