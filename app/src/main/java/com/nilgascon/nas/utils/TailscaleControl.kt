package com.nilgascon.nas.utils
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
object TailscaleControl {
    private const val PACKAGE_NAME = "com.tailscale.ipn"
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME) != null
            } catch (e2: Exception) {
                false
            }
        }
    }
    fun openAndConnect(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE_NAME"))
            marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(marketIntent)
        }
    }
    fun disconnectAndExit(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
        if (intent != null) {
            activity.startActivity(intent)
        }
        activity.finishAffinity()
        System.exit(0)
    }
}
