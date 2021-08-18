package com.mutho.music.glide.fetcher;

import com.mutho.music.model.ArtworkProvider;
import com.mutho.music.utils.ArtworkUtils;
import java.io.File;
import java.io.InputStream;

class FolderFetcher extends BaseFetcher {

    private static final String TAG = "FolderFetcher";

    private File file;

    FolderFetcher(ArtworkProvider artworkProvider, File file) {
        super(artworkProvider);
        this.file = file;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected InputStream getStream() {

        if (file == null) {
            return artworkProvider.getFolderArtwork();
        }

        return ArtworkUtils.getFileArtwork(file);
    }
}