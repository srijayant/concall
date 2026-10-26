package com.aiconference.app.popout

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import com.aiconference.app.tiles.AiService
import com.aiconference.app.tiles.Tile

/**
 * Launch helpers for flexible/freeform/split windows.
 *
 * Stock OxygenOS lets a third-party app open only ONE flexible window, and
 * usually ignores launch-bounds for *other* apps — so opening Gemini/ChatGPT
 * natively often goes full screen. We still request freeform + split
 * (LAUNCH_ADJACENT); if the OS ignores us, the UI tells the user how to
 * convert it from Recents.
 */
object FreeformLauncher {

    /** Hidden WindowConfiguration.WINDOWING_MODE_FREEFORM. */
    private const val WINDOWING_MODE_FREEFORM = 5

    fun freeformSettingEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1
    }.getOrDefault(false)

    fun popOutTile(activity: Activity, tile: Tile) {
        PopOutActivity.closeAllExcept(tile.id)
        tile.poppedOut = true
        val intent = Intent(activity, PopOutActivity::class.java)
            .putExtra(PopOutActivity.EXTRA_TILE_ID, tile.id)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
            )
        activity.startActivity(intent, windowOptions(activity).toBundle())
    }

    fun launchNativeApp(activity: Activity, service: AiService): Boolean {
        val pm = activity.packageManager
        val intent = service.nativePackages.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
        )
        activity.startActivity(intent, windowOptions(activity).toBundle())
        return true
    }

    fun isNativeAppInstalled(context: Context, service: AiService): Boolean =
        service.nativePackages.any { context.packageManager.getLaunchIntentForPackage(it) != null }

    private fun windowOptions(activity: Activity): ActivityOptions {
        val metrics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds
        } else {
            val dm = activity.resources.displayMetrics
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
        val w = (metrics.width() * 0.55f).toInt()
        val h = (metrics.height() * 0.55f).toInt()
        val left = metrics.width() - w - 24
        val top = (metrics.height() - h) / 5
        val options = ActivityOptions.makeBasic().apply {
            launchBounds = Rect(left, top, left + w, top + h)
        }
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(options, WINDOWING_MODE_FREEFORM)
        }
        return options
    }
}
