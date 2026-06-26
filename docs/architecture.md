# Architecture

Lunch Heir is an **overlay** on upstream Lawnchair, not a fork of it. This keeps the upstream tree
pristine so tracking Lawnchair stays cheap.

## The two halves

```
upstream/   git submodule -> LawnchairLauncher/lawnchair, tracking 16-dev. Never edited in git.
overlay/    everything Lunch Heir: Gradle config, source edits, and feature source.
```

The build runs with **`upstream/` as the Gradle root**. Nothing at the repo root is a Gradle project.

## How the overlay is applied

`overlay/apply-overlay.sh` runs `overlay/apply_overlay.py <upstream-dir>` at build time. It does two
kinds of thing:

1. **Exact-anchor source edits** to the submodule working tree (the "seams"). Each edit matches an
   exact substring of an upstream file and is **idempotent** (re-running is a no-op) and **loud on
   drift** (if upstream changed an anchored line, the script aborts with the anchor, so the seam is
   re-authored deliberately instead of silently mis-applied).
2. **Appends one line** to `upstream/build.gradle`:
   `apply from: "$rootDir/../overlay/lunchheir-overlay.gradle"`.

The submodule's working tree is dirtied at build time, but `.gitmodules` sets `ignore = dirty` so the
parent repo stays clean; only the submodule **pointer** changes are ever committed.

### The seams (in `apply_overlay.py`)

| Seam | File patched | Why |
|---|---|---|
| Backup compatibility | `LawnchairBackup.kt` | write `lunchheir_info.pb`; read both — one-directional compat |
| Home-screen hook | `LawnchairLauncher.kt` | one line into `onCreate` → `LunchHeirHome.onCreate(this)` |
| Feed bridge trust | `FeedBridge.kt` | prefer + trust the bundled bridge by signature |
| Bridge install permission | `AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES` |
| Live Panel picker activity | `AndroidManifest.xml` | declare `LivePanelWidgetPickerActivity` |
| Groups: load + render | `WorkspaceItemProcessor.kt`, `ItemInflater.kt` | route `ITEM_TYPE_GROUP`, inflate `GroupView` (drag-enabled) |
| Groups: create | `Folder.java` | long-press folder label → `GroupPromotion.onFolderLabelLongPress` |
| Nested folders: accept | `FolderInfo.java` | accept a folder dropped into a folder (gated) |
| Nested folders: load | `LoaderCursor.java` | attach a folder child to its container (gated) |
| Nested folders: guard | `FolderIcon.java` | refuse cycle / over-deep folder drops |
| Nested folders: render | `FolderPagedView.java` | a sub-folder renders as a `FolderIcon` |
| Disable config cache | `gradle.properties` | so the per-compile version bump re-runs every build |
| Apply overlay | `build.gradle` | the `apply from:` line |

All anchors are pinned to the upstream commit the submodule points at; a bump may move one, which is
exactly what `update-upstream.yml` (and a local re-apply) surfaces.

### The Gradle overlay (`lunchheir-overlay.gradle`)

Applied via `apply from:` into Lawnchair's `build.gradle.kts`. It is **Groovy** (not `.gradle.kts`)
on purpose: an applied script plugin reaches the host's `android { }`, `signingConfigs`, `licensee
{ }`, and `dependencies { }` extensions by dynamic dispatch; Kotlin DSL applied scripts get no
type-safe accessors for those, forcing verbose `configure<…>()` with AGP-internal type imports. It:

- computes and stamps the **version** (see below),
- attaches `overlay/src` to the app's `main` source set (java + kotlin),
- sets the per-flavor `applicationId`s and the derived app label,
- configures **release-only signing** (debug builds keep the debug key),
- bundles the Lunch Heir Bridge APK as an asset when present,
- adds the AzNavRail dependency (and allow-lists it for the `licensee` plugin).

## Feature code (`overlay/src`)

All Lunch Heir source is under `overlay/src/app/lawnchair/lunchheir/`:

- root — home wiring (`LunchHeirHome`), recents bar, second hotseat, Hax shell/system/settings, Live
  Panel (+ host + picker activity), prefs, bridge installer.
- `group/` — Groups: `GroupInfo`, `GroupView`, `GroupPromotion`, `GroupAppMonitor`,
  `SmartGroupSeeder`, `SmartGroupRegistry`.
- `folder/` — `NestedFolders` (the nesting gate + cycle/depth guard).
- `smartfill/` — the smart auto-fill engine, app signals, installed-apps source, config, and the
  provider-agnostic cloud refiner.
- `theme/` — `MonochromeShell`.

**No Room.** All Lunch Heir metadata (feature toggles, smart-group registry, smart-fill provider
config) lives in private `SharedPreferences`, keeping the whole thing overlay-contained with no
upstream schema patch.

The single launcher entry point is `LunchHeirHome.onCreate(launcher)` (the one home-hook seam); every
feature gates itself there on its own `LunchHeirPrefs.Feature` toggle.

## Versioning

`version.properties` (repo root) is the single source of truth:

```
versionMajor   manual — project owner only
versionMinor   manual — maintainer (e.g. per release)
versionPatch   +1 every compile; resets to 0 when versionMinor changes
versionBuild   +1 every compile; never resets (also floored at the git commit count)
```

`lunchheir-overlay.gradle` reads, bumps, and rewrites this at **configuration time**, then forces
`versionName = major.minor.patch.build` and `versionCode = build` onto every variant. Because this
runs at configuration time on every build, the upstream **configuration cache is disabled** for the
overlaid build (a cached configuration wouldn't re-run the bump, and config-time file writes are
incompatible with it). `versionBuild` is floored at the git commit count so it stays monotonic across
CI runs (which don't commit the bumped file back) without a commit-back loop.

## Submodule auto-update

`update-upstream.yml` keeps `upstream/` on the latest `16-dev`: bump → re-apply overlay → build. On
green it commits the new pointer to `main`; on red (a drifted seam or a build break) it files an
issue tasking Jules. So upstream tracking is automatic, and a breaking upstream change becomes a
ticket instead of a broken `main`.
