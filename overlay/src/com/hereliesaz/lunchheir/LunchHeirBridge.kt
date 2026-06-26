package com.hereliesaz.lunchheir

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast

/**
 * "Lunch Heir Bridge" companion: detection + a *guide-the-user-to-download* action.
 *
 * The bridge is a separate app (`com.hereliesaz.lunchheir.bridge`): Google only serves the Discover
 * feed to debuggable apps, so it can't live inside the release launcher. Lunch Heir deliberately does
 * **not** bundle or silently install it — installing an APK for the user is hostile and a Play-policy
 * risk. Instead [openDownloadPage] sends the user to the published bridge APK to download and install
 * themselves. Once installed, `FeedBridge` trusts it by signature (same signing key), so no
 * hard-coded hash is needed.
 */
object LunchHeirBridge {

    private const val TAG = "LunchHeirBridge"

    const val PACKAGE = "com.hereliesaz.lunchheir.bridge"

    /** Where the user downloads + installs the bridge APK themselves (published by bridge.yml). */
    const val DOWNLOAD_URL = "https://github.com/hereliesaz/lunchheir/releases/tag/bridge-latest"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Open the bridge download page so the user can install it themselves. */
    fun openDownloadPage(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            Log.w(TAG, "could not open bridge download page", e)
            runCatching {
                Toast.makeText(context, "Couldn't open the download page", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
