package com.mutho.music.glide.fetcher;

import com.bumptech.glide.load.data.HttpUrlFetcher;
import com.bumptech.glide.load.model.GlideUrl;
import com.mutho.music.model.ArtworkProvider;

public class RemoteFetcher extends HttpUrlFetcher {

    String TAG = "RemoteFetcher";

    public RemoteFetcher(ArtworkProvider artworkProvider) {
        super(new GlideUrl(artworkProvider.getRemoteArtworkUrl()));
    }
}