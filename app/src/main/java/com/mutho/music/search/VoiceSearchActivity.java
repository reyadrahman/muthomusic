package com.mutho.music.search;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;
import com.annimon.stream.Stream;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.dagger.module.ActivityModule;
import com.mutho.music.model.Album;
import com.mutho.music.model.AlbumArtist;
import com.mutho.music.playback.MediaManager;
import com.mutho.music.ui.activities.BaseActivity;
import com.mutho.music.ui.activities.MainActivity;
import com.mutho.music.utils.ComparisonUtils;
import com.mutho.music.utils.DataManager;
import com.mutho.music.utils.LogUtils;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import java.util.Collections;
import java.util.Locale;
import javax.inject.Inject;
import kotlin.Unit;

import static com.mutho.music.utils.StringUtils.containsIgnoreCase;

public class VoiceSearchActivity extends BaseActivity {

    private static final String TAG = "VoiceSearchActivity";

    private String filterString;

    private Intent intent;

    private int position = -1;

    @Inject
    MediaManager mediaManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MuthoMusicApplication.getInstance().getAppComponent().plus(new ActivityModule(this)).inject(this);

        intent = getIntent();

        filterString = intent.getStringExtra(SearchManager.QUERY);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        super.onServiceConnected(name, service);
        if (intent != null && intent.getAction() != null && intent.getAction().equals("android.media.action.MEDIA_PLAY_FROM_SEARCH")) {
            searchAndPlaySongs();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }

    private void searchAndPlaySongs() {

        DataManager.getInstance().getAlbumArtistsRelay()
                .first(Collections.emptyList())
                .flatMapObservable(Observable::fromIterable)
                .filter(albumArtist -> albumArtist.name.toLowerCase(Locale.getDefault()).contains(filterString.toLowerCase()))
                .flatMapSingle(AlbumArtist::getSongsSingle)
                .map(songs -> {
                    Collections.sort(songs, (a, b) -> a.getAlbumArtist().compareTo(b.getAlbumArtist()));
                    Collections.sort(songs, (a, b) -> a.getAlbum().compareTo(b.getAlbum()));
                    Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.track, b.track));
                    Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.discNumber, b.discNumber));
                    return songs;
                });

        //Search for album-artists, albums & songs matching our filter. Then, create an Observable emitting List<Song> for each type of result.
        //Then we concat the results, and return the first one which is non-empty. Order is important here, we want album-artist first, if it's
        //available, then albums, then songs.
        Observable.concat(
                //If we have an album artist matching our query, then play the songs by that album artist
                DataManager.getInstance().getAlbumArtistsRelay()
                        .first(Collections.emptyList())
                        .flatMapObservable(Observable::fromIterable)
                        .filter(albumArtist -> albumArtist.name.toLowerCase(Locale.getDefault()).contains(filterString.toLowerCase()))
                        .flatMapSingle(AlbumArtist::getSongsSingle)
                        .map(songs -> {
                            Collections.sort(songs, (a, b) -> a.getAlbumArtist().compareTo(b.getAlbumArtist()));
                            Collections.sort(songs, (a, b) -> a.getAlbum().compareTo(b.getAlbum()));
                            Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.track, b.track));
                            Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.discNumber, b.discNumber));
                            return songs;
                        }),
                //If we have an album matching our query, then play the songs from that album
                DataManager.getInstance().getAlbumsRelay()
                        .first(Collections.emptyList())
                        .flatMapObservable(Observable::fromIterable)
                        .filter(album -> containsIgnoreCase(album.name, filterString)
                                || containsIgnoreCase(album.name, filterString)
                                || (Stream.of(album.artists).anyMatch(artist -> containsIgnoreCase(artist.name, filterString)))
                                || containsIgnoreCase(album.albumArtistName, filterString))
                        .flatMapSingle(Album::getSongsSingle)
                        .map(songs -> {
                            Collections.sort(songs, (a, b) -> a.getAlbum().compareTo(b.getAlbum()));
                            Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.track, b.track));
                            Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.discNumber, b.discNumber));
                            return songs;
                        }),
                //If have a song, play that song, as well as others from the same album.
                DataManager.getInstance().getSongsRelay()
                        .first(Collections.emptyList())
                        .flatMapObservable(Observable::fromIterable)
                        .filter(song -> containsIgnoreCase(song.name, filterString)
                                || containsIgnoreCase(song.albumName, filterString)
                                || containsIgnoreCase(song.artistName, filterString)
                                || containsIgnoreCase(song.albumArtistName, filterString))
                        .flatMapSingle(song -> song.getAlbum().getSongsSingle()
                                .map(songs -> {
                                    Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.track, b.track));
                                    Collections.sort(songs, (a, b) -> ComparisonUtils.compareInt(a.discNumber, b.discNumber));
                                    position = songs.indexOf(song);
                                    return songs;
                                }))
        )
                .filter(songs -> !songs.isEmpty())
                .firstOrError()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(songs -> {
                    if (songs != null) {
                        mediaManager.playAll(songs, position, true, message -> {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                            return Unit.INSTANCE;
                        });
                        startActivity(new Intent(this, MainActivity.class));
                    }
                    finish();
                }, error -> {
                    LogUtils.logException(TAG, "Error attempting to playAll()", error);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
    }

    @Override
    protected String screenName() {
        return TAG;
    }
}