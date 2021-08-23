package com.mutho.music.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.util.List;

public interface ArtworkProvider {

    @interface Type {
        int MEDIA_STORE = 0;
        int TAG = 1;
        int FOLDER = 2;
        int REMOTE = 3;
    }

    @NonNull
    String getArtworkKey();

    @Nullable
    String getRemoteArtworkUrl();

    @Nullable
    InputStream getMediaStoreArtwork();

    @Nullable
    InputStream getFolderArtwork();

    @Nullable
    InputStream getTagArtwork();

    @Nullable
    List<File> getFolderArtworkFiles();
}