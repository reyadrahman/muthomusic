package com.mutho.music.search;

import android.support.annotation.NonNull;
import com.mutho.music.model.Album;
import com.mutho.music.model.AlbumArtist;
import com.mutho.music.model.Song;
import java.util.List;

public class SearchResult {

    @NonNull
    List<AlbumArtist> albumArtists;

    @NonNull
    List<Album> albums;

    @NonNull
    List<Song> songs;

    public SearchResult(@NonNull List<AlbumArtist> albumArtists, @NonNull List<Album> albums, @NonNull List<Song> songs) {
        this.albumArtists = albumArtists;
        this.albums = albums;
        this.songs = songs;
    }
}
