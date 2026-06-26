package app.lawnchair.lunchheir.folder

import android.content.Context
import app.lawnchair.lunchheir.LunchHeirPrefs

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
