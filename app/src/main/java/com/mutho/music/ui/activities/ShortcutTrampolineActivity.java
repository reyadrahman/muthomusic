package com.mutho.music.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.annimon.stream.Optional;
import com.mutho.music.model.Playlist;
import com.mutho.music.playback.MusicService;
import com.mutho.music.playback.constants.ShortcutCommands;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.PlaylistUtils;
import com.mutho.music.utils.ResumingServiceManager;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class ShortcutTrampolineActivity extends AppCompatActivity {

    private static final String TAG = "ShortcutTrampolineActiv";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String action = getIntent().getAction();
        switch (action) {
            case ShortcutCommands.PLAY:
            case ShortcutCommands.SHUFFLE_ALL:
                Intent intent = new Intent(this, MusicService.class);
                intent.setAction(action);
                new ResumingServiceManager(getLifecycle()).startService(this, intent, null);
                finish();
                break;
            case ShortcutCommands.FOLDERS:
                intent = new Intent(this, MainActivity.class);
                intent.setAction(action);
                startActivity(intent);
                finish();
                break;
            case ShortcutCommands.PLAYLIST:
                intent = new Intent(this, MainActivity.class);
                intent.setAction(action);
                Playlist.favoritesPlaylist()
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                playlist -> {
                                    intent.putExtra(PlaylistUtils.ARG_PLAYLIST, playlist);
                                    startActivity(intent);
                                    finish();
                                },
                                error -> LogUtils.logException(TAG, "Error starting activity", error)
                        );
                break;
        }
    }
}
