package com.mutho.music.ui.drawer;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.afollestad.aesthetic.Aesthetic;
import com.afollestad.aesthetic.Rx;
import com.afollestad.materialdialogs.MaterialDialog;
import com.annimon.stream.Stream;
import com.bignerdranch.expandablerecyclerview.model.Parent;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.dagger.module.ActivityModule;
import com.mutho.music.dagger.module.FragmentModule;
import com.mutho.music.model.Playlist;
import com.mutho.music.model.Song;
import com.mutho.music.ui.fragments.BaseFragment;
import com.mutho.music.ui.presenters.PlayerPresenter;
import com.mutho.music.ui.views.CircleImageView;
import com.mutho.music.ui.views.PlayerViewAdapter;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.PlaceholderProvider;
import com.mutho.music.utils.SleepTimer;
import com.mutho.music.utils.menu.playlist.PlaylistMenuCallbacksAdapter;
import com.mutho.music.utils.menu.playlist.PlaylistMenuUtils;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class DrawerFragment extends BaseFragment implements
        DrawerView,
        View.OnCreateContextMenuListener,
        DrawerParent.ClickListener {

    private static final String TAG = "DrawerFragment";

    private static final String STATE_SELECTED_DRAWER_PARENT = "selected_drawer_parent";

    private static final String STATE_SELECTED_PLAYLIST = "selected_drawer_playlist";

    private DrawerAdapter adapter;

    private DrawerParent playlistDrawerParent;

    private View rootView;

    @BindView(R.id.line1)
    TextView trackNameView;

    @BindView(R.id.line2)
    TextView artistNameView;

    @BindView(R.id.placeholder_text)
    TextView placeholderText;

    @BindView(R.id.background_image)
    ImageView backgroundImage;

    @BindView(R.id.artist_image)
    CircleImageView artistImage;

    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;

    @DrawerParent.Type
    private int selectedDrawerParent = DrawerParent.Type.LIBRARY;

    private Playlist currentSelectedPlaylist = null;

    @Inject
    PlayerPresenter playerPresenter;

    @Inject
    DrawerPresenter drawerPresenter;

    RequestManager requestManager;

    Drawable backgroundPlaceholder;

    private CompositeDisposable disposables = new CompositeDisposable();

    private List<Parent<DrawerChild>> drawerParents;

    private PlaylistMenuCallbacksAdapter playlistMenuCallbacksAdapter = new PlaylistMenuCallbacksAdapter(this, disposables);

    public DrawerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MuthoMusicApplication.getInstance().getAppComponent()
                .plus(new ActivityModule(getActivity()))
                .plus(new FragmentModule(this))
                .inject(this);

        if (savedInstanceState != null) {
            selectedDrawerParent = savedInstanceState.getInt(STATE_SELECTED_DRAWER_PARENT, DrawerParent.Type.LIBRARY);
            currentSelectedPlaylist = (Playlist) savedInstanceState.get(STATE_SELECTED_PLAYLIST);
        }

        requestManager = Glide.with(this);

        backgroundPlaceholder = ContextCompat.getDrawable(getContext(), R.drawable.ic_drawer_header_placeholder);

        playlistDrawerParent = DrawerParent.playlistsParent;

        drawerParents = new ArrayList<>();
        drawerParents.add(DrawerParent.libraryParent);
        drawerParents.add(DrawerParent.folderParent);
        drawerParents.add(playlistDrawerParent);
        drawerParents.add(new DrawerDivider());
        drawerParents.add(DrawerParent.sleepTimerParent);
        drawerParents.add(DrawerParent.equalizerParent);
        drawerParents.add(DrawerParent.settingsParent);
        drawerParents.add(DrawerParent.supportParent);

        adapter = new DrawerAdapter(drawerParents);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (rootView == null) {
            rootView = inflater.inflate(R.layout.fragment_drawer, container, false);

            ButterKnife.bind(this, rootView);

            recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        }
        if (recyclerView.getAdapter() != adapter) {
            recyclerView.setAdapter(adapter);
        }

        setDrawerItemSelected(selectedDrawerParent);

        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        drawerPresenter.bindView(this);
        playerPresenter.bindView(playerViewAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        disposables.add(Aesthetic.get(getContext())
                .colorPrimary()
                .compose(Rx.distinctToMainThread())
                .subscribe(color -> {
                    backgroundPlaceholder.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
                    if (mediaManager.getSong() == null) {
                        backgroundImage.setImageDrawable(backgroundPlaceholder);
                    }
                }));

        playerPresenter.updateTrackInfo();

        disposables.add(SleepTimer.getInstance().getCurrentTimeObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(aLong -> Stream.of(drawerParents)
                        .forEachIndexed((i, drawerParent) -> {
                            if (aLong > 0 && (drawerParent instanceof DrawerParent) && ((DrawerParent) drawerParent).type == DrawerParent.Type.SLEEP_TIMER) {
                                ((DrawerParent) drawerParent).setTimeRemaining(aLong);
                                adapter.notifyParentChanged(i);
                            }
                        }), throwable -> LogUtils.logException(TAG, "Error observing sleep time", throwable)));

        disposables.add(SleepTimer.getInstance().getTimerActiveSubject()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(active -> Stream.of(drawerParents)
                                .forEachIndexed((i, drawerParent) -> {
                                    if ((drawerParent instanceof DrawerParent) && ((DrawerParent) drawerParent).type == DrawerParent.Type.SLEEP_TIMER) {
                                        ((DrawerParent) drawerParent).setTimerActive(active);
                                        adapter.notifyParentChanged(i);
                                    }
                                }),
                        throwable -> LogUtils.logException(TAG, "Error observing sleep state", throwable))
        );

        Stream.of(drawerParents)
                .filter(parent -> parent instanceof DrawerParent)
                .forEach(parent -> ((DrawerParent) parent).setListener(this));
    }

    @Override
    public void onPause() {
        disposables.clear();

        Stream.of(drawerParents)
                .filter(parent -> parent instanceof DrawerParent)
                .forEach(parent -> ((DrawerParent) parent).setListener(null));

        super.onPause();
    }

    @Override
    public void onDestroyView() {
        drawerPresenter.unbindView(this);
        playerPresenter.unbindView(playerViewAdapter);

        super.onDestroyView();
    }

    @Override
    public void onClick(DrawerParent drawerParent) {
        drawerPresenter.onDrawerItemClicked(drawerParent);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_SELECTED_DRAWER_PARENT, selectedDrawerParent);
        outState.putSerializable(STATE_SELECTED_PLAYLIST, currentSelectedPlaylist);
    }

    void onPlaylistClicked(Playlist playlist) {
        drawerPresenter.onPlaylistClicked(playlist);
    }

    @Override
    public void setPlaylistItems(List<Playlist> playlists) {

        int parentPosition = adapter.getParentList().indexOf(playlistDrawerParent);

        int prevItemCount = playlistDrawerParent.children.size();
        playlistDrawerParent.children.clear();
        adapter.notifyChildRangeRemoved(parentPosition, 0, prevItemCount);

        List<DrawerChild> drawerChildren = Stream.of(playlists)
                .map(playlist -> {
                    DrawerChild drawerChild = new DrawerChild(playlist);
                    drawerChild.setListener(new DrawerChild.ClickListener() {
                        @Override
                        public void onClick(Playlist playlist) {
                            onPlaylistClicked(playlist);
                        }

                        @Override
                        public void onOverflowClick(View view, Playlist playlist) {
                            PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
                            PlaylistMenuUtils.INSTANCE.setupPlaylistMenu(popupMenu, playlist);
                            popupMenu.setOnMenuItemClickListener(PlaylistMenuUtils.INSTANCE.getPlaylistPopupMenuClickListener(mediaManager, playlist, playlistMenuCallbacksAdapter));
                            popupMenu.show();
                        }
                    });
                    return drawerChild;
                }).toList();

        playlistDrawerParent.children.addAll(drawerChildren);
        adapter.notifyChildRangeInserted(parentPosition, 0, drawerChildren.size());

        adapter.notifyParentChanged(parentPosition);
    }

    @Override
    public void closeDrawer() {
        DrawerLayout drawerLayout = getParentDrawerLayout(rootView);
        if (drawerLayout != null) {
            drawerLayout.closeDrawer(Gravity.START);
        }
    }

    @Override
    public void setDrawerItemSelected(@DrawerParent.Type int type) {
        Stream.of(adapter.getParentList())
                .forEachIndexed((i, drawerParent) -> {
                    if (drawerParent instanceof DrawerParent) {
                        if (((DrawerParent) drawerParent).type == type) {
                            if (!((DrawerParent) drawerParent).isSelected()) {
                                ((DrawerParent) drawerParent).setSelected(true);
                                adapter.notifyParentChanged(i);
                            }
                        } else {
                            if (((DrawerParent) drawerParent).isSelected()) {
                                ((DrawerParent) drawerParent).setSelected(false);
                                adapter.notifyParentChanged(i);
                            }
                        }
                    }
                });
    }

    PlayerViewAdapter playerViewAdapter = new PlayerViewAdapter() {
        @Override
        public void trackInfoChanged(@Nullable Song song) {

            if (song == null) {
                return;
            }

            trackNameView.setText(song.name);
            artistNameView.setText(String.format("%s - %s", song.albumArtistName, song.albumName));
            placeholderText.setText(R.string.app_name);

            requestManager.load(song)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .error(backgroundPlaceholder)
                    .into(backgroundImage);

            requestManager.load(song.getAlbumArtist())
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(PlaceholderProvider.getInstance().getMediumPlaceHolderResId())
                    .into(artistImage);

            if (song.name == null || (song.albumName == null && song.albumArtistName == null)) {
                placeholderText.setVisibility(View.VISIBLE);
                trackNameView.setVisibility(View.GONE);
                artistNameView.setVisibility(View.GONE);
            } else {
                placeholderText.setVisibility(View.GONE);
                trackNameView.setVisibility(View.VISIBLE);
                artistNameView.setVisibility(View.VISIBLE);
            }
        }
    };

    @Nullable
    public static DrawerLayout getParentDrawerLayout(@Nullable View v) {
        if (v == null) return null;

        if (v instanceof DrawerLayout) {
            return (DrawerLayout) v;
        }

        if (v.getParent() instanceof View) {
            return getParentDrawerLayout((View) v.getParent());
        }

        return null;
    }

    @Override
    protected String screenName() {
        return TAG;
    }
}
