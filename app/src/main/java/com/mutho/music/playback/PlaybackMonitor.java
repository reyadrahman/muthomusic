package com.mutho.music.playback;

import com.mutho.music.utils.MusicServiceConnectionUtils;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PlaybackMonitor {

    private static final String TAG = "PlaybackMonitor";

    private Flowable<Float> progressObservable;
    private Flowable<Long> currentTimeObservable;

    @Inject
    public PlaybackMonitor(MediaManager mediaManager) {
        progressObservable = Flowable.defer(() -> Observable.interval(32, TimeUnit.MILLISECONDS)
                .filter(aLong -> {
                    if (MusicServiceConnectionUtils.serviceBinder != null
                            && MusicServiceConnectionUtils.serviceBinder.getService() != null) {
                        if (mediaManager.getDuration() > 0) {
                            return true;
                        }
                    }
                    return false;
                })
                .map(aLong -> (float) mediaManager.getPosition() / (float) mediaManager.getDuration())
                .toFlowable(BackpressureStrategy.DROP))
                .share();

        currentTimeObservable = Flowable.defer(() -> Observable.interval(150, TimeUnit.MILLISECONDS)
                .filter(aLong -> MusicServiceConnectionUtils.serviceBinder != null
                        && MusicServiceConnectionUtils.serviceBinder.getService() != null)
                .map(time -> mediaManager.getPosition())
                .toFlowable(BackpressureStrategy.DROP))
                .share();
    }

    public Flowable<Float> getProgressObservable() {
        return progressObservable;
    }

    public Flowable<Long> getCurrentTimeObservable() {
        return currentTimeObservable;
    }
}