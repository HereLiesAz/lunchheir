package app.lawnchair.lunchheir.group

import android.content.pm.LauncherApps
import android.os.Process
import app.lawnchair.LawnchairLauncher
import app.lawnchair.lunchheir.smartfill.InstalledAppsSource
import app.lawnchair.lunchheir.smartfill.SmartFill
import com.android.launcher3.model.data.AppInfo
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Smart seeding for a group: take the group's current apps as seeds, ask [SmartFill] which other
 * installed apps fit the emerging pattern (and what to title it), and add the matches into the group
 * as new workspace items. The on-device baseline always runs; the provider-agnostic cloud refiner
 * runs only when the user has enabled and configured it.
 *
 * Coupling is confined to minting a `WorkspaceItemInfo` per matched app
 * ([AppInfo.makeWorkspaceItem], verified against the pinned upstream) and `ModelWriter` add/update —
 * no loader internals. The caller reloads the model afterward so the additions bind with icons.
 *
 * Main-profile apps only for now (matches are resolved against [Process.myUserHandle]); work-profile
 * seeding is a follow-up.
 */
object SmartGroupSeeder {

    suspend fun fill(launcher: LawnchairLauncher, group: GroupInfo) {
        val index = InstalledAppsSource(launcher).byPackage()

        val present = group.getAppContents()
            .mapNotNull { it.targetComponent?.packageName }
            .toMutableSet()
        val seeds = present.mapNotNull { index[it] }

        val result = SmartFill.suggest(launcher, seeds, group.title?.toString())

        val launcherApps = launcher.getSystemService(LauncherApps::class.java) ?: return
        val user = Process.myUserHandle()
        val writer = launcher.modelWriter
        val cols = group.spanX.coerceAtLeast(1)

        var cellIndex = group.getContents().size
        for (app in result.members) {
            if (app.packageName in present) continue
            val activity = launcherApps.getActivityList(app.packageName, user).firstOrNull() ?: continue
            val item = AppInfo(launcher, activity, user).makeWorkspaceItem(launcher)
            writer.addItemToDatabase(item, group.id, 0, cellIndex % cols, cellIndex / cols)
            group.add(item)
            present.add(app.packageName)
            cellIndex++
        }

        // Resize the group to fit the grown membership and apply the suggested title, then persist.
        val total = group.getContents().size
        val newCols = ceil(sqrt(total.toDouble())).toInt().coerceAtLeast(1)
        group.spanX = newCols
        group.spanY = ceil(total.toDouble() / newCols).toInt().coerceAtLeast(1)
        result.suggestedTitle?.let { group.title = it }
        writer.updateItemInDatabase(group)
    }
}
