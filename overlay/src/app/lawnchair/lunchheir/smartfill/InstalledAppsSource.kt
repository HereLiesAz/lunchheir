package app.lawnchair.lunchheir.smartfill

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserManager

/**
 * Resolves the installed-app candidate set the [SmartFillEngine] scores against, as [AppSignals].
 *
 * Deliberately built on **platform APIs** ([LauncherApps] + [android.content.pm.PackageManager]),
 * not Lawnchair's model: the engine needs category / installer / label signals that the framework
 * already exposes, and staying off Lawnchair internals keeps this rebase-safe as upstream changes.
 * (A launcher app holds the LauncherApps permission, so `getActivityList` returns the full set.)
 *
 * Dormant in increment 2 — instantiated nowhere yet. Increment 3+ calls [all] on install/uninstall
 * and feeds the result into [SmartFillEngine.converge] alongside a collection's seed apps.
 */
class InstalledAppsSource(private val context: Context) {

    /** Every launchable app across the user's profiles, as scoring signals. */
    fun all(): List<AppSignals> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val pm = context.packageManager

        val result = ArrayList<AppSignals>()
        for (user in userManager.userProfiles) {
            // Serial number is the stable, documented per-user identifier (UserHandle.hashCode is
            // not contractually stable); keeps each profile's component keys unique and durable.
            val userSerial = userManager.getSerialNumberForUser(user)
            for (info in launcherApps.getActivityList(null, user)) {
                val appInfo = info.applicationInfo
                val pkg = appInfo.packageName
                // getInstallerPackageName is deprecated for getInstallSourceInfo (API 30), but the
                // latter would trip NewApi lint at minSdk 26; this stays available and lint-clean.
                val installer = try {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(pkg)
                } catch (e: IllegalArgumentException) {
                    null
                }
                result.add(
                    AppSignals(
                        key = info.componentName.flattenToString() + "#" + userSerial,
                        packageName = pkg,
                        label = info.label.toString(),
                        category = appInfo.category,
                        installer = installer,
                    ),
                )
            }
        }
        return result
    }

    /** Signals indexed by package, for resolving a collection's existing members into seeds. */
    fun byPackage(): Map<String, AppSignals> = all().associateBy { it.packageName }
}
