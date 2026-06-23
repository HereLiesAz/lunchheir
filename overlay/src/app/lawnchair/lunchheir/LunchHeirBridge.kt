package app.lawnchair.lunchheir

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log

/**
 * Bundled "Lunch Heir Bridge" companion: detection + one-tap install.
 *
 * Lunch Heir ships the bridge APK as an asset (placed at overlay/bridge-dist/ and bundled by
 * lunchheir-overlay.gradle). Because the Google App only serves the Discover feed to debuggable
 * apps, the bridge can't be merged into the release launcher — but we can install it for the
 * user with a single confirm dialog instead of making them hunt for an APK.
 *
 * [installBundled] is the entry point a settings action / the "enable feed" flow calls. It is not
 * auto-invoked (installing on launch would be hostile).
 */
object LunchHeirBridge {

    private const val TAG = "LunchHeirBridge"
    const val PACKAGE = "com.hereliesaz.lunchheir.bridge"
    const val ASSET = "lunchheir-bridge.apk"
    private const val INSTALL_STATUS_ACTION = "$PACKAGE.INSTALL_STATUS"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** True if the bridge APK was bundled into this build (i.e. overlay/bridge-dist had it). */
    fun isBundled(context: Context): Boolean =
        runCatching { context.assets.list("")?.contains(ASSET) == true }.getOrDefault(false)

    /**
     * Install the bundled bridge APK via the system package installer (shows a user confirm UI).
     * Requires the REQUEST_INSTALL_PACKAGES permission (added to the launcher manifest by the
     * overlay). No-op if the APK isn't bundled or the bridge is already installed.
     */
    fun installBundled(context: Context) {
        if (isInstalled(context)) return
        if (!isBundled(context)) {
            Log.w(TAG, "bridge APK not bundled into this build; nothing to install")
            return
        }
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply { setAppPackageName(PACKAGE) }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                context.assets.open(ASSET).use { input ->
                    session.openWrite("bridge.apk", 0, -1).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val statusIntent = Intent(INSTALL_STATUS_ACTION).setPackage(context.packageName)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to install bundled bridge", e)
        }
    }
}
