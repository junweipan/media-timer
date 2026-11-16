package expo.modules.mediatimer

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * Sends the phone to the home screen from the background.
 *
 * Android blocks activity starts from the background unless the app holds the "Display over
 * other apps" permission, and from Android 15 it must also be showing an overlay window at
 * that moment. So this shows an invisible 1x1 overlay for a second while it starts the
 * launcher. Without the permission it does nothing.
 */
internal object HomeLauncher {
  fun canGoHome(context: Context): Boolean = Settings.canDrawOverlays(context)

  fun goHome(context: Context) {
    val app = context.applicationContext
    if (!canGoHome(app)) return

    val handler = Handler(Looper.getMainLooper())
    handler.post {
      val overlay = showOverlay(app)
      handler.postDelayed({ startHome(app) }, 150)
      handler.postDelayed({ removeOverlay(app, overlay) }, 1500)
    }
  }

  private fun startHome(context: Context) {
    val home = Intent(Intent.ACTION_MAIN)
      .addCategory(Intent.CATEGORY_HOME)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(home) }
  }

  private fun showOverlay(context: Context): View? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val params = WindowManager.LayoutParams(
      1,
      1,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
      PixelFormat.TRANSLUCENT
    )
    val view = View(context)
    val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return runCatching { windows.addView(view, params); view }.getOrNull()
  }

  private fun removeOverlay(context: Context, view: View?) {
    if (view == null) return
    val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    runCatching { windows.removeView(view) }
  }
}
