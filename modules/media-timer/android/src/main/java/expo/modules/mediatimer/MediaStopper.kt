package expo.modules.mediatimer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Stops whatever other apps are playing. It uses two system-wide mechanisms, neither of which
 * needs a special permission:
 *
 * 1. A media PAUSE key event. Android routes it to the active media session (the browser,
 *    YouTube, Spotify, a podcast app...). PAUSE never starts playback, unlike PLAY_PAUSE.
 * 2. A permanent audio focus request. The app that holds focus receives AUDIOFOCUS_LOSS and
 *    must stop; Android mutes apps that ignore it.
 */
internal object MediaStopper {
  private val focusListener = AudioManager.OnAudioFocusChangeListener { }
  private var abandon: (() -> Unit)? = null

  @Synchronized
  fun stop(context: Context) {
    val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    sendKey(audio, KeyEvent.ACTION_DOWN)
    sendKey(audio, KeyEvent.ACTION_UP)

    release()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setOnAudioFocusChangeListener(focusListener)
        .build()
      audio.requestAudioFocus(request)
      abandon = { audio.abandonAudioFocusRequest(request) }
    } else {
      @Suppress("DEPRECATION")
      audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
      @Suppress("DEPRECATION")
      abandon = { audio.abandonAudioFocus(focusListener) }
    }
  }

  /** Gives audio focus back. The other apps lost it permanently, so they do not resume. */
  @Synchronized
  fun release() {
    abandon?.invoke()
    abandon = null
  }

  private fun sendKey(audio: AudioManager, action: Int) {
    val now = SystemClock.uptimeMillis()
    audio.dispatchMediaKeyEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
  }
}
