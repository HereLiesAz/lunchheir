package app.lawnchair.lunchheir.group

import app.lawnchair.LawnchairLauncher
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Converts an existing Lawnchair **folder into a [GroupInfo]** — the chosen way to create a group
 * (reuse folder creation, then "promote"). This is the model surgery; the entry point that calls it
 * (a "Convert to group" action in the folder's options menu) is a separate one-line seam patch.
 *
 * The conversion, via the verified [com.android.launcher3.model.ModelWriter] API:
 *  1. create a group row in the folder's old workspace cell, sized to hold the children;
 *  2. reparent every child into the group with *relative* cells (container = the new group id);
 *  3. delete the now-empty folder row (children only moved, never deleted);
 *  4. force a model reload so the workspace rebinds with the group in place.
 *
 * Dormant until the menu seam calls it — no behavior changes yet. It deliberately touches only
 * ModelWriter (no loader internals), so it stays additive and rebase-safe.
 */
object GroupPromotion {

    @JvmStatic
    fun promote(launcher: LawnchairLauncher, folder: FolderInfo) {
        val writer = launcher.modelWriter
        val children = ArrayList<ItemInfo>(folder.getContents())

        // Square-ish grid that fits the children (at least 1x1 for an empty folder).
        val cols = ceil(sqrt(children.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(children.size.toDouble() / cols).toInt().coerceAtLeast(1)

        val group = GroupInfo().apply {
            title = folder.title
            spanX = cols
            spanY = rows
        }
        // Place the group where the folder was; addItemToDatabase assigns group.id.
        writer.addItemToDatabase(group, folder.container, folder.screenId, folder.cellX, folder.cellY)

        children.forEachIndexed { index, child ->
            group.add(child)
            writer.moveItemInDatabase(child, group.id, 0, index % cols, index / cols)
        }

        // Children are already reparented, so this removes only the empty folder row.
        writer.deleteItemFromDatabase(folder, "promoted to Lunch Heir group")

        // Rebind the workspace so the new group renders in place of the folder.
        launcher.model.forceReload()
    }
}
