package com.mutho.music;

import android.Manifest;
import android.app.Application;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.core.CrashlyticsCore;
import com.google.android.libraries.cast.companionlibrary.cast.CastConfiguration;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mutho.music.constants.Config;
import com.mutho.music.dagger.component.AppComponent;
import com.mutho.music.dagger.component.DaggerAppComponent;
import com.mutho.music.dagger.module.AppModule;
import com.mutho.music.model.Genre;
import com.mutho.music.model.Query;
import com.mutho.music.model.UserSelectedArtwork;
import com.mutho.music.sql.SqlUtils;
import com.mutho.music.sql.databases.CustomArtworkTable;
import com.mutho.music.sql.providers.PlayCountTable;
import com.mutho.music.sql.sqlbrite.SqlBriteUtils;
import com.mutho.music.utils.DataManager;
import com.mutho.music.utils.InputMethodManagerLeaks;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.SettingsManager;
import com.mutho.music.utils.StringUtils;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import io.fabric.sdk.android.Fabric;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.TagOptionSingleton;

public class MuthoMusicApplication extends Application {

    private static final String TAG = "MuthoMusicApplication";

    private static MuthoMusicApplication instance;

    public static synchronized MuthoMusicApplication getInstance() {
        return instance;
    }

    public static final double VOLUME_INCREMENT = 0.05;

    private RefWatcher refWatcher;

    public HashMap<String, UserSelectedArtwork> userSelectedArtwork = new HashMap<>();

    private static Logger jaudioTaggerLogger1 = Logger.getLogger("org.jaudiotagger.audio");
    private static Logger jaudioTaggerLogger2 = Logger.getLogger("org.jaudiotagger");

    private AppComponent appComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }

        instance = this;

        appComponent = initDagger(this);

        refWatcher = LeakCanary.install(this);
        // workaround to fix InputMethodManager leak as suggested by LeakCanary lib
        InputMethodManagerLeaks.fixFocusedViewLeak(this);

        //Crashlytics
        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();

        Fabric.with(this,
                new Crashlytics.Builder()
                        .core(crashlyticsCore)
                        .answers(new Answers())
                        .build());

        // Firebase
        FirebaseApp.initializeApp(this);
        FirebaseAnalytics.getInstance(this);

        VideoCastManager.initialize(this,
                new CastConfiguration.Builder(Config.CHROMECAST_APP_ID)
                        .enableLockScreen()
                        .enableNotification()
                        .build()
        );

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // we cannot call setDefaultValues for multiple fragment based XML preference
        // files with readAgain flag set to false, so always check KEY_HAS_SET_DEFAULT_VALUES
        if (!prefs.getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
            PreferenceManager.setDefaultValues(this, R.xml.settings_headers, true);
            PreferenceManager.setDefaultValues(this, R.xml.settings_artwork, true);
            PreferenceManager.setDefaultValues(this, R.xml.settings_blacklist, true);
            PreferenceManager.setDefaultValues(this, R.xml.settings_display, true);
            PreferenceManager.setDefaultValues(this, R.xml.settings_headset, true);
            PreferenceManager.setDefaultValues(this, R.xml.settings_scrobbling, true);
            PreferenceManager.setDefaultValues(this, R.xml.settings_themes, true);
        }

        // Turn off logging for jaudiotagger.
        jaudioTaggerLogger1.setLevel(Level.OFF);
        jaudioTaggerLogger2.setLevel(Level.OFF);

        TagOptionSingleton.getInstance().setPadNumbers(true);

        SettingsManager.getInstance().incrementLaunchCount();

        Completable.fromAction(() -> {
            Query query = new Query.Builder()
                    .uri(CustomArtworkTable.URI)
                    .projection(new String[] { CustomArtworkTable.COLUMN_ID, CustomArtworkTable.COLUMN_KEY, CustomArtworkTable.COLUMN_TYPE, CustomArtworkTable.COLUMN_PATH })
                    .build();

            SqlUtils.createActionableQuery(MuthoMusicApplication.this, cursor ->
                            userSelectedArtwork.put(
                                    cursor.getString(cursor.getColumnIndexOrThrow(CustomArtworkTable.COLUMN_KEY)),
                                    new UserSelectedArtwork(
                                            cursor.getInt(cursor.getColumnIndexOrThrow(CustomArtworkTable.COLUMN_TYPE)),
                                            cursor.getString(cursor.getColumnIndexOrThrow(CustomArtworkTable.COLUMN_PATH)))
                            ),
                    query);
        })
                .doOnError(throwable -> LogUtils.logException(TAG, "Error updating user selected artwork", throwable))
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public RefWatcher getRefWatcher() {
        return this.refWatcher;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        Glide.get(this).clearMemory();
    }

    public AppComponent getAppComponent() {
        return appComponent;
    }

    protected AppComponent initDagger(MuthoMusicApplication application) {
        return DaggerAppComponent.builder()
                .appModule(new AppModule(application))
                .build();
    }

    public static String getVersion() {
        try {
            return instance.getPackageManager().getPackageInfo(instance.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException | NullPointerException ignored) {

        }
        return "unknown";
    }

    public static File getDiskCacheDir(String uniqueName) {
        try {
            // Check if media is mounted or storage is built-in, if so, try and use external cache dir
            // otherwise use internal cache dir
            String cachePath = null;
            File externalCacheDir = getInstance().getExternalCacheDir();
            if ((Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment.isExternalStorageRemovable()) && externalCacheDir != null) {
                cachePath = externalCacheDir.getPath();
            } else if (getInstance().getCacheDir() != null) {
                cachePath = getInstance().getCacheDir().getPath();
            }
            if (cachePath != null) {
                return new File(cachePath + File.separator + uniqueName);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "getDiskCacheDir() failed. " + e.toString());
        }
        return null;
    }

    /**
     * Check items in the Most Played playlist and ensure their ids exist in the MediaStore.
     * <p>
     * If they don't, remove them from the playlist.
     */
    @NonNull
    private Completable cleanMostPlayedPlaylist() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return Completable.complete();
        }

        return Completable.fromAction(() -> {
            List<Integer> playCountIds = new ArrayList<>();

            Query query = new Query.Builder()
                    .uri(PlayCountTable.URI)
                    .projection(new String[] { PlayCountTable.COLUMN_ID })
                    .build();

            SqlUtils.createActionableQuery(this, cursor ->
                    playCountIds.add(cursor.getInt(cursor.getColumnIndex(PlayCountTable.COLUMN_ID))), query);

            List<Integer> songIds = new ArrayList<>();

            query = new Query.Builder()
                    .uri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
                    .projection(new String[] { MediaStore.Audio.Media._ID })
                    .build();

            SqlUtils.createActionableQuery(this, cursor ->
                    songIds.add(cursor.getInt(cursor.getColumnIndex(PlayCountTable.COLUMN_ID))), query);

            StringBuilder selection = new StringBuilder(PlayCountTable.COLUMN_ID + " IN (");

            selection.append(TextUtils.join(",", Stream.of(playCountIds)
                    .filter(playCountId -> !songIds.contains(playCountId))
                    .toList()));

            selection.append(")");

            try {
                getContentResolver().delete(PlayCountTable.URI, selection.toString(), null);
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    @NonNull
    private Completable cleanGenres() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return Completable.complete();
        }

        // This observable emits a genre every 50ms. We then make a query against the genre database to populate the song count.
        // If the count is zero, then the genre can be deleted.
        // The reason for the delay is, on some slower devices, if the user has tons of genres, a ton of cursors get created.
        // If the maximum number of cursors is created (based on memory/processor speed or god knows what else), then the device
        // will start throwing CursorWindow exceptions, and the queries will slow down massively. This ends up making all queries slow.
        // This task isn't time critical, so we can afford to let it just casually do its job.
        return SqlBriteUtils.createSingleList(MuthoMusicApplication.getInstance(), Genre::new, Genre.getQuery())
                .flatMapObservable(Observable::fromIterable)
                .concatMap(genre -> Observable.just(genre).delay(50, TimeUnit.MILLISECONDS))
                .flatMapSingle(genre -> genre.getSongsObservable()
                        .doOnSuccess(songs -> {
                            if (songs.isEmpty()) {
                                try {
                                    getContentResolver().delete(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, MediaStore.Audio.Genres._ID + " == " + genre.id, null);
                                } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                                    //Don't care if we couldn't delete this uri.
                                }
                            }
                        })
                ).flatMapCompletable(songs -> Completable.complete());
    }

    @NonNull
    private Completable repairMediaStoreYearFromTags() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return Completable.complete();
        }

        return DataManager.getInstance()
                .getSongsObservable(value -> value.year < 1)
                .first(Collections.emptyList())
                .flatMapObservable(Observable::fromIterable)
                .concatMap(song -> Observable.just(song).delay(50, TimeUnit.MILLISECONDS))
                .flatMap(song -> {
                            if (!TextUtils.isEmpty(song.path)) {
                                File file = new File(song.path);
                                // Don't bother checking files > 100mb, uses too much memory.
                                if (file.exists() && file.length() < 100 * 1024 * 1024) {
                                    try {
                                        AudioFile audioFile = AudioFileIO.read(file);
                                        Tag tag = audioFile.getTag();
                                        if (tag != null) {
                                            String year = tag.getFirst(FieldKey.YEAR);
                                            int yearInt = StringUtils.parseInt(year);
                                            if (yearInt > 0) {
                                                song.year = yearInt;
                                                ContentValues contentValues = new ContentValues();
                                                contentValues.put(MediaStore.Audio.Media.YEAR, yearInt);

                                                return Observable.just(ContentProviderOperation
                                                        .newUpdate(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id))
                                                        .withValues(contentValues)
                                                        .build());
                                            }
                                        }
                                    } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException | OutOfMemoryError e) {
                                        LogUtils.logException(TAG, "Failed to repair media store year", e);
                                    }
                                }
                            }
                            return Observable.empty();
                        }

                ).toList()
                .doOnSuccess(contentProviderOperations -> {
                    getContentResolver().applyBatch(MediaStore.AUTHORITY, new ArrayList<>(contentProviderOperations));
                })
                .flatMapCompletable(songs -> Completable.complete());
    }

    private void enableStrictMode() {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyFlashScreen()
                .build());
    }
}
