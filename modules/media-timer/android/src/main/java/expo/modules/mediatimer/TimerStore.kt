package expo.modules.mediatimer

import android.content.Context

/** Timer state kept in SharedPreferences, so the service and the JS module see the same values. */
internal class TimerStore(context: Context) {
  private val prefs =
    context.applicationContext.getSharedPreferences("media_timer", Context.MODE_PRIVATE)

  /** Wall-clock time (ms since epoch) at which the timer ends. 0 means no timer is set. */
  var endTime: Long
    get() = prefs.getLong("endTime", 0L)
    set(value) = prefs.edit().putLong("endTime", value).apply()

  var goHome: Boolean
    get() = prefs.getBoolean("goHome", false)
    set(value) = prefs.edit().putBoolean("goHome", value).apply()

  var lastDurationMs: Long
    get() = prefs.getLong("lastDurationMs", 20 * 60_000L)
    set(value) = prefs.edit().putLong("lastDurationMs", value).apply()
}
