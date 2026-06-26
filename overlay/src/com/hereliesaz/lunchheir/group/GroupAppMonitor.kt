package com.hereliesaz.lunchheir.group

import android.content.pm.LauncherApps
import android.os.UserHandle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.launch

/**
 * Keeps **smart groups** up to date as the app inventory changes — the continuous, self-reinforcing
 * half of auto-fill. When a package is installed, every smart group currently on the workspace is
 * re-seeded through [SmartGroupSeeder], so a newly installed app that fits the pattern is absorbed
 * without the user touching the group.
 *
 * Smart groups are found by walking the view tree for bound [GroupView]s (their tag is the
 * [GroupInfo]) and filtering by [SmartGroupRegistry] — no launcher model internals. Bound to the
 * launcher lifecycle by [com.hereliesaz.lunchheir.LunchHeirHome] so the callback is only registered
 * while home is active. Gated by the GROUPS feature toggle at the call site.
 *
 * Reacts only to installs (not every package change) to bound churn; debouncing rapid installs is a
 * follow-up.
 */
class GroupAppMonitor(private val launcher: LawnchairLauncher) {

    private val launcherApps = launcher.getSystemService(LauncherApps::class.java)
    private val registry = SmartGroupRegistry(launcher)

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = onInstalled(packageName, user)
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

    /**
     * On a new install: re-seed every smart group (pattern match), and let the first plain group
     * with free space catch the new app (the dumb auto-accept). One reload covers both.
     */
    private fun onInstalled(packageName: String, user: UserHandle) {
        val groups = ArrayList<GroupInfo>()
        collectGroups(launcher.dragLayer, groups)
        if (groups.isEmpty()) return

        launcher.lifecycleScope.launch {
            var changed = false
            for (group in groups) {
                if (registry.isSmart(group.id)) {
                    SmartGroupSeeder.fill(launcher, group)
                    changed = true
                }
            }
            // Dumb auto-accept: the first plain group with a free cell absorbs the new app.
            val plain = groups.firstOrNull {
                !registry.isSmart(it.id) && it.getContents().size < it.spanX * it.spanY
            }
            if (plain != null && addApp(plain, packageName, user)) changed = true

            if (changed) launcher.model.forceReload()
        }
    }

    /** Mint a workspace item for [packageName] and add it to [group]; false if absent / already in. */
    private fun addApp(group: GroupInfo, packageName: String, user: UserHandle): Boolean {
        if (group.getAppContents().any { it.targetComponent?.packageName == packageName }) return false
        val activity = launcherApps?.getActivityList(packageName, user)?.firstOrNull() ?: return false
        val item = AppInfo(launcher, activity, user).makeWorkspaceItem(launcher)
        val cols = group.spanX.coerceAtLeast(1)
        val idx = group.getContents().size
        launcher.modelWriter.addItemToDatabase(item, group.id, 0, idx % cols, idx / cols)
        group.add(item)
        return true
    }

    private fun collectGroups(view: View, out: MutableList<GroupInfo>) {
        if (view is GroupView) {
            (view.tag as? GroupInfo)?.let { out.add(it) }
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectGroups(view.getChildAt(i), out)
        }
    }
}
