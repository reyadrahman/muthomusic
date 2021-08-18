package com.mutho.music.glide.fetcher;

import com.mutho.music.model.ArtworkProvider;
import java.io.InputStream;

public class MediaStoreFetcher extends BaseFetcher {

    String TAG = "MediaStoreFetcher";

    public MediaStoreFetcher(ArtworkProvider artworkProvider) {
        super(artworkProvider);
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected InputStream getStream() {
        return artworkProvider.getMediaStoreArtwork();
    }
}