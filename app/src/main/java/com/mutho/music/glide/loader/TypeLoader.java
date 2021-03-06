package com.mutho.music.glide.loader;

import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.mutho.music.glide.fetcher.TypeFetcher;
import com.mutho.music.model.ArtworkProvider;
import java.io.File;
import java.io.InputStream;

public class TypeLoader implements ModelLoader<ArtworkProvider, InputStream> {

    private static final String TAG = "ArtworkModelLoader";

    @ArtworkProvider.Type
    private int type;

    private File file;

    public TypeLoader(@ArtworkProvider.Type int type, File file) {
        this.type = type;
        this.file = file;
    }

    @Override
    public DataFetcher<InputStream> getResourceFetcher(ArtworkProvider model, int width, int height) {
        return new TypeFetcher(model, type, file);
    }
}
