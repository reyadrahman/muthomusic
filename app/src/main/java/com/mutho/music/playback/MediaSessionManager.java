package com.mutho.music.playback;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.RemoteControlClient;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import com.annimon.stream.Stream;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.cantrowitz.rxbroadcast.RxBroadcast;
import com.mutho.music.R;
import com.mutho.music.MuthoMusicApplication;
import com.mutho.music.androidauto.CarHelper;
import com.mutho.music.androidauto.MediaIdHelper;
import com.mutho.music.model.Song;
import com.mutho.music.playback.constants.InternalIntents;
import com.mutho.music.ui.queue.QueueItem;
import com.mutho.music.ui.queue.QueueItemKt;
import com.mutho.music.utils.LogUtils;
import com.mutho.music.utils.MediaButtonIntentReceiver;
import com.mutho.music.utils.SettingsManager;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import java.util.List;
import kotlin.Unit;

public class MediaSessionManager {

    private static final String TAG = "MediaSessionManager";

    private Context context;

    private MediaSessionCompat mediaSession;

    private QueueManager queueManager;

    private PlaybackManager playbackManager;

    private CompositeDisposable disposables = new CompositeDisposable();

    private MediaIdHelper mediaIdHelper = new MediaIdHelper();

    private static String SHUFFLE_ACTION = "ACTION_SHUFFLE";

    MediaSessionManager(Context context, QueueManager queueManager, PlaybackManager playbackManager, MusicService.Callbacks musicServiceCallbacks) {
        this.context = context.getApplicationContext();
        this.queueManager = queueManager;
        this.playbackManager = playbackManager;

        ComponentName mediaButtonReceiverComponent = new ComponentName(context.getPackageName(), MediaButtonIntentReceiver.class.getName());
        mediaSession = new MediaSessionCompat(context, "Shuttle", mediaButtonReceiverComponent, null);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPause() {
                playbackManager.pause();
                playbackManager.setPausedByTransientLossOfFocus(false);
            }

            @Override
            public void onPlay() {
                playbackManager.play();
            }

            @Override
            public void onSeekTo(long pos) {
                playbackManager.seekTo(pos);
            }

            @Override
            public void onSkipToNext() {
                playbackManager.next(true);
            }

            @Override
            public void onSkipToPrevious() {
                playbackManager.previous();
            }

            @Override
            public void onSkipToQueueItem(long id) {
                List<QueueItem> queueItems = queueManager.getCurrentPlaylist();

                QueueItem queueItem = Stream.of(queueItems)
                        .filter(aQueueItem -> (long) aQueueItem.hashCode() == id)
                        .findFirst()
                        .orElse(null);

                if (queueItem != null) {
                    playbackManager.setQueuePosition(queueItems.indexOf(queueItem));
                }
            }

            @Override
            public void onStop() {
                playbackManager.pause();
                playbackManager.setPausedByTransientLossOfFocus(false);
                musicServiceCallbacks.releaseServiceUiAndStop();
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                Log.e("MediaButtonReceiver", "OnMediaButtonEvent called");
                MediaButtonIntentReceiver.MediaButtonReceiverHelper.onReceive(context, mediaButtonEvent);
                return true;
            }

            @Override
            public void onPlayFromMediaId(String mediaId, Bundle extras) {
                mediaIdHelper.getSongListForMediaId(mediaId, (songs, position) ->
                {
                    playbackManager.open((List<Song>) songs, position);
                    playbackManager.play();
                    return Unit.INSTANCE;
                });
            }

            @SuppressLint("CheckResult")
            @Override
            public void onPlayFromSearch(String query, Bundle extras) {
                if (TextUtils.isEmpty(query)) {
                    playbackManager.play();
                } else {
                    mediaIdHelper.handlePlayFromSearch(query, extras)
                            .subscribe(
                                    pair -> {
                                        if (!pair.getFirst().isEmpty()) {
                                            playbackManager.open(pair.getFirst(), pair.getSecond());
                                            playbackManager.play();
                                        } else {
                                            playbackManager.pause();
                                        }
                                    },
                                    error -> LogUtils.logException(TAG, "Failed to gather songs from search. Query: " + query, error)
                            );
                }
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                if (action.equals(SHUFFLE_ACTION)) {
                    queueManager.setShuffleMode(queueManager.shuffleMode == QueueManager.ShuffleMode.ON ? QueueManager.ShuffleMode.OFF : QueueManager.ShuffleMode.ON);
                }
                updateMediaSession(action);
            }
        });

        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);

        //For some reason, MediaSessionCompat doesn't seem to pass all of the available 'actions' on as
        //transport control flags for the RCC, so we do that manually
        RemoteControlClient remoteControlClient = (RemoteControlClient) mediaSession.getRemoteControlClient();
        if (remoteControlClient != null) {
            remoteControlClient.setTransportControlFlags(
                    RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                            | RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                            | RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
                            | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                            | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
                            | RemoteControlClient.FLAG_KEY_MEDIA_STOP);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(InternalIntents.QUEUE_CHANGED);
        intentFilter.addAction(InternalIntents.META_CHANGED);
        intentFilter.addAction(InternalIntents.PLAY_STATE_CHANGED);
        intentFilter.addAction(InternalIntents.POSITION_CHANGED);
        disposables.add(RxBroadcast.fromBroadcast(context, intentFilter).subscribe(intent -> {
            String action = intent.getAction();
            if (action != null) {
                updateMediaSession(intent.getAction());
            }
        }));
    }

    private void updateMediaSession(final String action) {

        int playState = playbackManager.getIsSupposedToBePlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

        long playbackActions = getMediaSessionActions();

        QueueItem currentQueueItem = queueManager.getCurrentQueueItem();

        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setActions(playbackActions);

        switch (queueManager.shuffleMode) {
            case QueueManager.ShuffleMode.OFF:
                builder.addCustomAction(
                        new PlaybackStateCompat.CustomAction.Builder(SHUFFLE_ACTION, MuthoMusicApplication.getInstance().getString(R.string.btn_shuffle_on), R.drawable.ic_shuffle_off_circled).build());
                break;
            case QueueManager.ShuffleMode.ON:
                builder.addCustomAction(
                        new PlaybackStateCompat.CustomAction.Builder(SHUFFLE_ACTION, MuthoMusicApplication.getInstance().getString(R.string.btn_shuffle_off), R.drawable.ic_shuffle_on_circled).build());
                break;
        }

        builder.setState(playState, playbackManager.getSeekPosition(), 1.0f);

        if (currentQueueItem != null) {
            builder.setActiveQueueItemId((long) currentQueueItem.hashCode());
        }

        PlaybackStateCompat playbackState = builder.build();

        if (action.equals(InternalIntents.PLAY_STATE_CHANGED) || action.equals(InternalIntents.POSITION_CHANGED) || action.equals(SHUFFLE_ACTION)) {
            mediaSession.setPlaybackState(playbackState);
        } else if (action.equals(InternalIntents.META_CHANGED) || action.equals(InternalIntents.QUEUE_CHANGED)) {

            if (currentQueueItem != null) {
                MediaMetadataCompat.Builder metaData = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(queueManager.getCurrentSong().id))
                        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentQueueItem.getSong().artistName)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, currentQueueItem.getSong().albumArtistName)
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentQueueItem.getSong().albumName)
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentQueueItem.getSong().name)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentQueueItem.getSong().duration)
                        .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, (long) (queueManager.queuePosition + 1))
                        //Getting the genre is expensive.. let's not bother for now.
                        //.putString(MediaMetadataCompat.METADATA_KEY_GENRE, getGenreName())
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null)
                        .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, (long) (queueManager.getCurrentPlaylist().size()));

                // If we're in car mode, don't wait for the artwork to load before setting session metadata.
                if (CarHelper.isCarUiMode(context)) {
                    mediaSession.setMetadata(metaData.build());
                }

                mediaSession.setPlaybackState(playbackState);

                mediaSession.setQueue(QueueItemKt.toMediaSessionQueueItems(queueManager.getCurrentPlaylist()));
                mediaSession.setQueueTitle(context.getString(R.string.menu_queue));

                if (SettingsManager.getInstance().showLockscreenArtwork() || CarHelper.isCarUiMode(context)) {

                    disposables.add(
                            Completable.defer(() -> Completable.fromAction(() ->
                                    Glide.with(context)
                                            .load(currentQueueItem.getSong().getAlbum())
                                            .asBitmap()
                                            .override(1024, 1024)
                                            .into(new SimpleTarget<Bitmap>() {
                                                @Override
                                                public void onResourceReady(Bitmap bitmap, GlideAnimation<? super Bitmap> glideAnimation) {
                                                    if (bitmap != null) {
                                                        metaData.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
                                                    }
                                                    try {
                                                        mediaSession.setMetadata(metaData.build());
                                                    } catch (NullPointerException e) {
                                                        metaData.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null);
                                                        mediaSession.setMetadata(metaData.build());
                                                    }
                                                }

                                                @Override
                                                public void onLoadFailed(Exception e, Drawable errorDrawable) {
                                                    super.onLoadFailed(e, errorDrawable);
                                                    mediaSession.setMetadata(metaData.build());
                                                }
                                            })
                            ))
                                    .subscribeOn(AndroidSchedulers.mainThread())
                                    .subscribe()

                    );
                } else {
                    mediaSession.setMetadata(metaData.build());
                }
            }
        }
    }

    private long getMediaSessionActions() {
        return PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
    }

    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    public void setActive(boolean active) {
        mediaSession.setActive(active);
    }

    public void destroy() {
        disposables.clear();
        mediaSession.release();
    }
}