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
