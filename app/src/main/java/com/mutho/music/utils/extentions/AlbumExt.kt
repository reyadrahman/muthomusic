package com.mutho.music.utils.extensions

import com.mutho.music.model.Album
import com.mutho.music.model.Song
import com.mutho.music.utils.ComparisonUtils
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.util.ArrayList

fun Album.getSongsSingle(): Single<List<Song>> {
    return songsSingle
        .map { songs ->
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compareInt(b.year, a.year) })
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compareInt(a.track, b.track) })
            songs.sortWith(Comparator { a, b -> ComparisonUtils.compareInt(a.discNumber, b.discNumber) })
            songs
        }
}

fun Single<List<Album>>.getSongsSingle(): Single<List<Song>> {
    return this.flatMapObservable { list -> Observable.fromIterable(list) }
        .concatMap { album -> album.songsSingle.toObservable() }
        .reduce(emptyList(),
            { songs: List<Song>, songs2: List<Song> ->
                val allSongs = ArrayList<Song>(songs)
                allSongs.addAll(songs2)
                allSongs
            })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
}