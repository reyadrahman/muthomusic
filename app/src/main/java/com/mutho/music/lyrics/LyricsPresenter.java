package com.mutho.music.lyrics;

import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.cantrowitz.rxbroadcast.RxBroadcast;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.model.Query;
import com.mutho.music.model.Song;
import com.mutho.music.playback.MediaManager;
import com.mutho.music.playback.constants.InternalIntents;
import com.mutho.music.sql.SqlUtils;
import com.mutho.music.ui.presenters.Presenter;
import com.mutho.music.utils.LogUtils;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Observable;
import java.io.File;
import java.io.IOException;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;

class LyricsPresenter extends Presenter<LyricsView> {

    private static final String TAG = "LyricsPresenter";
    private MediaManager mediaManager;

    public LyricsPresenter(MediaManager mediaManager) {
        this.mediaManager = mediaManager;
    }

    @Override
    public void bindView(@NonNull LyricsView view) {
        super.bindView(view);

        updateLyrics();

        addDisposable(RxBroadcast.fromBroadcast(MuthoMusicApplication.getInstance(), new IntentFilter(InternalIntents.META_CHANGED))
                .toFlowable(BackpressureStrategy.LATEST)
                .subscribe(intent -> updateLyrics(), error -> LogUtils.logException(TAG, "Error receiving meta changed", error)));
    }

    void downloadOrLaunchQuickLyric() {
        LyricsView lyricsView = getView();
        if (lyricsView != null) {
            if (QuickLyricUtils.isQLInstalled()) {
                Song song = mediaManager.getSong();
                if (song != null) {
                    lyricsView.launchQuickLyric(song);
                }
            } else {
                lyricsView.downloadQuickLyric();
            }
        }
    }

    void showQuickLyricInfoDialog() {
        LyricsView lyricsView = getView();
        if (lyricsView != null) {
            lyricsView.showQuickLyricInfoDialog();
        }
    }

    private void updateLyrics() {

        addDisposable(Observable.fromCallable(() -> {

            String lyrics = "";
            String path = mediaManager.getFilePath();

            if (TextUtils.isEmpty(path)) {
                return lyrics;
            }

            if (path.startsWith("content://")) {
                Query query = new Query.Builder()
                        .uri(Uri.parse(path))
                        .projection(new String[] { MediaStore.Audio.Media.DATA })
                        .build();

                Cursor cursor = SqlUtils.createQuery(MuthoMusicApplication.getInstance(), query);
                if (cursor != null) {
                    try {
                        int colIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                        if (cursor.moveToFirst()) {
                            path = cursor.getString(colIndex);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }

            File file = new File(path);
            if (file.exists()) {
                try {
                    AudioFile audioFile = AudioFileIO.read(file);
                    if (audioFile != null) {
                        Tag tag = audioFile.getTag();
                        if (tag != null) {
                            String tagLyrics = tag.getFirst(FieldKey.LYRICS);
                            if (tagLyrics != null && tagLyrics.length() != 0) {
                                lyrics = tagLyrics.replace("\r", "\n");
                            }
                        }
                    }
                } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException | UnsupportedOperationException ignored) {
                }
            }

            return lyrics;
        }).subscribe(lyrics -> {
            LyricsView lyricsView = getView();
            if (lyricsView != null) {
                lyricsView.updateLyrics(lyrics);
                lyricsView.showNoLyricsView(TextUtils.isEmpty(lyrics));
                lyricsView.showQuickLyricInfoButton(!QuickLyricUtils.isQLInstalled());
            }
        }, error -> LogUtils.logException(TAG, "Error getting lyrics", error)));
    }
}