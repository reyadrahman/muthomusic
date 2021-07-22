package com.mutho.music.ui.detail.album;

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
import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mutho.music.R;
import com.mutho.music.glide.utils.AlwaysCrossFade;
import com.mutho.music.model.Album;
import com.mutho.music.model.ArtworkProvider;
import com.mutho.music.model.Song;
import com.mutho.music.playback.MediaManager;
import com.mutho.music.tagger.TaggerDialog;
import com.mutho.music.ui.dialog.BiographyDialog;
import com.mutho.music.ui.dialog.DeleteDialog;
import com.mutho.music.ui.drawer.DrawerLockManager;
import com.mutho.music.ui.fragments.BaseFragment;
import com.mutho.music.ui.fragments.TransitionListenerAdapter;
import com.mutho.music.ui.modelviews.DiscNumberView;
import com.mutho.music.ui.modelviews.EmptyView;
import com.mutho.music.ui.modelviews.SelectableViewModel;
import com.mutho.music.ui.modelviews.SongView;
import com.mutho.music.ui.modelviews.SubheaderView;
import com.mutho.music.ui.views.ContextualToolbar;
import com.mutho.music.ui.views.ContextualToolbarHost;
import com.mutho.music.utils.ActionBarUtils;
import com.mutho.music.utils.ArtworkDialog;
import com.mutho.music.utils.ContextualToolbarHelper;
import com.mutho.music.utils.Operators;
import com.mutho.music.utils.PlaceholderProvider;
import com.mutho.music.utils.PlaylistUtils;
import com.mutho.music.utils.ResourceUtils;
import com.mutho.music.utils.ShuttleUtils;
import com.mutho.music.utils.StringUtils;
import com.mutho.music.utils.TypefaceManager;
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

import static com.afollestad.aesthetic.Rx.distinctToMainThread;

public class AlbumDetailFragment extends BaseFragment implements
        AlbumDetailView,
        Toolbar.OnMenuItemClickListener,
        DrawerLockManager.DrawerLock,
        ContextualToolbarHost {

    private static final String TAG = "BaseDetailFragment";

    private static final String ARG_TRANSITION_NAME = "transition_name";

    public static String ARG_ALBUM = "album";

    private Album album;

    private AlbumDetailPresenter presenter;

    private ViewModelAdapter adapter = new ViewModelAdapter();

    private RequestManager requestManager;

    private CompositeDisposable disposables = new CompositeDisposable();

    private SongMenuCallbacksAdapter songMenuCallbacksAdapter = new SongMenuCallbacksAdapter(this, disposables);

    @Nullable
    private ColorStateList collapsingToolbarTextColor;
    @Nullable
    private ColorStateList collapsingToolbarSubTextColor;

    private EmptyView emptyView = new EmptyView(R.string.empty_songlist);

    @Nullable
    private Disposable setItemsDisposable = null;

    @Nullable
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

    public static AlbumDetailFragment newInstance(Album album, String transitionName) {
        Bundle args = new Bundle();
        AlbumDetailFragment fragment = new AlbumDetailFragment();
        args.putSerializable(ARG_ALBUM, album);
        args.putString(ARG_TRANSITION_NAME, transitionName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        //noinspection ConstantConditions
        album = (Album) getArguments().getSerializable(ARG_ALBUM);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        presenter = new AlbumDetailPresenter(mediaManager, album);

        requestManager = Glide.with(this);

        setHasOptionsMenu(true);

        setEnterSharedElementCallback(enterSharedElementCallback);

        isFirstLoad = true;
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

        toolbarLayout.setTitle(album.name);
        toolbarLayout.setSubtitle(album.albumArtistName);
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

        presenter.bindView(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        presenter.loadData();

        DrawerLockManager.getInstance().addDrawerLock(this);
    }

    @Override
    public void onPause() {

        DrawerLockManager.getInstance().removeDrawerLock(this);

        super.onPause();
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

        // Create playlist menu
        final SubMenu sub = toolbar.getMenu().findItem(R.id.addToPlaylist).getSubMenu();
        disposables.add(PlaylistUtils.createUpdatingPlaylistMenu(sub).subscribe());

        // Inflate sorting menus
        MenuItem item = toolbar.getMenu().findItem(R.id.sorting);
        getActivity().getMenuInflater().inflate(R.menu.menu_detail_sort_albums, item.getSubMenu());
        getActivity().getMenuInflater().inflate(R.menu.menu_detail_sort_songs, item.getSubMenu());

        toolbar.getMenu().findItem(R.id.editTags).setVisible(true);
        toolbar.getMenu().findItem(R.id.info).setVisible(true);
        toolbar.getMenu().findItem(R.id.artwork).setVisible(true);

        AlbumSortHelper.updateAlbumSortMenuItems(toolbar.getMenu(), SortManager.getInstance().getArtistDetailAlbumsSortOrder(), SortManager.getInstance().getArtistDetailAlbumsAscending());
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.play:
                presenter.playAll();
                return true;
            case R.id.playNext:
                presenter.playNext();
                return true;
            case R.id.addToQueue:
                presenter.addToQueue();
                return true;
            case MediaManager.Defs.ADD_TO_PLAYLIST:
                presenter.newPlaylist();
                return true;
            case MediaManager.Defs.PLAYLIST_SELECTED:
                presenter.playlistSelected(getContext(), item, () -> presenter.closeContextualToolbar());
                return true;
            case R.id.editTags:
                presenter.editTags();
                return true;
            case R.id.info:
                presenter.showBio();
                return true;
            case R.id.artwork:
                presenter.editArtwork();
                return true;
        }

        Integer songSortOder = SongSortHelper.handleSongMenuSortOrderClicks(item);
        if (songSortOder != null) {
            SortManager.getInstance().setAlbumDetailSongsSortOrder(songSortOder);
            presenter.loadData();
        }
        Boolean songsAsc = SongSortHelper.handleSongDetailMenuSortOrderAscClicks(item);
        if (songsAsc != null) {
            SortManager.getInstance().setAlbumDetailSongsAscending(songsAsc);
            presenter.loadData();
        }

        AlbumSortHelper.updateAlbumSortMenuItems(toolbar.getMenu(), SortManager.getInstance().getArtistDetailAlbumsSortOrder(), SortManager.getInstance().getArtistDetailAlbumsAscending());

        return super.onOptionsItemSelected(item);
    }

    void loadBackgroundImage() {

        if (album == null) {
            return;
        }

        int width = ResourceUtils.getScreenSize().width + ResourceUtils.toPixels(60);
        int height = getResources().getDimensionPixelSize(R.dimen.header_view_height);

        requestManager.load((ArtworkProvider) album)
                // Need to override the height/width, as the shared element transition tricks Glide into thinking this ImageView has
                // the same dimensions as the ImageView that the transition starts with.
                // So we'll set it to screen width (plus a little extra, which might fix an issue on some devices..)
                .override(width, height)
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .priority(Priority.HIGH)
                .placeholder(PlaceholderProvider.getInstance().getPlaceHolderDrawable(album.name, true))
                .centerCrop()
                .animate(new AlwaysCrossFade(false))
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

        int songsSortOrder = SortManager.getInstance().getAlbumDetailSongsSortOrder();

        if (!data.isEmpty()) {

            viewModels.add(new SubheaderView(StringUtils.makeSongsAndTimeLabel(getContext(), data.size(), Stream.of(data).mapToLong(song -> song.duration / 1000).sum())));

            viewModels.addAll(new ArrayList<ViewModel>(Stream.of(data)
                    .map(song -> {
                        SongView songView = new SongView(song, requestManager);
                        songView.showArtistName(false);
                        songView.showAlbumName(false);
                        songView.setShowTrackNumber(songsSortOrder == SortManager.SongSort.TRACK_NUMBER || songsSortOrder == SortManager.SongSort.DETAIL_DEFAULT);
                        songView.setClickListener(songClickListener);
                        return songView;
                    }).toList()));

            if (album.numDiscs > 1 && (songsSortOrder == SortManager.SongSort.DETAIL_DEFAULT || songsSortOrder == SortManager.SongSort.TRACK_NUMBER)) {
                int discNumber = 0;
                int length = viewModels.size();
                for (int i = 0; i < length; i++) {
                    ViewModel viewModel = viewModels.get(i);
                    if (viewModel instanceof SongView) {
                        if (discNumber != ((SongView) viewModel).song.discNumber) {
                            discNumber = ((SongView) viewModel).song.discNumber;
                            viewModels.add(i, new DiscNumberView(discNumber));
                        }
                    }
                }
            }
        } else {
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

    @Override
    public String screenName() {
        return "AlbumDetailFragment";
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
            SongMenuUtils.INSTANCE.setupSongMenu(popupMenu, false);
            popupMenu.setOnMenuItemClickListener(SongMenuUtils.INSTANCE.getSongMenuClickListener(song, songMenuCallbacksAdapter));
            popupMenu.show();
        }

        @Override
        public void onStartDrag(SongView.ViewHolder holder) {

        }
    };

    // AlbumDetailView implementation

    @Override
    public void showToast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showTaggerDialog() {
        TaggerDialog.newInstance(album).show(getChildFragmentManager());
    }

    @Override
    public void showDeleteDialog() {
        DeleteDialog.newInstance(() -> Collections.singletonList(album)).show(getChildFragmentManager());
    }

    @Override
    public void showArtworkDialog() {
        ArtworkDialog.build(getContext(), album).show();
    }

    @Override
    public void showBioDialog() {
        BiographyDialog.getArtistBiographyDialog(getContext(), album.name).show();
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