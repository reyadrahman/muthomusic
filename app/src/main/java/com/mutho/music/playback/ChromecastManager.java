package com.mutho.music.playback;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.common.images.WebImage;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.CastException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.NoConnectionException;
import com.google.android.libraries.cast.companionlibrary.cast.exceptions.TransientNetworkDisconnectionException;
import com.mutho.music.glide.utils.GlideUtils;
import com.mutho.music.http.HttpServer;
import com.mutho.music.model.Song;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.PlaceholderProvider;
import com.mutho.music.utils.ShuttleUtils;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import java.io.ByteArrayOutputStream;

class ChromecastManager {

    private static final String TAG = "ChromecastManager";

    private Context context;

    private PlaybackManager playbackManager;

    VideoCastManager castManager;

    private VideoCastConsumerImpl castConsumer;

    private int castMediaStatus = -1;

    private CompositeDisposable disposables = new CompositeDisposable();

    private QueueManager queueManager;

    ChromecastManager(Context context, QueueManager queueManager, PlaybackManager playbackManager) {
        this.context = context;
        this.queueManager = queueManager;
        this.playbackManager = playbackManager;
    }

    void init() {
        castManager = VideoCastManager.getInstance();
        setupCastListener();
        castManager.addVideoCastConsumer(castConsumer);

        if (castManager != null && castManager.isConnected()) {
            updatePlaybackLocation(PlaybackManager.PlaybackLocation.REMOTE);
        } else {
            updatePlaybackLocation(PlaybackManager.PlaybackLocation.LOCAL);
        }
    }

    void release() {
        disposables.clear();

        if (castManager != null) {
            try {
                castManager.stop();
            } catch (CastException | TransientNetworkDisconnectionException | NoConnectionException e) {
                LogUtils.logException(TAG, "Failed to stop cast manager", e);
            }
            castManager.removeVideoCastConsumer(castConsumer);
        }
    }

    private void updatePlaybackLocation(@PlaybackManager.PlaybackLocation int location) {

        // If the location has changed and it's no longer ChromeCast
        if (location == PlaybackManager.PlaybackLocation.LOCAL && location != playbackManager.getPlaybackLocation()) {
            try {
                if (castManager != null && castManager.isConnected()) {
                    playbackManager.seekTo(castManager.getCurrentMediaPosition());
                    castManager.stop();
                }
            } catch (CastException | NoConnectionException | TransientNetworkDisconnectionException | IllegalStateException e) {
                Log.e(TAG, "updatePlaybackLocation error: " + e);
            }
        }

        playbackManager.setPlaybackLocation(location);
    }

    private void loadRemoteMedia(@NonNull Song song, @NonNull MediaInfo selectedMedia, int position, boolean autoPlay, @Nullable Bitmap bitmap, Drawable errorDrawable) {
        disposables.add(Completable.fromAction(() -> {
                    HttpServer.getInstance().serveAudio(song.path);

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();

                    if (bitmap == null) {
                        GlideUtils.drawableToBitmap(errorDrawable).compress(Bitmap.CompressFormat.JPEG, 80, stream);
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                    }

                    HttpServer.getInstance().serveImage(stream.toByteArray());
                })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            try {
                                castManager.loadMedia(selectedMedia, autoPlay, position);
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to load media. " + e.toString());
                            }
                        }, throwable -> LogUtils.logException(TAG, "Error loading remote media", throwable))
        );
    }

    void prepareChromeCastLoad(@NonNull Song song, int position, boolean autoPlay) {

        MediaMetadata metadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        metadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, song.albumArtistName);
        metadata.putString(MediaMetadata.KEY_ALBUM_TITLE, song.albumName);
        metadata.putString(MediaMetadata.KEY_TITLE, song.name);
        metadata.addImage(new WebImage(Uri.parse("http://" + ShuttleUtils.getIpAddr() + ":5000" + "/image/" + song.id)));

        MediaInfo selectedMedia = new MediaInfo.Builder("http://" + ShuttleUtils.getIpAddr() + ":5000" + "/audio/" + song.id)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("audio/*")
                .setMetadata(metadata)
                .build();

        disposables.add(Completable.defer(() -> Completable.fromAction(() ->
                Glide.with(context)
                        .load(song)
                        .asBitmap()
                        .override(1024, 1024)
                        .placeholder(PlaceholderProvider.getInstance().getPlaceHolderDrawable(song.name, true))
                        .into(new SimpleTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap> glideAnimation) {
                                loadRemoteMedia(song, selectedMedia, position, autoPlay, resource, null);
                            }

                            @Override
                            public void onLoadFailed(Exception e, Drawable errorDrawable) {
                                super.onLoadFailed(e, errorDrawable);
                                loadRemoteMedia(song, selectedMedia, position, autoPlay, null, errorDrawable);
                            }
                        })))
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe());
    }

    private void setupCastListener() {

        castConsumer = new VideoCastConsumerImpl() {

            @Override
            public void onApplicationConnected(ApplicationMetadata appMetadata, String sessionId, boolean wasLaunched) {

                Log.d(TAG, "onApplicationLaunched()");

                HttpServer.getInstance().start();

                boolean wasPlaying = playbackManager.getIsSupposedToBePlaying();

                // If music is playing on the phone, pause it
                if (playbackManager.getPlaybackLocation() == PlaybackManager.PlaybackLocation.LOCAL && wasPlaying) {
                    playbackManager.pause();
                }

                // Try to play from the same position, but on the ChromeCast
                if (queueManager.getCurrentSong() != null) {
                    prepareChromeCastLoad(queueManager.getCurrentSong(), (int) playbackManager.getSeekPosition(), wasPlaying);
                    if (wasPlaying) {
                        playbackManager.setPlaybackState(PlaybackManager.PlaybackState.PLAYING);
                    } else {
                        playbackManager.setPlaybackState(PlaybackManager.PlaybackState.PAUSED);
                    }
                }

                updatePlaybackLocation(PlaybackManager.PlaybackLocation.REMOTE);
            }

            @Override
            public void onApplicationDisconnected(int errorCode) {
                Log.d(TAG, "onApplicationDisconnected() is reached with errorCode: " + errorCode);
                playbackManager.setIsSupposedToBePlaying(false, true);
                playbackManager.setPlaybackState(PlaybackManager.PlaybackState.STOPPED);
                updatePlaybackLocation(PlaybackManager.PlaybackLocation.LOCAL);

                HttpServer.getInstance().stop();
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "onDisconnected() is reached");
                playbackManager.setIsSupposedToBePlaying(false, true);
                playbackManager.setPlaybackState(PlaybackManager.PlaybackState.STOPPED);
                updatePlaybackLocation(PlaybackManager.PlaybackLocation.LOCAL);

                HttpServer.getInstance().stop();
            }

            @Override
            public void onRemoteMediaPlayerStatusUpdated() {
                // Only send a track finished message if the state has changed..
                if (castManager.getPlaybackStatus() != castMediaStatus) {
                    if (castManager.getPlaybackStatus() == MediaStatus.PLAYER_STATE_IDLE && castManager.getIdleReason() == MediaStatus.IDLE_REASON_FINISHED) {
                        playbackManager.notifyTrackEnded();
                    }
                }
                castMediaStatus = castManager.getPlaybackStatus();
            }
        };
    }
}
