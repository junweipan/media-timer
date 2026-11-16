package expo.modules.mediatimer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class MediaTimerModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("MediaTimer")

    Events("onTimerChange")

    OnCreate {
      MediaTimerService.listener = { sendEvent("onTimerChange", state()) }
    }

    OnDestroy {
      MediaTimerService.listener = null
    }

    Function("getState") { state() }

    Function("start") { durationMs: Double ->
      ContextCompat.startForegroundService(
        context,
        serviceIntent(MediaTimerService.ACTION_START, durationMs.toLong())
      )
    }

    Function("extend") { durationMs: Double ->
      if (isRunning()) {
        context.startService(serviceIntent(MediaTimerService.ACTION_EXTEND, durationMs.toLong()))
      }
    }

    Function("cancel") {
      if (TimerStore(context).endTime > 0) {
        context.startService(serviceIntent(MediaTimerService.ACTION_CANCEL))
      }
    }

    Function("setGoHome") { enabled: Boolean ->
      TimerStore(context).goHome = enabled
    }

    Function("stopMediaNow") {
      val appContext = context.applicationContext
      MediaStopper.stop(appContext)
      Handler(Looper.getMainLooper()).postDelayed({ MediaStopper.release() }, 2500)
    }

    // The app draws edge to edge; JS pads the screen by the status and navigation bar sizes.
    Function("getSystemBarInsets") {
      val decorView = appContext.currentActivity?.window?.decorView
      val insets = decorView
        ?.let { ViewCompat.getRootWindowInsets(it) }
        ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      val density = context.resources.displayMetrics.density
      mapOf(
        "top" to (insets?.top ?: 0) / density.toDouble(),
        "bottom" to (insets?.bottom ?: 0) / density.toDouble()
      )
    }

    Function("canGoHome") { HomeLauncher.canGoHome(context) }

    Function("openGoHomeSettings") {
      val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }
  }

  private fun isRunning(): Boolean = TimerStore(context).endTime > System.currentTimeMillis()

  private fun serviceIntent(action: String, durationMs: Long = 0): Intent =
    Intent(context, MediaTimerService::class.java)
      .setAction(action)
      .putExtra(MediaTimerService.EXTRA_DURATION_MS, durationMs)

  private fun state(): Map<String, Any?> {
    val store = TimerStore(context)
    val endTime = store.endTime
    return mapOf(
      "endTime" to if (endTime > System.currentTimeMillis()) endTime.toDouble() else null,
      "goHome" to store.goHome,
      "lastDurationMs" to store.lastDurationMs.toDouble()
    )
  }
}
