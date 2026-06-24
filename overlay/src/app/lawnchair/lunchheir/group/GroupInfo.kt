package app.lawnchair.lunchheir.group

import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * A **Group**: like a folder, but it never collapses — its children render inline on the
 * workspace. This is the model class (a [CollectionInfo], mirroring FolderInfo's contract).
 *
 * This is increment 1 of the Groups feature: the class compiles against the model but is
 * instantiated nowhere yet, so it changes no behavior. The loader/bind/edit/auto-accept wiring
 * (the parts that touch core Launcher3) lands in later increments.
 */
class GroupInfo : CollectionInfo() {

    private val contents = ArrayList<ItemInfo>()

    init {
        itemType = ITEM_TYPE_GROUP
    }

    override fun add(item: ItemInfo) {
        require(willAcceptItemType(item.itemType)) {
            "tried to add an illegal type into a group"
        }
        contents.add(item)
    }

    // Returns the live backing list, deliberately, to match FolderInfo/CollectionInfo: Launcher3's
    // loader and WorkspaceItemProcessor mutate a collection's contents *through* getContents()
    // (e.g. `collection.getContents().forEach(...)`, removal on drag-out), so an unmodifiable view
    // would break that contract. add() still enforces willAcceptItemType for our own call sites.
    override fun getContents(): List<ItemInfo> = contents

    override fun getAppContents(): List<WorkspaceItemInfo> {
        val result = ArrayList<WorkspaceItemInfo>()
        for (item in contents) {
            when (item) {
                is WorkspaceItemInfo -> result.add(item)
                is AppPairInfo -> result.addAll(item.getAppContents())
            }
        }
        return result
    }

    companion object {
        // Merge-safe ids well clear of upstream's range: upstream reserves containers at/beyond
        // -200 (EXTENDED_CONTAINERS) for non-AOSP variants, and uses item types 0..11.
        const val CONTAINER_GROUP = -201
        const val ITEM_TYPE_GROUP = 100

        /** Groups, like folders, accept apps / deep shortcuts / app pairs. */
        fun willAcceptItemType(itemType: Int): Boolean =
            itemType == Favorites.ITEM_TYPE_APPLICATION ||
                itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT ||
                itemType == Favorites.ITEM_TYPE_APP_PAIR
    }
}
