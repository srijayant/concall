package com.aiconference.app.popout

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import com.aiconference.app.tiles.AiService
import com.aiconference.app.tiles.Tile

/**
 * Launch helpers for flexible/freeform windows.
 *
 * Stock OxygenOS allows only ONE flexible window at a time, so pop-out is a
 * "focus mode": launching a new pop-out closes the previous one and parks its
 * tile back in the grid. With the "enable freeform windows" developer option
 * (`adb shell settings put global enable_freeform_support 1`) more windows
 * may be possible, but we do not rely on it.
 */
object FreeformLauncher {

    fun freeformSettingEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(false)

    /** Pop a web tile out into its own window; enforces the single-window focus mode. */
    fun popOutTile(activity: Activity, tile: Tile) {
        PopOutActivity.closeAllExcept(tile.id)
        tile.poppedOut = true
        val intent = Intent(activity, PopOutActivity::class.java)
            .putExtra(PopOutActivity.EXTRA_TILE_ID, tile.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivity(intent, windowOptions(activity).toBundle())
    }

    /**
     * Launch the service's native Android app (if installed) in a freeform
     * window. Native apps can never be embedded in the grid; this is a
     * side-channel for users who prefer e.g. the ChatGPT app's voice mode.
     */
    fun launchNativeApp(activity: Activity, service: AiService): Boolean {
        val pm = activity.packageManager
        val intent = service.nativePackages.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent, windowOptions(activity).toBundle())
        return true
    }

    fun isNativeAppInstalled(context: Context, service: AiService): Boolean =
        service.nativePackages.any { context.packageManager.getLaunchIntentForPackage(it) != null }

    /**
     * Request launch bounds covering ~70% of the screen, centered. On devices
     * honoring freeform this yields a floating window; elsewhere the system
     * ignores the bounds and launches full screen, which is a safe fallback.
     */
    private fun windowOptions(activity: Activity): ActivityOptions {
        val metrics = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds
        } else {
            val dm = activity.resources.displayMetrics
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val w = (metrics.width() * 0.7f).toInt()
        val h = (metrics.height() * 0.6f).toInt()
        val left = (metrics.width() - w) / 2
        val top = (metrics.height() - h) / 2
        return ActivityOptions.makeBasic().apply {
            launchBounds = Rect(left, top, left + w, top + h)
        }
    }
}
