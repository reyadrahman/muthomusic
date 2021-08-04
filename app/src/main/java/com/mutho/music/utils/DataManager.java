package com.mutho.music.utils;

import android.annotation.SuppressLint;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;
import com.jakewharton.rxrelay2.BehaviorRelay;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.model.Album;
import com.mutho.music.model.AlbumArtist;
import com.mutho.music.model.Genre;
import com.mutho.music.model.InclExclItem;
import com.mutho.music.model.Playlist;
import com.mutho.music.model.Song;
import com.mutho.music.sql.databases.InclExclDbOpenHelper;
import com.mutho.music.sql.sqlbrite.SqlBriteUtils;
import com.squareup.sqlbrite2.BriteDatabase;
import com.squareup.sqlbrite2.SqlBrite;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataManager {

    private static final String TAG = "DataManager";

    private static DataManager instance;

    private Disposable songsSubscription;
    private BehaviorRelay<List<Song>> songsRelay = BehaviorRelay.create();

    private Disposable allSongsSubscription;
    private BehaviorRelay<List<Song>> allSongsRelay = BehaviorRelay.create();

    private Disposable albumsSubscription;
    private BehaviorRelay<List<Album>> albumsRelay = BehaviorRelay.create();

    private Disposable albumArtistsSubscription;
    private BehaviorRelay<List<AlbumArtist>> albumArtistsRelay = BehaviorRelay.create();

    private Disposable genresSubscription;
    private BehaviorRelay<List<Genre>> genresRelay = BehaviorRelay.create();

    private Disposable playlistsSubscription;
    private BehaviorRelay<List<Playlist>> playlistsRelay = BehaviorRelay.create();

    private Disposable favoriteSongsSubscription;
    private BehaviorRelay<List<Song>> favoriteSongsRelay = BehaviorRelay.create();

    private BriteDatabase inclExclDatabase;

    private Disposable inclSubscription;
    private BehaviorRelay<List<InclExclItem>> inclRelay = BehaviorRelay.create();

    private Disposable exclSubscription;
    private BehaviorRelay<List<InclExclItem>> exclRelay = BehaviorRelay.create();

    public static DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    private DataManager() {

        MuthoMusicApplication.getInstance()
                .getContentResolver()
                .registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true, new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        onChange(selfChange, null);
                    }

                    @SuppressLint("CheckResult")
                    @Override
                    public void onChange(boolean selfChange, @Nullable Uri uri) {
                        SqlBriteUtils.createObservableList(MuthoMusicApplication.getInstance(), Playlist::new, Playlist.getQuery())
                                .first(Collections.emptyList())
                                .subscribeOn(AndroidSchedulers.mainThread())
                                .subscribe(
                                        playlists -> playlistsRelay.accept(playlists),
                                        throwable -> LogUtils.logException(TAG, "Failed to update playlist relay", throwable)
                                );
                    }
                });
    }

    public Observable<List<Song>> getAllSongsRelay() {
        if (allSongsSubscription == null || allSongsSubscription.isDisposed()) {
            allSongsSubscription = SqlBriteUtils.createObservableList(MuthoMusicApplication.getInstance(), Song::new, Song.getQuery())
                    .subscribe(
                            allSongsRelay,
                            error -> LogUtils.logException(TAG, "getAllSongsRelay threw error", error)
                    );
        }
        return allSongsRelay
                .subscribeOn(Schedulers.io())
                .map(ArrayList::new);
    }

    /**
     * Returns an {@link Observable}, which emits a List of {@link Song}s retrieved from the MediaStore.
     * <p>
     * Note: The resultant songs are filtered according to the include/exclude lists (see {@link com.mutho.music.sql.databases.InclExclHelper})
     * <p>
     * Note: The resultant songs list does not include podcasts (See {@link Song#isPodcast})
     * <p>
     * Note: This Observable is continuous. It will emit its most recent value upon subscription, and continue to emit
     * whenever the underlying {@code uri}'s data changes.
     * <p>
     * This Observable is backed by an {@link BehaviorRelay} subscribed to a {@link SqlBrite} {@link Observable}.
     * <p>
     * <b>Caution:</b>
     * <p>
     * Although the underlying {@link SqlBrite} {@link Observable} is subscribed on the {@link Schedulers#io()} thread,
     * it seems the {@code Scheduler} will not persist for subsequent {@code subscribe} calls once this {@link Observable} is unsubscribed.
     * Presumably, since subsequent {@code subscribe} calls to this {@link Observable} only re-emit the most recent emission from the
     * source {@link Observable}, the source {@link Observable} is no longer part of the current Observable chain (its job is now
     * just to keep the {@link BehaviorRelay} up to date). So a {@code Scheduler} must be supplied if you wish to ensure the work of this
     * {@link Observable} is not done on the calling thread. For now, the {@link Observable} is automatically subscribed on the
     * {@link Schedulers#io()} {@code scheduler}.
     */
    public Observable<List<Song>> getSongsRelay() {
        if (songsSubscription == null || songsSubscription.isDisposed()) {
            songsSubscription = getAllSongsRelay()
                    .compose(getInclExclTransformer())
                    .map(songs -> Stream.of(songs).filterNot(song -> song.isPodcast).toList())
                    .subscribe(
                            songsRelay,
                            error -> LogUtils.logException(TAG, "getSongsRelay threw error", error)
                    );
        }

        return songsRelay
                .subscribeOn(Schedulers.io())
                .map(ArrayList::new);
    }

    public ObservableTransformer<List<Song>, List<Song>> getInclExclTransformer() {
        return upstream -> Observable.combineLatest(upstream, getInclRelay(), getExclRelay(), (songs, inclItems, exclItems) ->
        {
            List<Song> result = songs;

            // Filter out excluded paths
            if (!exclItems.isEmpty()) {
                result = Stream.of(songs)
                        .filterNot(song -> Stream.of(exclItems)
                                .anyMatch(exclItem -> StringUtils.containsIgnoreCase(song.path, exclItem.path)))
                        .toList();
            }

            // Filter out non-included paths
            if (!inclItems.isEmpty()) {
                result = Stream.of(result)
                        .filter(song -> Stream.of(inclItems)
                                .anyMatch(inclItem -> StringUtils.containsIgnoreCase(song.path, inclItem.path)))
                        .toList();
            }

            return result;
        });
    }

    /**
     * Returns an {@link Observable}, which emits a List of {@link Album}s built from the {@link Song}s returned by
     * {@link #getSongsRelay()}.
     * <p>
     * This Observable is continuous. It will emit its most recent value upon subscription, and continue to emit
     * whenever the underlying {@code uri}'s data changes.
     * <p>
     * This Observable is backed by an {@link BehaviorRelay} subscribed to a {@link SqlBrite} {@link Observable}.
     * <p>
     * <b>Caution:</b>
     * <p>
     * Although the underlying {@link SqlBrite} {@link Observable} is subscribed on the {@link Schedulers#io()} thread,
     * it seems the {@code Scheduler} will not persist for subsequent {@code subscribe} calls once this {@link Observable} is unsubscribed.
     * Presumably, since subsequent {@code subscribe} calls to this {@link Observable} only re-emit the most recent emission from the
     * source {@link Observable}, the source {@link Observable} is no longer part of the current Observable chain (its job is now
     * just to keep the {@link BehaviorRelay} up to date). So a {@code Scheduler} must be supplied if you wish to ensure the work of this
     * {@link Observable} is not done on the calling thread. For now, the {@link Observable} is automatically subscribed on the
     * {@link Schedulers#io()} {@code scheduler}.
     */
    public Observable<List<Album>> getAlbumsRelay() {
        if (albumsSubscription == null || albumsSubscription.isDisposed()) {
            albumsSubscription = getSongsRelay()
                    .flatMap(songs -> Observable.just(Operators.songsToAlbums(songs)))
                    .subscribe(albumsRelay, error -> LogUtils.logException(TAG, "getAlbumsRelay threw error: ", error));
        }
        return albumsRelay.subscribeOn(Schedulers.io()).map(ArrayList::new);
    }

    /**
     * Returns an {@link Observable}, which emits a List of {@link AlbumArtist}s built from the {@link Album}s returned by
     * {@link #getAlbumsRelay()}.
     * <p>
     * This Observable is continuous. It will emit its most recent value upon subscription, and continue to emit
     * whenever the underlying {@code uri}'s data changes.
     * <p>
     * This Observable is backed by an {@link BehaviorRelay} subscribed to a {@link SqlBrite} {@link Observable}.
     * <p>
     * <b>Caution:</b>
     * <p>
     * Although the underlying {@link SqlBrite} {@link Observable} is subscribed on the {@link Schedulers#io()} thread,
     * it seems the {@code Scheduler} will not persist for subsequent {@code subscribe} calls once this {@link Observable} is unsubscribed.
     * Presumably, since subsequent {@code subscribe} calls to this {@link Observable} only re-emit the most recent emission from the
     * source {@link Observable}, the source {@link Observable} is no longer part of the current Observable chain (its job is now
     * just to keep the {@link BehaviorRelay} up to date). So a {@code Scheduler} must be supplied if you wish to ensure the work of this
     * {@link Observable} is not done on the calling thread. For now, the {@link Observable} is automatically subscribed on the
     * {@link Schedulers#io()} {@code scheduler}.
     */
    public Observable<List<AlbumArtist>> getAlbumArtistsRelay() {
        if (albumArtistsSubscription == null || albumArtistsSubscription.isDisposed()) {
            albumArtistsSubscription = getAlbumsRelay()
                    .flatMap(albums -> Observable.just(Operators.albumsToAlbumArtists(albums)))
                    .subscribe(albumArtistsRelay, error -> LogUtils.logException(TAG, "getAlbumArtistsRelay threw error", error));
        }
        return albumArtistsRelay.subscribeOn(Schedulers.io()).map(ArrayList::new);
    }

    /**
     * Returns an {@link Observable}, which emits a List of {@link Genre}s retrieved from the MediaStore.
     * <p>
     * This Observable is continuous. It will emit its most recent value upon subscription, and continue to emit
     * whenever the underlying {@code uri}'s data changes.
     * <p>
     * This Observable is backed by an {@link BehaviorRelay} subscribed to a {@link SqlBrite} {@link Observable}.
     * <p>
     * <b>Caution:</b>
     * <p>
     * Although the underlying {@link SqlBrite} {@link Observable} is subscribed on the {@link Schedulers#io()} thread,
     * it seems the {@code Scheduler} will not persist for subsequent {@code subscribe} calls once this {@link Observable} is unsubscribed.
     * Presumably, since subsequent {@code subscribe} calls to this {@link Observable} only re-emit the most recent emission from the
     * source {@link Observable}, the source {@link Observable} is no longer part of the current Observable chain (its job is now
     * just to keep the {@link BehaviorRelay} up to date). So a {@code Scheduler} must be supplied if you wish to ensure the work of this
     * {@link Observable} is not done on the calling thread. For now, the {@link Observable} is automatically subscribed on the
     * {@link Schedulers#io()} {@code scheduler}.
     */
    public Observable<List<Genre>> getGenresRelay() {
        if (genresSubscription == null || genresSubscription.isDisposed()) {
            genresSubscription = SqlBriteUtils.createObservableList(MuthoMusicApplication.getInstance(), Genre::new, Genre.getQuery())
                    .subscribe(genresRelay, error -> LogUtils.logException(TAG, "getGenresRelay threw error", error));
        }

        return genresRelay.subscribeOn(Schedulers.io()).map(ArrayList::new);
    }

    public void updateGenresRelay(List<Genre> genres) {
        genresRelay.accept(genres);
    }

    /**
     * Returns an {@link Observable}, which emits a List of {@link Playlist}s retrieved from the MediaStore.
     * <p>
     * This Observable is continuous. It will emit its most recent value upon subscription, and continue to emit
     * whenever the underlying {@code uri}'s data changes.
     * <p>
     * This Observable is backed by an {@link BehaviorRelay} subscribed to a {@link SqlBrite} {@link Observable}.
     * <p>
     * <b>Caution:</b>
     * <p>
     * Although the underlying {@link SqlBrite} {@link Observable} is subscribed on the {@link Schedulers#io()} thread,
     * it seems the {@code Scheduler} will not persist for subsequent {@code subscribe} calls once this {@link Observable} is unsubscribed.
     * Presumably, since subsequent {@code subscribe} calls to this {@link Observable} only re-emit the most recent emission from the
     * source {@link Observable}, the source {@link Observable} is no longer part of the current Observable chain (its job is now
     * just to keep the {@link BehaviorRelay} up to date). So a {@code Scheduler} must be supplied if you wish to ensure the work of this
     * {@link Observable} is not done on the calling thread. For now, the {@link Observable} is automatically subscribed on the
     * {@link Schedulers#io()} {@code scheduler}.
     */
    public Observable<List<Playlist>> getPlaylistsRelay() {
        if (playlistsSubscription == null || playlistsSubscription.isDisposed()) {
            playlistsSubscription = SqlBriteUtils.createObservableList(MuthoMusicApplication.getInstance(), Playlist::new, Playlist.getQuery())
                    .subscribe(playlistsRelay, error -> LogUtils.logException(TAG, "getPlaylistRelay threw error", error));
        }
        return playlistsRelay
                .distinctUntilChanged()
                .subscribeOn(Schedulers.io()).map(ArrayList::new);
    }

    public Observable<List<Song>> getFavoriteSongsRelay() {
        if (favoriteSongsSubscription == null || favoriteSongsSubscription.isDisposed()) {
            favoriteSongsSubscription = Playlist.favoritesPlaylist()
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .flatMapObservable(Playlist::getSongsObservable)
                    .switchIfEmpty(Observable.just(Collections.emptyList()))
                    .subscribe(favoriteSongsRelay, error -> LogUtils.logException(TAG, "getFavoriteSongsRelay threw error", error));
        }
        return favoriteSongsRelay.subscribeOn(Schedulers.io()).map(ArrayList::new);
    }

    /**
     * Returns an {@link Observable<List>} from the songs relay, filtered by the passed in predicate.
     */
    public Observable<List<Song>> getSongsObservable(Predicate<Song> predicate) {
        return getSongsRelay()
                .map(songs -> Stream.of(songs)
                        .filter(predicate)
                        .toList());
    }

    /**
     * @return a {@link BriteDatabase} wrapping the greylist SqliteOpenHelper.
     */
    public BriteDatabase getInclExclDatabase() {
        if (inclExclDatabase == null) {
            inclExclDatabase = new SqlBrite.Builder().build()
                    .wrapDatabaseHelper(new InclExclDbOpenHelper(MuthoMusicApplication.getInstance()), Schedulers.io());
        }
        return inclExclDatabase;
    }

    public Observable<List<InclExclItem>> getIncludeItems() {
        return DataManager.getInstance().getInclExclDatabase()
                .createQuery(InclExclDbOpenHelper.TABLE_NAME, "SELECT * FROM " + InclExclDbOpenHelper.TABLE_NAME + " WHERE " + InclExclDbOpenHelper.COLUMN_TYPE + " = " + InclExclItem.Type.INCLUDE)
                .mapToList(InclExclItem::new);
    }

    /**
     * @return a <b>continuous</b> stream of {@link List<InclExclItem>>} of type {@link InclExclItem.Type#INCLUDE} , backed by a behavior relay for caching query results.
     */
    private Observable<List<InclExclItem>> getInclRelay() {
        if (inclSubscription == null || inclSubscription.isDisposed()) {
            inclSubscription = getIncludeItems().subscribe(inclRelay, error -> LogUtils.logException(TAG, "getInclRelay threw error", error));
        }
        return inclRelay.subscribeOn(Schedulers.io()).map(ArrayList::new);
    }

    public Observable<List<InclExclItem>> getExcludeItems() {
        return DataManager.getInstance().getInclExclDatabase()
                .createQuery(InclExclDbOpenHelper.TABLE_NAME, "SELECT * FROM " + InclExclDbOpenHelper.TABLE_NAME + " WHERE " + InclExclDbOpenHelper.COLUMN_TYPE + " = " + InclExclItem.Type.EXCLUDE)
                .mapToList(InclExclItem::new);
    }

    /**
     * @return a <b>continuous</b> stream of {@link List<InclExclItem>>} of type {@link InclExclItem.Type#EXCLUDE}, backed by a behavior relay for caching query results.
     */
    private Observable<List<InclExclItem>> getExclRelay() {
        if (exclSubscription == null || exclSubscription.isDisposed()) {
            exclSubscription = getExcludeItems()
                    .subscribe(exclRelay, error -> LogUtils.logException(TAG, "getExclRelay threw error", error));
        }
        return exclRelay.subscribeOn(Schedulers.io()).map(ArrayList::new);
    }
}