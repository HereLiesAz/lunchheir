# Groups (and Smart auto-fill)

## What a Group is
A **Group** is like a folder, but it **never collapses**. It renders all of its children inline
on a workspace page, spans multiple cells (up to a whole page), moves as a single unit, and
**auto-accepts** newly installed apps when it has free space. The point: you can move a whole
cluster of apps together without hiding them inside an icon, and the group keeps catching new
installs (insecure/dumb on purpose — you remove what you don't want).

## Data model
- New `GroupInfo extends CollectionInfo` (mirrors `FolderInfo`, minus the collapse/icon-preview).
- New container id `CONTAINER_GROUP = -201` (upstream reserves the `EXTENDED_CONTAINERS` region
  ≤ -200 for non-AOSP variants) and item type `ITEM_TYPE_GROUP = 100` (well clear of upstream's
  0..11) — chosen to survive upstream additions.
- The **group row** is a normal Favorites desktop row: `itemType=ITEM_TYPE_GROUP`,
  `container=DESKTOP`, `screen/cellX/cellY` = position, `spanX/spanY` = cells it occupies.
- **Children** are normal rows with `container = <group row id>` and `cellX/cellY` *relative* to
  the group's internal grid. Moving the group row moves only that row; children ride along with
  zero per-child writes (absolute position = group origin + relative offset, never stored).
- Group metadata that doesn't fit Favorites (auto-accept flag, internal dims, smart-fill config)
  lives in a new Room table in Lawnchair's `AppDatabase` — the merge-safe side-store Lawnchair
  already uses for folders.

## Core seams (minimal, tracked patches via `overlay/apply_overlay.py`)
- `LauncherSettings.java` — add the container + itemType constants (and `containerToString`).
- `LoaderCursor.findOrMakeFolder` / `checkAndAddItem` — make the right `CollectionInfo` subtype
  (group vs folder) and route children in. This is the linchpin; mirror the app-pair
  placeholder-upgrade pattern.
- The bind path — add a "bind a `GroupView`" branch (inflate an inline `CellLayout`-based view
  into the workspace page rather than a `FolderIcon`).
- `ItemInstallQueue` / `WorkspaceItemSpaceFinder` — divert a newly installed app into a Group with
  free space before falling through to normal placement.
Everything else (GroupInfo, GroupView, the Room table, auto-place logic) is new overlay code.

## Build order (each CI-green; the core ones need on-device testing)
1. **Inert foundation** — `GroupInfo`, the constants, the Room metadata table. Compiles, unused,
   changes no behavior.
2. **Load + render** — loader routing + a `GroupView` bound inline on the page (read-only).
3. **Edit** — drag the group as a unit; drag children in/out; reorder; persist.
4. **Auto-accept** — new installs land in a Group with free space.

## Smart auto-fill (AI) — "Smart Group / Smart Folder"
Applies to **both folders and groups**. The title and the app set are **mutually- and
self-informing**, and re-evaluated **continuously** (not one-shot) as the group's state changes:
- **apps → title** and **title → title:** the AI proposes a best-guess title from the apps, and
  re-derives / refines that title as membership changes.
- **title → apps** and **apps → apps:** a (user- or AI-set) title steers population (e.g. "Adobe"
  pulls Adobe apps; "Photo editing" pulls photo editors), and the current membership re-informs
  which other installed apps belong — the set self-reinforces (its own apps imply more apps).

Either or both may be user-provided; the AI fills in and keeps refining whichever side is missing
or stale. Net: a living group whose title and contents keep re-deriving from its own evolving
state (seed, then new installs, then user edits, then re-evaluation).

### Backend (open decision — needs user input)
- **On-device heuristics** (PackageManager `ApplicationInfo.category`, installer/developer, name
  similarity): free, fully private; good for type/developer, weak on "purpose" and on
  title↔apps semantic reasoning.
- **Cloud LLM (e.g. Claude API)**: strong at purpose/semantic grouping and naming, but sends the
  installed-app list off-device and needs an API key.
- Likely shape: **hybrid** — on-device baseline always available; optional LLM for purpose/title
  reasoning, behind a privacy/consent toggle. API-key sourcing TBD.

Build after the Group container exists.
