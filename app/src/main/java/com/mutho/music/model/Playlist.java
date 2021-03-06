package com.mutho.music.model;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Pair;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.rx.UnsafeConsumer;
import com.mutho.music.sql.SqlUtils;
import com.mutho.music.sql.providers.PlayCountTable;
import com.mutho.music.sql.sqlbrite.SqlBriteUtils;
import com.mutho.music.utils.ComparisonUtils;
import com.mutho.music.utils.DataManager;
import com.mutho.music.utils.PlaylistUtils;
import com.mutho.music.utils.SettingsManager;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Playlist implements Serializable {

    private static final String TAG = "Playlist";

    public @interface Type {
        int PODCAST = 0;
        int RECENTLY_ADDED = 1;
        int MOST_PLAYED = 2;
        int RECENTLY_PLAYED = 3;
        int FAVORITES = 4;
        int USER_CREATED = 5;
    }

    @Type
    public int type;

    public long id;
    public String name;
    public boolean canEdit = true;
    public boolean canClear = false;
    public boolean canDelete = true;
    public boolean canRename = true;
    public boolean canSort = true;

    // These are the Playlist rows that we will retrieve.
    public static final String[] PROJECTION = new String[] {
            MediaStore.Audio.Playlists._ID,
            MediaStore.Audio.Playlists.NAME
    };

    public static Query getQuery() {
        return new Query.Builder()
                .uri(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI)
                .projection(PROJECTION)
                .selection(null)
                .sort(null)
                .build();
    }

    public Playlist(@Type int type, long id, String name, boolean canEdit, boolean canClear, boolean canDelete, boolean canRename, boolean canSort) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.canEdit = canEdit;
        this.canClear = canClear;
        this.canDelete = canDelete;
        this.canRename = canRename;
        this.canSort = canSort;
    }

    public Playlist(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Playlists._ID));
        name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME));
        type = Type.USER_CREATED;
        canClear = true;

        if (MuthoMusicApplication.getInstance().getString(R.string.fav_title).equals(name)) {
            type = Type.FAVORITES;
            canDelete = false;
            canRename = false;
        }
    }

    public void clear() {
        switch (type) {
            case Playlist.Type.FAVORITES:
                PlaylistUtils.clearFavorites();
                break;
            case Playlist.Type.MOST_PLAYED:
                PlaylistUtils.clearMostPlayed();
                break;
            case Playlist.Type.USER_CREATED:
                PlaylistUtils.clearPlaylist(id);
                break;
        }
    }

    public static Playlist podcastPlaylist() {

        // Check if there are any podcasts
        Query query = new Query.Builder()
                .uri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                .projection(new String[] { MediaStore.Audio.Media.IS_PODCAST })
                .selection(MediaStore.Audio.Media.IS_PODCAST + "=1")
                .build();

        return SqlUtils.createSingleQuery(MuthoMusicApplication.getInstance(), cursor -> new Playlist(
                        Type.PODCAST, PlaylistUtils.PlaylistIds.PODCASTS_PLAYLIST,
                        MuthoMusicApplication.getInstance().getString(R.string.podcasts_title),
                        false, false, false, false, false),
                query);
    }

    public static Playlist recentlyAddedPlaylist = new Playlist(
            Type.RECENTLY_ADDED, PlaylistUtils.PlaylistIds.RECENTLY_ADDED_PLAYLIST,
            MuthoMusicApplication.getInstance().getString(R.string.recentlyadded),
            false,
            false,
            false,
            false,
            false
    );

    public static Playlist mostPlayedPlaylist = new Playlist(
            Type.MOST_PLAYED,
            PlaylistUtils.PlaylistIds.MOST_PLAYED_PLAYLIST,
            MuthoMusicApplication.getInstance().getString(R.string.mostplayed),
            false,
            true,
            false,
            false,
            false
    );

    public static Playlist recentlyPlayedPlaylist = new Playlist(
            Type.RECENTLY_PLAYED,
            PlaylistUtils.PlaylistIds.RECENTLY_PLAYED_PLAYLIST,
            MuthoMusicApplication.getInstance().getString(R.string.suggested_recent_title),
            false,
            false,
            false,
            false,
            false
    );

    @NonNull
    public static Single<Optional<Playlist>> favoritesPlaylist() {
        return DataManager.getInstance().getPlaylistsRelay()
                .first(Collections.emptyList())
                .flatMapObservable(Observable::fromIterable)
                .filter(playlist -> playlist.type == Type.FAVORITES)
                .map(Optional::of)
                .switchIfEmpty(Observable.fromCallable(PlaylistUtils::createFavoritePlaylist))
                .firstOrError();
    }

    public boolean delete() {
        Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id);
        if (uri != null) {
            return MuthoMusicApplication.getInstance().getContentResolver().delete(uri, null, null) > 0;
        }
        return false;
    }

    public void removeSong(@NonNull Song song, @Nullable UnsafeConsumer<Boolean> success) {
        PlaylistUtils.removeFromPlaylist(this, song, success);
    }

    public boolean moveSong(int from, int to) {
        return MediaStore.Audio.Playlists.Members.moveItem(MuthoMusicApplication.getInstance().getContentResolver(), id, from, to);
    }

    public Observable<List<Song>> getSongsObservable() {

        if (id == PlaylistUtils.PlaylistIds.RECENTLY_ADDED_PLAYLIST) {
            int numWeeks = SettingsManager.getInstance().getNumWeeks() * 3600 * 24 * 7;
            return DataManager.getInstance().getSongsObservable(song -> song.dateAdded > (System.currentTimeMillis() / 1000 - numWeeks))
                    .map(songs -> {
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compare(a.albumArtistName, b.albumArtistName));
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(b.year, a.year));
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.track, b.track));
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.discNumber, b.discNumber));
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compare(a.albumName, b.albumName));
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compareLong(b.dateAdded, a.dateAdded));
                        return songs;
                    });
        } else if (id == PlaylistUtils.PlaylistIds.PODCASTS_PLAYLIST) {
            return DataManager.getInstance().getAllSongsRelay()
                    .compose(DataManager.getInstance().getInclExclTransformer())
                    .map(songs -> Stream.of(songs).filter(song -> song.isPodcast).toList())
                    .map(songs -> {
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compareLong(a.playlistSongPlayOrder, b.playlistSongPlayOrder));
                        return songs;
                    });
        } else if (id == PlaylistUtils.PlaylistIds.MOST_PLAYED_PLAYLIST) {
            Query query = new Query.Builder()
                    .uri(PlayCountTable.URI)
                    .projection(new String[] { PlayCountTable.COLUMN_ID, PlayCountTable.COLUMN_PLAY_COUNT })
                    .sort(PlayCountTable.COLUMN_PLAY_COUNT + " DESC")
                    .build();

            return SqlBriteUtils.createObservableList(MuthoMusicApplication.getInstance(), cursor ->
                    new Pair<>(
                            cursor.getInt(cursor.getColumnIndexOrThrow(PlayCountTable.COLUMN_ID)),
                            cursor.getInt(cursor.getColumnIndexOrThrow(PlayCountTable.COLUMN_PLAY_COUNT))
                    ), query)
                    .flatMap(pairs -> DataManager.getInstance().getSongsObservable(song ->
                            Stream.of(pairs)
                                    .filter(pair -> {
                                        song.playCount = pair.second;
                                        return pair.first == song.id && pair.second >= 2;
                                    })
                                    .findFirst()
                                    .orElse(null) != null)
                            .map(songs -> {
                                Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(b.playCount, a.playCount));
                                return songs;
                            }));
        } else if (id == PlaylistUtils.PlaylistIds.RECENTLY_PLAYED_PLAYLIST) {
            Query query = new Query.Builder()
                    .uri(PlayCountTable.URI)
                    .projection(new String[] { PlayCountTable.COLUMN_ID, PlayCountTable.COLUMN_TIME_PLAYED })
                    .sort(PlayCountTable.COLUMN_TIME_PLAYED + " DESC")
                    .build();

            return SqlBriteUtils.createObservableList(MuthoMusicApplication.getInstance(), cursor ->
                    new Pair<>(
                            cursor.getLong(cursor.getColumnIndexOrThrow(PlayCountTable.COLUMN_ID)),
                            cursor.getLong(cursor.getColumnIndexOrThrow(PlayCountTable.COLUMN_TIME_PLAYED))
                    ), query)
                    .flatMap(pairs -> DataManager.getInstance().getSongsObservable(song ->
                            Stream.of(pairs)
                                    .filter(pair -> {
                                        song.lastPlayed = pair.second;
                                        return pair.first == song.id;
                                    })
                                    .findFirst()
                                    .orElse(null) != null)
                            .map(songs -> {
                                Collections.sort(songs, (a, b) -> ComparisonUtils.compareLong(b.lastPlayed, a.lastPlayed));
                                return songs;
                            }));
        } else {
            Query query = Song.getQuery();
            query.uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
            List<String> projection = new ArrayList<>(Arrays.asList(Song.getProjection()));
            projection.add(MediaStore.Audio.Playlists.Members._ID);
            projection.add(MediaStore.Audio.Playlists.Members.AUDIO_ID);
            projection.add(MediaStore.Audio.Playlists.Members.PLAY_ORDER);
            query.projection = projection.toArray(new String[projection.size()]);

            return SqlBriteUtils.createObservableList(MuthoMusicApplication.getInstance(), Playlist::createSongFromPlaylistCursor, query)
                    .map(songs -> {
                        Collections.sort(songs, (a, b) -> ComparisonUtils.compareLong(a.playlistSongPlayOrder, b.playlistSongPlayOrder));
                        return songs;
                    });
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Playlist playlist = (Playlist) o;

        if (id != playlist.id) return false;
        return name != null ? name.equals(playlist.name) : playlist.name == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (id ^ (id >>> 32));
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Playlist{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }

    private static Song createSongFromPlaylistCursor(Cursor cursor) {
        Song song = new Song(cursor);
        song.id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.AUDIO_ID));
        song.playlistSongId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members._ID));
        song.playlistSongPlayOrder = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.Members.PLAY_ORDER));
        return song;
    }
}
