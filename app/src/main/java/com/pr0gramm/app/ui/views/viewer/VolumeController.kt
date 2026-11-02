package com.pr0gramm.app.ui.views.viewer

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.core.content.edit
import androidx.media3.exoplayer.ExoPlayer
import com.pr0gramm.app.Duration
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.Settings
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.ui.base.MainScope
import com.pr0gramm.app.ui.base.launchIgnoreErrors
import com.pr0gramm.app.util.catchAll
import com.pr0gramm.app.util.delay
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.setImageResource

class VolumeController(val view: ImageView, private val exo: () -> ExoPlayer?) {
    private val logger = Logger("VolumeController")
    private val preferences = view.context.injector.instance<SharedPreferences>()

    private val audioManager: AudioManager = view.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val afChangeListener = object : AudioManager.OnAudioFocusChangeListener {
        override fun onAudioFocusChange(focusChange: Int) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                abandonAudioFocus()
                logger.info { "Lost audio focus, muting now." }
                setMuted(true)
            }
        }
    }

    init {
        view.setOnClickListener { toggle() }
    }

    fun abandonAudioFocusSoon() {
        MainScope.launchIgnoreErrors {
            delay(Duration.millis(500))
            abandonAudioFocus()
        }
    }

    private fun toggle() {
        val exo = exo() ?: return
        val mutedCurrently = exo.volume < 0.1f
        setMuted(!mutedCurrently)
    }

    private fun setMuted(mute: Boolean): Unit = catchAll {
        val exo = exo()

        if (exo == null) {
            logger.debug { "No exo player found, abandon audio focus now." }
            abandonAudioFocus()
            return
        }

        val hasAudioFocus = if (mute) {
            logger.debug { "Mute requested, abandon audio focus" }
            abandonAudioFocus()
            false
        } else {
            logger.debug { "Request to get audio focus" }
            val result = requestAudioFocus()

            result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }


        if (mute || !hasAudioFocus) {
            exo.volume = 0f
            storeUnmuteTime(0)
            view.setImageResource(R.drawable.ic_video_mute_on)
        } else {
            exo.volume = 1f
            storeUnmuteTime(System.currentTimeMillis())
            view.setImageResource(R.drawable.ic_video_mute_off, ThemeHelper.accentColor)
        }


    }

    /**
     * Mute if not "unmuted" within the last 10 minutes.
     */
    fun applyMuteState() {
        val now = System.currentTimeMillis()
        val lastUnmutedVideo = preferences.getLong(lastUnmutedTimeKey, 0L)
        val diffInSeconds = (now - lastUnmutedVideo) / 1000
        setMuted(diffInSeconds > 10 * 60)
    }

    private fun storeUnmuteTime(time: Long) {
        preferences.edit {
            putLong(lastUnmutedTimeKey, time)
        }
    }

    private var audioFocusRequest: AudioFocusRequest? = null

    private fun requestAudioFocus(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            abandonAudioFocus()
            val request = AudioFocusRequest.Builder(audioFocusGain())
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build())
                .setOnAudioFocusChangeListener(afChangeListener, Handler(Looper.getMainLooper()))
                .build()
            audioFocusRequest = request
            return audioManager.requestAudioFocus(request)
        }

        // Android 6 and 7 do not provide AudioFocusRequest.
        @Suppress("DEPRECATION")
        return audioManager.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, audioFocusGain())
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
            audioFocusRequest = null
        } else {
            // Android 6 and 7 require the listener-based API.
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(afChangeListener)
        }
    }

    private fun audioFocusGain(): Int {
        return if (Settings.audioFocusTransient) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        } else {
            AudioManager.AUDIOFOCUS_GAIN
        }
    }

    companion object {
        private const val lastUnmutedTimeKey = "VolumeController.lastUnmutedVideo"

        fun resetMuteTime(context: Context) {
            val prefs = context.injector.instance<SharedPreferences>()
            prefs.edit {
                putLong(lastUnmutedTimeKey, 0L)
            }
        }
    }
}
