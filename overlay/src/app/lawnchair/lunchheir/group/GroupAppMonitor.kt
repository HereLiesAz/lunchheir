package app.lawnchair.lunchheir.group

import android.content.pm.LauncherApps
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import kotlinx.coroutines.launch

/**
 * Keeps **smart groups** up to date as the app inventory changes — the continuous, self-reinforcing
 * half of auto-fill. When a package is installed, every smart group currently on the workspace is
 * re-seeded through [SmartGroupSeeder], so a newly installed app that fits the pattern is absorbed
 * without the user touching the group.
 *
 * Smart groups are found by walking the view tree for bound [GroupView]s (their tag is the
 * [GroupInfo]) and filtering by [SmartGroupRegistry] — no launcher model internals. Bound to the
 * launcher lifecycle by [app.lawnchair.lunchheir.LunchHeirHome] so the callback is only registered
 * while home is active. Gated by the GROUPS feature toggle at the call site.
 *
 * Reacts only to installs (not every package change) to bound churn; debouncing rapid installs is a
 * follow-up.
 */
class GroupAppMonitor(private val launcher: LawnchairLauncher) {

    private val launcherApps = launcher.getSystemService(LauncherApps::class.java)
    private val registry = SmartGroupRegistry(launcher)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reevaluate()
        override fun onPackageRemoved(packageName: String, user: UserHandle) {}
        override fun onPackageChanged(packageName: String, user: UserHandle) {}
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) {}
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) {}
    }

    fun start() {
        launcherApps?.registerCallback(callback)
    }

    fun stop() {
        launcherApps?.unregisterCallback(callback)
    }

    private fun reevaluate() {
        if (registry.isEmpty()) return
        val groups = ArrayList<GroupInfo>()
        collectSmartGroups(launcher.dragLayer, groups)
        if (groups.isEmpty()) return

        launcher.lifecycleScope.launch {
            for (group in groups) SmartGroupSeeder.fill(launcher, group)
            launcher.model.forceReload()
        }
    }

    private fun collectSmartGroups(view: View, out: MutableList<GroupInfo>) {
        if (view is GroupView) {
            (view.tag as? GroupInfo)?.let { if (registry.isSmart(it.id)) out.add(it) }
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectSmartGroups(view.getChildAt(i), out)
        }
    }
}
