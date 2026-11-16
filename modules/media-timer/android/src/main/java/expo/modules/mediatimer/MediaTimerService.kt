package expo.modules.mediatimer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.text.DateFormat
import java.util.Date

/**
 * Foreground service that owns the countdown. It keeps running when the app is closed, shows
 * the countdown notification with "+5 min" and "Cancel", and stops media when time is up.
 */
class MediaTimerService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private val timeUp = Runnable { onTimeUp() }
  private lateinit var store: TimerStore
  private var finishing = false

  override fun onCreate() {
    super.onCreate()
    store = TimerStore(this)
    createChannel()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // A service started with startForegroundService() must go foreground straight away.
    goForeground()

    when (intent?.action) {
      ACTION_START -> {
        val duration = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        if (duration > 0) {
          finishing = false
          handler.removeCallbacksAndMessages(null)
          MediaStopper.release()
          store.lastDurationMs = duration
          store.endTime = System.currentTimeMillis() + duration
          arm()
        } else {
          resume()
        }
      }
      ACTION_EXTEND -> {
        if (store.endTime > 0 && !finishing) {
          store.endTime += intent.getLongExtra(EXTRA_DURATION_MS, 0L)
          arm()
        } else {
          resume()
        }
      }
      ACTION_CANCEL -> shutdown()
      // ACTION_FIRE from the alarm, or a null intent when Android restarts the service.
      else -> resume()
    }
    return START_STICKY
  }

  override fun onDestroy() {
    handler.removeCallbacksAndMessages(null)
    super.onDestroy()
  }

  /** Continues from the stored state: fire if the time has passed, otherwise keep counting. */
  private fun resume() {
    if (finishing) return
    val endTime = store.endTime
    when {
      endTime <= 0 -> shutdown()
      System.currentTimeMillis() >= endTime - 1000 -> onTimeUp()
      else -> arm()
    }
  }

  /** Schedules the end of the timer and refreshes the notification. */
  private fun arm() {
    val endTime = store.endTime
    goForeground()
    scheduleAlarm(endTime)
    // The alarm wakes the phone from doze. The handler is a second trigger for the normal
    // case where the phone is awake because something is playing.
    handler.removeCallbacks(timeUp)
    handler.postDelayed(timeUp, (endTime - System.currentTimeMillis()).coerceAtLeast(0))
    notifyListener()
  }

  private fun onTimeUp() {
    if (finishing) return
    // An extended timer can leave an old trigger behind; ignore it.
    val endTime = store.endTime
    if (endTime <= 0 || System.currentTimeMillis() < endTime - 1000) return

    finishing = true
    handler.removeCallbacks(timeUp)
    cancelAlarm()
    store.endTime = 0

    val power = getSystemService(Context.POWER_SERVICE) as PowerManager
    power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MediaTimer:fire").acquire(10_000)

    MediaStopper.stop(this)
    if (store.goHome) HomeLauncher.goHome(this)
    showFinishedNotification()
    notifyListener()

    // Hold audio focus briefly so the other app really stops, then finish.
    handler.postDelayed({
      MediaStopper.release()
      shutdown()
    }, 2500)
  }

  private fun shutdown() {
    finishing = false
    handler.removeCallbacksAndMessages(null)
    cancelAlarm()
    store.endTime = 0
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
    notifyListener()
  }

  private fun notifyListener() {
    listener?.invoke()
  }

  // region Alarm

  private fun alarmIntent(): PendingIntent {
    val intent = Intent(this, MediaTimerService::class.java).setAction(ACTION_FIRE)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(this, REQUEST_FIRE, intent, flags)
    } else {
      PendingIntent.getService(this, REQUEST_FIRE, intent, flags)
    }
  }

  private fun scheduleAlarm(endTime: Long) {
    val alarms = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactAllowed =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    runCatching {
      if (exactAllowed) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, alarmIntent())
      } else {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, alarmIntent())
      }
    }
  }

  private fun cancelAlarm() {
    val alarms = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarms.cancel(alarmIntent())
  }

  // endregion

  // region Notifications

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(CHANNEL_ID, "Timer", NotificationManager.IMPORTANCE_LOW)
    channel.description = "Shows the running media timer"
    channel.setShowBadge(false)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun goForeground() {
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    } else {
      0
    }
    runCatching {
      ServiceCompat.startForeground(this, NOTIFICATION_RUNNING, runningNotification(), type)
    }
  }

  private fun openAppIntent(): PendingIntent? {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
    return PendingIntent.getActivity(
      this, REQUEST_OPEN, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun actionIntent(action: String, requestCode: Int, durationMs: Long = 0): PendingIntent {
    val intent = Intent(this, MediaTimerService::class.java)
      .setAction(action)
      .putExtra(EXTRA_DURATION_MS, durationMs)
    return PendingIntent.getService(
      this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun runningNotification(): Notification {
    val endTime = store.endTime
    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.media_timer_ic_notification)
      .setContentIntent(openAppIntent())
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    if (endTime > System.currentTimeMillis()) {
      val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endTime))
      builder
        .setContentTitle("Media stops at $endsAt")
        .setContentText("Playback in other apps will be paused")
        .setWhen(endTime)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .addAction(0, "+5 min", actionIntent(ACTION_EXTEND, REQUEST_EXTEND, 5 * 60_000L))
        .addAction(0, "Cancel", actionIntent(ACTION_CANCEL, REQUEST_CANCEL))
    } else {
      builder.setContentTitle("Media Timer").setContentText("Stopping media…")
    }
    return builder.build()
  }

  private fun showFinishedNotification() {
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.media_timer_ic_notification)
      .setContentTitle("Time's up")
      .setContentText("Media playback was paused")
      .setContentIntent(openAppIntent())
      .setAutoCancel(true)
      .setSilent(true)
      .setTimeoutAfter(10 * 60_000L)
      .build()
    runCatching {
      getSystemService(NotificationManager::class.java).notify(NOTIFICATION_FINISHED, notification)
    }
  }

  // endregion

  companion object {
    const val ACTION_START = "expo.modules.mediatimer.START"
    const val ACTION_EXTEND = "expo.modules.mediatimer.EXTEND"
    const val ACTION_CANCEL = "expo.modules.mediatimer.CANCEL"
    const val ACTION_FIRE = "expo.modules.mediatimer.FIRE"
    const val EXTRA_DURATION_MS = "durationMs"

    private const val CHANNEL_ID = "media_timer"
    private const val NOTIFICATION_RUNNING = 4101
    private const val NOTIFICATION_FINISHED = 4102
    private const val REQUEST_FIRE = 1
    private const val REQUEST_EXTEND = 2
    private const val REQUEST_CANCEL = 3
    private const val REQUEST_OPEN = 4

    /** Called on every state change so the JS module can emit an event. */
    @Volatile
    var listener: (() -> Unit)? = null
  }
}
