package com.mutho.music.playback

import android.content.Context
import android.content.Intent
import com.mutho.music.R
import com.mutho.music.model.Song
import com.mutho.music.playback.constants.ExternalIntents

class ScrobbleManager {

    enum class ScrobbleStatus(val value: Int) {
        START(0),
        RESUME(1),
        PAUSE(2),
        COMPLETE(3)
    }

    fun scrobbleBroadcast(context: Context, state: ScrobbleStatus, song: Song) {
        if (PlaybackSettingsManager.enableLastFmScrobbling) {
            val intent = Intent(ExternalIntents.SCROBBLER)
            intent.putExtra("state", state.value)
            intent.putExtra("app-name", context.getString(R.string.app_name))
            intent.putExtra("app-package", context.packageName)
            intent.putExtra("artist", song.artistName)
            intent.putExtra("album", song.albumName)
            intent.putExtra("track", song.name)
            intent.putExtra("duration", song.duration / 1000)
            context.sendBroadcast(intent)
        }
    }
}
