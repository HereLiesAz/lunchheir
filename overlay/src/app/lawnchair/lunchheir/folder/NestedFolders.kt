package app.lawnchair.lunchheir.folder

import android.content.Context
import app.lawnchair.lunchheir.LunchHeirPrefs
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo

/**
 * Gate for **nested folders** (a folder inside a folder). Two upstream seams consult it:
 *  - `LoaderCursor.checkAndAddItem` ([isEnabled], context in hand) — attach a folder as a child;
 *  - `FolderInfo.willAcceptItemType` ([isAccepting], a *static* with no context) — let the existing
 *    drag-to-folder flow drop a folder onto/into another folder, so creation reuses Launcher3's drag
 *    machinery rather than new code.
 *
 * Because the accept seam is context-free, the toggle is cached into a flag refreshed at launcher
 * create ([refresh]). **Off by default**: with the toggle off both seams are no-ops and folder
 * behavior is identical to upstream.
 */
object NestedFolders {

    /** Max nesting depth the cycle/depth guard will enforce (a later increment). */
    const val MAX_DEPTH = 3

    @Volatile
    private var accepting: Boolean? = null

    /** Refresh the cached accept-flag from prefs. Called at launcher create. */
    @JvmStatic
    fun refresh(context: Context) {
        accepting = LunchHeirPrefs.isEnabled(context, LunchHeirPrefs.Feature.NESTED_FOLDERS)
    }

    /** Context-free accept check for the static `FolderInfo.willAcceptItemType` seam. */
    @JvmStatic
    fun isAccepting(): Boolean = accepting ?: false

    /**
     * Guard for dropping [dragged] into [target] (the `FolderIcon.willAcceptItem` seam). Folder-only:
     * non-folder drags are always allowed, so normal drag-and-drop is unchanged. Blocks a drop that
     * would create a **cycle** — target is the dragged folder, or lives inside it — which would
     * otherwise recurse forever at render. Also applies a best-effort depth cap on the dragged
     * subtree's own height (a chain built one level at a time can still exceed it; the cycle guard is
     * the hard safety, the cap is cosmetic).
     */
    @JvmStatic
    fun canDrop(target: FolderInfo?, dragged: ItemInfo?): Boolean {
        if (target == null || dragged !is FolderInfo) return true
        if (target === dragged) return false
        if (contains(dragged, target)) return false
        return height(dragged) < MAX_DEPTH
    }

    /** True if [needle] is anywhere inside [folder]'s subtree. */
    private fun contains(folder: FolderInfo, needle: ItemInfo): Boolean {
        for (child in folder.getContents()) {
            if (child === needle) return true
            if (child is FolderInfo && contains(child, needle)) return true
        }
        return false
    }

    /** Height of [folder]'s subtree: 1 for a folder with no sub-folders. */
    private fun height(folder: FolderInfo): Int {
        var deepest = 0
        for (child in folder.getContents()) {
            if (child is FolderInfo) deepest = maxOf(deepest, height(child))
        }
        return deepest + 1
    }

    /**
     * Context-backed gate for the loader seam. Reads prefs only once (lazily, if [refresh] hasn't
     * run yet) — this is called for every workspace item during loading, so it must not hit
     * SharedPreferences each time.
     */
    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        // Double-checked locking: many loader threads may race here on first read; only one hits prefs.
        return accepting ?: synchronized(this) {
            accepting ?: LunchHeirPrefs.isEnabled(context, LunchHeirPrefs.Feature.NESTED_FOLDERS)
                .also { accepting = it }
        }
    }
}
