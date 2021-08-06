package com.mutho.music.utils.extensions

import com.mutho.music.model.Genre
import com.mutho.music.model.Song
import com.mutho.music.utils.ComparisonUtils
import io.reactivex.Single
import java.util.Comparator

fun Genre.getSongs(): Single<List<Song>> {
    return songsObservable
        .map { songs ->
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compareInt(b.year, a.year) })
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compareInt(a.track, b.track) })
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compareInt(a.discNumber, b.discNumber) })
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compare(a.albumName, b.albumName) })
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compare(a.albumArtistName, b.albumArtistName) })
            songs
        }
}