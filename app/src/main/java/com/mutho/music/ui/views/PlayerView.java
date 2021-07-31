package com.mutho.music.ui.views;

import android.support.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import com.mutho.music.model.Song;
import com.mutho.music.playback.QueueManager;
import com.mutho.music.tagger.TaggerDialog;

public interface PlayerView {

    void setSeekProgress(int progress);

    void currentTimeVisibilityChanged(boolean visible);

    void currentTimeChanged(long seconds);

    void totalTimeChanged(long seconds);

    void queueChanged(int queuePosition, int queueLength);

    void playbackChanged(boolean isPlaying);

    void shuffleChanged(@QueueManager.ShuffleMode int shuffleMode);

    void repeatChanged(@QueueManager.RepeatMode int repeatMode);

    void favoriteChanged(boolean isFavorite);

    void trackInfoChanged(@Nullable Song song);

    void showToast(String message, int duration);

    void showLyricsDialog(MaterialDialog dialog);

    void showTaggerDialog(TaggerDialog taggerDialog);

    void showSongInfoDialog(MaterialDialog dialog);
}