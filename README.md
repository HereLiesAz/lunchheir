# Lunch Heir

**Lunch Heir** (`com.hereliesaz.lunchheir`) is an Android launcher built on
[Lawnchair 16](https://github.com/LawnchairLauncher/lawnchair) (a Launcher3 derivative).

It is designed to **track upstream Lawnchair with minimal effort**: upstream lives untouched
in a pinned git submodule, and all Lunch Heir customization lives in an `overlay/` that is
layered on at build time. Updating Lawnchair is (mostly) a submodule pointer bump.

Lunch Heir is GPLv3, like Lawnchair — see `LICENSE.txt`.

## Repository layout

```
.
├── upstream/        git submodule -> LawnchairLauncher/lawnchair (PRISTINE, pinned)
├── overlay/         all Lunch Heir code & configuration (layered onto upstream)
│   ├── lunchheir.init.gradle     Gradle init script: applicationId, label, source dirs
│   ├── lunchheir-overlay.gradle  fallback overlay (apply-line) if the init script needs it
│   ├── apply_overlay.py          source-level edits to the submodule (backup compatibility)
│   ├── apply-overlay.sh          wrapper for apply_overlay.py
│   └── src/                      Lunch Heir feature source (used by later phases)
├── build-lunchheir.sh   one-shot: init submodule, apply overlay, build with the overlay
├── .gitmodules
└── LICENSE.txt
```

The build runs with **upstream as the Gradle root**; the overlay is injected via
`--init-script`, so no upstream file is edited. The only source-level change that can't be
expressed in Gradle (backup format) is applied to the submodule working tree at build time by
`overlay/apply_overlay.py` — idempotent, and loud if upstream drifts. The submodule stays
pristine in git.

## Build

```bash
git submodule update --init --recursive   # fetch upstream Lawnchair + its nested submodule
./build-lunchheir.sh                       # apply overlay + assemble the github debug APK
```

`build-lunchheir.sh` is equivalent to:

```bash
overlay/apply-overlay.sh
cd upstream && ./gradlew --init-script ../overlay/lunchheir.init.gradle \
    assembleLawnWithQuickstepGithubDebug
```

Requires JDK 21 and the Android SDK (as upstream Lawnchair expects).

> **Status:** the overlay/submodule scaffolding has not yet been built end-to-end in CI. The
> Gradle injection (`androidComponents`/DSL timing) and the backup overlay are best-effort and
> may need a small adjustment on the first real build. If the init script misbehaves, switch to
> the documented fallback: append one line to `upstream/build.gradle` —
> `apply from: "$rootDir/../overlay/lunchheir-overlay.gradle"`.

## Updating Lawnchair

```bash
cd upstream
git fetch origin
git checkout <new-lawnchair-commit-or-tag>
cd ..
git add upstream
overlay/apply-overlay.sh        # re-apply source overlay; fails loudly if anchors moved
./build-lunchheir.sh            # rebuild & verify
git commit -m "chore: bump upstream Lawnchair"
```

The submodule is currently pinned at Lawnchair commit
`de7f02acf1c0228edf4b561b4504149cf986bc05` (the tree this repo was forked from).

## Backup compatibility

- Lunch Heir **restores Lawnchair `.lawnchairbackup` files** (it reads the `info.pb` entry).
- Lunch Heir **writes `.lunchheirbackup` files** whose metadata lives in a `lunchheir_info.pb`
  entry. Stock Lawnchair only looks for `info.pb`, so it **cannot restore a Lunch Heir backup**.

## Roadmap

The first milestone (this repo's current state) is the **Foundation**: rebranded, buildable
Lunch Heir on the submodule + overlay architecture, with one-directional backup compatibility.
Planned later phases, layered through the same overlay:

1. Dual persistent rows + a live recent-apps bar (swipe-up to dismiss; replaces system recents).
2. "Hax"-style start menu + flat monochrome theme + Live Panel animated widgets.
3. Sections — inline, never-collapsing collections that auto-accept new installs.
4. Folders within folders.
