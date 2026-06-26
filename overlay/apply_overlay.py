#!/usr/bin/env python3
"""Apply Lunch Heir source-level overlay edits to the pristine upstream submodule.

Some Lunch Heir changes can't be expressed as a Gradle overlay because they touch
upstream Kotlin source (the backup format). Rather than vendoring a fork or shipping
a brittle line-numbered patch, we apply a few *exact-substring* replacements here.

Design goals:
  - Idempotent: running twice is a no-op.
  - Loud on drift: if upstream changes an anchor string, we fail with a clear message
    so the edit is re-done deliberately instead of silently mis-applied.

Run via overlay/apply-overlay.sh, or directly:
    python3 overlay/apply_overlay.py <upstream-dir>

This edits the submodule working tree in place; that is expected. The submodule stays
pristine in git — the edits are applied at build time and never committed into it.
Re-run after every `git submodule update`.
"""
import sys
from pathlib import Path

# (anchor that proves already-applied, original, replacement)
Edit = tuple


def append_once(path: Path, marker: str, text_to_append: str) -> bool:
    """Append text to a file unless `marker` is already present. Idempotent."""
    text = path.read_text(encoding="utf-8")
    if marker in text:
        print(f"  already applied: {path.name} (append)")
        return False
    if not text.endswith("\n"):
        text += "\n"
    text += text_to_append
    path.write_text(text, encoding="utf-8")
    print(f"  appended to: {path.name}")
    return True


def edit_file(path: Path, edits, applied_marker: str) -> bool:
    text = path.read_text(encoding="utf-8")
    if applied_marker in text:
        print(f"  already applied: {path.name}")
        return False
    for original, replacement in edits:
        count = text.count(original)
        if count != 1:
            sys.exit(
                f"ERROR: expected exactly 1 occurrence of anchor in {path}, found {count}.\n"
                f"Upstream likely changed. Re-author this overlay edit.\n"
                f"Anchor:\n{original}"
            )
        text = text.replace(original, replacement)
    path.write_text(text, encoding="utf-8")
    print(f"  patched: {path.name}")
    return True


def main():
    if len(sys.argv) != 2:
        sys.exit("usage: apply_overlay.py <upstream-dir>")
    upstream = Path(sys.argv[1]).resolve()
    if not upstream.is_dir():
        sys.exit(f"ERROR: upstream dir not found: {upstream}\n"
                 f"Did you run `git submodule update --init --recursive`?")

    print("Applying Lunch Heir source overlay to upstream submodule...")

    # ── Backup compatibility ────────────────────────────────────────────────────
    # Lunch Heir reads BOTH Lawnchair backups (info.pb) and its own
    # (lunchheir_info.pb), but writes only the Lunch Heir variant. Stock Lawnchair
    # only looks for "info.pb", so it cannot read a Lunch Heir backup (its `info`
    # is never populated and restore aborts). One-directional compatibility.
    backup = upstream / "lawnchair/src/app/lawnchair/backup/LawnchairBackup.kt"
    if not backup.is_file():
        sys.exit(f"ERROR: expected file missing: {backup}")

    edit_file(
        backup,
        edits=[
            # bump backup version (marker)
            (
                "        private const val BACKUP_VERSION = 1\n",
                "        private const val BACKUP_VERSION = 2\n",
            ),
            # new info-entry constant
            (
                '        const val INFO_FILE_NAME = "info.pb"\n',
                '        const val INFO_FILE_NAME = "info.pb"\n'
                "        // LunchHeir: distinct info entry so stock Lawnchair cannot read our backups\n"
                '        const val LUNCHHEIR_INFO_FILE_NAME = "lunchheir_info.pb"\n',
            ),
            # read both Lawnchair and Lunch Heir info entries
            (
                "                INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },\n",
                "                INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },\n"
                "                LUNCHHEIR_INFO_FILE_NAME to { info = BackupInfo.newBuilder().mergeFrom(it).build() },\n",
            ),
            # write the Lunch Heir info entry name
            (
                "                        out.putNextEntry(ZipEntry(INFO_FILE_NAME))\n",
                "                        out.putNextEntry(ZipEntry(LUNCHHEIR_INFO_FILE_NAME))\n",
            ),
            # distinct file name + extension on export
            (
                '            val fileName = "Lawnchair_Backup ${SimpleDateFormat.getDateTimeInstance().format(Date())}"\n'
                '            return "$fileName.lawnchairbackup"\n',
                '            val fileName = "LunchHeir_Backup ${SimpleDateFormat.getDateTimeInstance().format(Date())}"\n'
                '            return "$fileName.lunchheirbackup"\n',
            ),
        ],
        applied_marker="LUNCHHEIR_INFO_FILE_NAME",
    )

    # ── Home-screen extensions hook ─────────────────────────────────────────────
    # A single line in LawnchairLauncher.onCreate hands control to the overlay so all
    # Lunch Heir home logic (live recents bar, second hotseat, ...) lives in overlay/src.
    launcher = upstream / "lawnchair/src/app/lawnchair/LawnchairLauncher.kt"
    if not launcher.is_file():
        sys.exit(f"ERROR: expected file missing: {launcher}")

    edit_file(
        launcher,
        edits=[
            (
                "        layoutInflater.factory2 = LawnchairLayoutFactory(this)\n"
                "        super.onCreate(savedInstanceState)\n"
                "\n"
                "        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)\n",
                "        layoutInflater.factory2 = LawnchairLayoutFactory(this)\n"
                "        super.onCreate(savedInstanceState)\n"
                "\n"
                "        // LunchHeir: initialize home-screen extensions (live recents bar, etc.)\n"
                "        app.lawnchair.lunchheir.LunchHeirHome.onCreate(this)\n"
                "\n"
                "        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)\n",
            ),
        ],
        applied_marker="app.lawnchair.lunchheir.LunchHeirHome",
    )

    # ── Feed bridge: register + trust the bundled Lunch Heir Bridge ─────────────
    # Make the launcher prefer our bundled bridge for the Google Discover feed, and trust any
    # feed provider signed with our own certificate (no hardcoded signature hash). Ship the
    # bridge signed with the same key as the launcher and FeedBridge accepts it by signature.
    feedbridge = upstream / "lawnchair/src/app/lawnchair/FeedBridge.kt"
    if not feedbridge.is_file():
        sys.exit(f"ERROR: expected file missing: {feedbridge}")
    edit_file(
        feedbridge,
        edits=[
            (
                "    private val bridgePackages by lazy {\n"
                "        listOf(\n"
                '            PixelBridgeInfo("com.google.android.apps.nexuslauncher", R.integer.bridge_signature_hash),\n',
                "    private val bridgePackages by lazy {\n"
                "        listOf(\n"
                "            // LunchHeir: prefer the bundled Lunch Heir Bridge (trusted by signature match)\n"
                '            BridgeInfo("com.hereliesaz.lunchheir.bridge", 0),\n'
                '            PixelBridgeInfo("com.google.android.apps.nexuslauncher", R.integer.bridge_signature_hash),\n',
            ),
            (
                "        open fun isSigned(): Boolean {\n"
                "            when {\n"
                "                BuildConfig.DEBUG -> return true\n",
                "        open fun isSigned(): Boolean {\n"
                "            // LunchHeir: trust a feed provider signed with our own certificate (the\n"
                "            // bundled Lunch Heir Bridge), so no signature hash needs hardcoding.\n"
                "            if (context.packageManager.checkSignatures(context.packageName, packageName) ==\n"
                "                PackageManager.SIGNATURE_MATCH\n"
                "            ) {\n"
                "                return true\n"
                "            }\n"
                "            when {\n"
                "                BuildConfig.DEBUG -> return true\n",
            ),
        ],
        applied_marker="com.hereliesaz.lunchheir.bridge",
    )

    # ── Manifest: permission to install the bundled bridge ──────────────────────
    manifest = upstream / "lawnchair/AndroidManifest.xml"
    if not manifest.is_file():
        sys.exit(f"ERROR: expected file missing: {manifest}")
    edit_file(
        manifest,
        edits=[
            (
                '    <uses-permission android:name="android.permission.INTERNET" />\n',
                '    <uses-permission android:name="android.permission.INTERNET" />\n'
                "    <!-- LunchHeir: install the bundled Lunch Heir Bridge companion -->\n"
                '    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n',
            ),
        ],
        applied_marker="REQUEST_INSTALL_PACKAGES",
    )

    # ── Groups: load + render (additive, dormant until a group row exists) ──────
    # Route ITEM_TYPE_GROUP rows through the folder/app-pair processor, upgrade the placeholder
    # to a GroupInfo (keeping its multi-cell span), and inflate a GroupView for it. All branches
    # are new (only fire for the new type), so existing item loading is unchanged.
    wip = upstream / "src/com/android/launcher3/model/WorkspaceItemProcessor.kt"
    if not wip.is_file():
        sys.exit(f"ERROR: expected file missing: {wip}")
    edit_file(
        wip,
        edits=[
            (
                "                Favorites.ITEM_TYPE_FOLDER,\n"
                "                Favorites.ITEM_TYPE_APP_PAIR -> processFolderOrAppPair()\n",
                "                Favorites.ITEM_TYPE_FOLDER,\n"
                "                Favorites.ITEM_TYPE_APP_PAIR -> processFolderOrAppPair()\n"
                "                app.lawnchair.lunchheir.group.GroupInfo.ITEM_TYPE_GROUP -> processFolderOrAppPair()\n",
            ),
            (
                "        c.applyCommonProperties(collection)\n"
                "        // Do not trim the folder label, as is was set by the user.\n"
                "        collection.title = c.getString(c.mTitleIndex)\n"
                "        collection.spanX = 1\n"
                "        collection.spanY = 1\n",
                "        // LunchHeir: upgrade the placeholder Folder to a Group, which renders inline and keeps\n"
                "        // its multi-cell span (unlike folders/app-pairs, forced to 1x1 below).\n"
                "        if (c.itemType == app.lawnchair.lunchheir.group.GroupInfo.ITEM_TYPE_GROUP && collection is FolderInfo) {\n"
                "            val newGroup = app.lawnchair.lunchheir.group.GroupInfo()\n"
                "            collection.getContents().forEach(newGroup::add)\n"
                "            collection = newGroup\n"
                "        }\n"
                "\n"
                "        c.applyCommonProperties(collection)\n"
                "        // Do not trim the folder label, as is was set by the user.\n"
                "        collection.title = c.getString(c.mTitleIndex)\n"
                "        if (collection !is app.lawnchair.lunchheir.group.GroupInfo) {\n"
                "            collection.spanX = 1\n"
                "            collection.spanY = 1\n"
                "        }\n",
            ),
        ],
        applied_marker="app.lawnchair.lunchheir.group.GroupInfo",
    )

    inflater = upstream / "src/com/android/launcher3/util/ItemInflater.kt"
    if not inflater.is_file():
        sys.exit(f"ERROR: expected file missing: {inflater}")
    edit_file(
        inflater,
        edits=[
            (
                '            else -> throw RuntimeException("Invalid Item Type")\n',
                "            app.lawnchair.lunchheir.group.GroupInfo.ITEM_TYPE_GROUP ->\n"
                "                return app.lawnchair.lunchheir.group.GroupView.inflate(\n"
                "                    context,\n"
                "                    parent,\n"
                "                    item as app.lawnchair.lunchheir.group.GroupInfo,\n"
                "                ).apply {\n"
                "                    onFocusChangeListener = focusListener\n"
                "                    // LunchHeir: drag/reorder the group as a unit, like any workspace item\n"
                "                    setOnLongClickListener(com.android.launcher3.touch.ItemLongClickListener.INSTANCE_WORKSPACE)\n"
                "                }\n"
                '            else -> throw RuntimeException("Invalid Item Type")\n',
            ),
        ],
        applied_marker="app.lawnchair.lunchheir.group.GroupView",
    )

    # ── Nested folders: let a folder be dropped into a folder (gated, opt-in) ────
    # FolderInfo.willAcceptItemType decides what a folder accepts (drag-drop, and FolderInfo.add);
    # it excludes folders. Accept ITEM_TYPE_FOLDER when nesting is on, reusing all of Launcher3's
    # existing drag-to-folder machinery for creation. Context-free static -> read the cached flag.
    folderinfo = upstream / "src/com/android/launcher3/model/data/FolderInfo.java"
    if not folderinfo.is_file():
        sys.exit(f"ERROR: expected file missing: {folderinfo}")
    edit_file(
        folderinfo,
        edits=[
            (
                "                || itemType == ITEM_TYPE_APP_PAIR;\n",
                "                || itemType == ITEM_TYPE_APP_PAIR\n"
                "                // LunchHeir: accept a folder into a folder when nesting is enabled (opt-in)\n"
                "                || (itemType == com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER\n"
                "                    && app.lawnchair.lunchheir.folder.NestedFolders.isAccepting());\n",
            ),
        ],
        applied_marker="app.lawnchair.lunchheir.folder.NestedFolders.isAccepting",
    )

    # ── Nested folders: cycle/depth guard on folder-into-folder drops ───────────
    # FolderIcon.willAcceptItem has both the target folder (mInfo) and the dragged item, so it's the
    # place to refuse a drop that would create a cycle (which renders forever) or nest too deep.
    foldericon = upstream / "src/com/android/launcher3/folder/FolderIcon.java"
    if not foldericon.is_file():
        sys.exit(f"ERROR: expected file missing: {foldericon}")
    edit_file(
        foldericon,
        edits=[
            (
                "        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());\n",
                "        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen()\n"
                "                // LunchHeir: refuse folder-into-folder drops that would cycle or over-nest\n"
                "                && app.lawnchair.lunchheir.folder.NestedFolders.canDrop(mInfo, item));\n",
            ),
        ],
        applied_marker="app.lawnchair.lunchheir.folder.NestedFolders.canDrop",
    )

    # ── Nested folders: render a sub-folder as a FolderIcon inside its parent ────
    # FolderPagedView.createNewView casts every non-app-pair child to WorkspaceItemInfo, so a folder
    # child would crash. Add a FolderInfo branch that inflates a real FolderIcon. Safe always-on:
    # with nesting off no folder child exists, and handling one beats crashing.
    folderpaged = upstream / "src/com/android/launcher3/folder/FolderPagedView.java"
    if not folderpaged.is_file():
        sys.exit(f"ERROR: expected file missing: {folderpaged}")
    edit_file(
        folderpaged,
        edits=[
            (
                "                    getContext()), null , api, BubbleTextView.DISPLAY_FOLDER);\n"
                "        } else {\n",
                "                    getContext()), null , api, BubbleTextView.DISPLAY_FOLDER);\n"
                "        } else if (item instanceof com.android.launcher3.model.data.FolderInfo lhFolder) {\n"
                "            // LunchHeir: a nested folder renders as a folder icon inside its parent folder\n"
                "            icon = FolderIcon.inflateIcon(R.layout.folder_icon, mFolder.mActivityContext, null, lhFolder);\n"
                "        } else {\n",
            ),
        ],
        applied_marker="LunchHeir: a nested folder renders as a folder icon",
    )

    # ── Nested folders: allow a folder as a child of a collection (gated, opt-in) ─
    # checkAndAddItem only attaches app/shortcut/app-pair children to their container; folders are
    # excluded, which blocks nesting. Add ITEM_TYPE_FOLDER, gated by NestedFolders.isEnabled so the
    # default (toggle off) is byte-for-byte upstream behavior. mContext is LoaderCursor's Context.
    loadercursor = upstream / "src/com/android/launcher3/model/LoaderCursor.java"
    if not loadercursor.is_file():
        sys.exit(f"ERROR: expected file missing: {loadercursor}")
    edit_file(
        loadercursor,
        edits=[
            (
                "                    || info.itemType == ITEM_TYPE_APPLICATION)\n",
                "                    || info.itemType == ITEM_TYPE_APPLICATION\n"
                "                    // LunchHeir: allow a folder inside a folder when nesting is enabled (opt-in)\n"
                "                    || (info.itemType == com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER\n"
                "                        && app.lawnchair.lunchheir.folder.NestedFolders.isEnabled(mContext)))\n",
            ),
        ],
        applied_marker="app.lawnchair.lunchheir.folder.NestedFolders",
    )

    # ── Groups: create by promoting a folder ────────────────────────────────────
    # Long-press an open folder's label to convert it into a Lunch Heir group. One line into the
    # folder label setup; all logic lives in the overlay (GroupPromotion), gated by the GROUPS
    # toggle. mInfo is the folder's FolderInfo; getContext() is the launcher (Folder is a View).
    folder = upstream / "src/com/android/launcher3/folder/Folder.java"
    if not folder.is_file():
        sys.exit(f"ERROR: expected file missing: {folder}")
    edit_file(
        folder,
        edits=[
            (
                "        mFolderName.setOnBackKeyListener(this);\n"
                "        mFolderName.setOnEditorActionListener(this);\n"
                "        mFolderName.setSelectAllOnFocus(true);\n",
                "        mFolderName.setOnBackKeyListener(this);\n"
                "        mFolderName.setOnEditorActionListener(this);\n"
                "        mFolderName.setSelectAllOnFocus(true);\n"
                "        // LunchHeir: long-press the folder label to convert the folder into a group\n"
                "        mFolderName.setOnLongClickListener(v ->\n"
                "                app.lawnchair.lunchheir.group.GroupPromotion.onFolderLabelLongPress(getContext(), mInfo));\n",
            ),
        ],
        applied_marker="GroupPromotion.onFolderLabelLongPress",
    )

    # ── Apply the Lunch Heir Gradle overlay ─────────────────────────────────────
    # Append one line to the app build script so Lunch Heir branding (applicationId,
    # label) and overlay source dirs are configured in the normal config phase. This
    # is the primary mechanism; the build no longer needs --init-script.
    build_gradle = upstream / "build.gradle"
    if not build_gradle.is_file():
        sys.exit(f"ERROR: expected file missing: {build_gradle}")
    append_once(
        build_gradle,
        marker="lunchheir-overlay.gradle",
        text_to_append=(
            "\n// LunchHeir: apply the Lunch Heir overlay (branding + overlay source dirs)\n"
            'apply from: "$rootDir/../overlay/lunchheir-overlay.gradle"\n'
        ),
    )

    print("Done.")


if __name__ == "__main__":
    main()
