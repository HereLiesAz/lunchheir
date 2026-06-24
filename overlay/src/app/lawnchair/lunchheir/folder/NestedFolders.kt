package app.lawnchair.lunchheir.folder

import android.content.Context
import app.lawnchair.lunchheir.LunchHeirPrefs

/**
 * Gate for **nested folders** (a folder inside a folder). The loader seam in
 * `LoaderCursor.checkAndAddItem` consults [isEnabled] before attaching an `ITEM_TYPE_FOLDER` as a
 * child of another collection — the one place the loader otherwise excludes folders-in-folders.
 *
 * **Off by default.** With the toggle off, the loader behaves exactly like upstream (folders are not
 * nested), so existing folder loading is untouched — the seam is a true no-op. The in-folder
 * FolderIcon rendering, nested open-navigation (single Folder view + nav stack), and the cycle /
 * depth guard are later increments; until a creation path exists no nested data is produced, so
 * enabling the toggle alone changes nothing visible yet.
 */
object NestedFolders {

    /** Max nesting depth once the guard lands; referenced here so the cap has one home. */
    const val MAX_DEPTH = 3

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        LunchHeirPrefs.isEnabled(context, LunchHeirPrefs.Feature.NESTED_FOLDERS)
}
