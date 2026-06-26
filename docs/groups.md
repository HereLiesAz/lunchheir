# Groups

A **Group** is like a folder, but it **never collapses**. It renders all of its children inline on a
workspace page, spans multiple cells (up to a whole page), moves as a single unit, and **auto-accepts**
newly installed apps. A **smart** group additionally pulls in apps that fit its pattern and titles
itself, continuously. The point: move a whole cluster of apps together without hiding them inside an
icon, and (for smart groups) keep them coherent as the device changes.

Gated by the `GROUPS` toggle (default on). All group code is overlay code under
`overlay/src/app/lawnchair/lunchheir/group/`; the only upstream edits are the small seams below.

## Data model (`GroupInfo`)

- `GroupInfo extends CollectionInfo` (mirrors `FolderInfo`, minus collapse/icon-preview). It holds an
  ordered list of children and exposes `getAppContents()` / `getContents()`.
- `ITEM_TYPE_GROUP = 100` (well clear of upstream's `0..11`) and `CONTAINER_GROUP = -201` (upstream
  reserves the `EXTENDED_CONTAINERS` region `≤ -200` for non-AOSP variants) — chosen to survive
  upstream additions.
- The **group row** is a normal Favorites desktop row: `itemType=ITEM_TYPE_GROUP`,
  `container=DESKTOP`, `screen/cellX/cellY` = position, `spanX/spanY` = cells occupied.
- **Children** are normal rows with `container = <group row id>` and relative `cellX/cellY`. Moving
  the group row moves only that row; children ride along (absolute = origin + relative offset).
- Group metadata that doesn't fit Favorites (which groups are *smart*) lives in **SharedPreferences**
  (`SmartGroupRegistry`) — overlay-contained, no upstream schema patch. (There is no Room table.)

## Upstream seams (in `apply_overlay.py`)

- `WorkspaceItemProcessor.kt` — route `ITEM_TYPE_GROUP` through the folder/app-pair processor and
  upgrade the placeholder to a `GroupInfo`, keeping its multi-cell span (mirrors the app-pair
  placeholder-upgrade pattern).
- `ItemInflater.kt` — a "bind a `GroupView`" branch that inflates an inline view onto the workspace
  page (not a `FolderIcon`), wired to the workspace long-click/drag listener.
- `Folder.java` — long-press an open folder's label → `GroupPromotion.onFolderLabelLongPress`.

Everything else (`GroupInfo`, `GroupView`, promotion, the monitor, smart seeding) is overlay code.

## Creating a group — promote a folder

A group is created by converting an existing Lawnchair folder (reuse folder creation, then promote).
Long-pressing an open folder's label offers **Smart group** / **Plain group** / Cancel
(`GroupPromotion`):

- `convert(launcher, folder)` — the model surgery via `ModelWriter`: create a group row in the
  folder's cell sized to its children, reparent each child into the group with relative cells, delete
  the empty folder row. Touches only `ModelWriter` (no loader internals) → additive / rebase-safe.
- **Plain group** reloads and you're done.
- **Smart group** also registers the group as smart (`SmartGroupRegistry`) and runs
  `SmartGroupSeeder.fill` off the main thread: the group's apps seed `SmartFill.suggest`, matching
  installed apps are minted as workspace items (`AppInfo.makeWorkspaceItem`) and added, the group is
  resized to fit, and the suggested title is applied — then one model reload.

## Rendering & dragging (`GroupView`)

`GroupView` lays the group's child icons out in an internal grid (columns follow `spanX`), drawing
each from its loaded icon bitmap. The group **drags as a unit**: `ItemInflater` wires it to the
workspace drag listener, and each child icon forwards its long-press to the group, so long-pressing
anywhere picks up the whole group; the drop persists through the standard `moveItemInDatabase` path. A
tap on an icon still launches it.

## Keeping groups current (`GroupAppMonitor`)

A `LauncherApps.Callback`, registered while home is active, reacts to **new installs**:

- **Smart groups** are re-seeded through `SmartGroupSeeder` (pattern match pulls the new app in if it
  fits, and re-derives the title).
- **Plain groups** — the first one with a free cell **auto-accepts** the new app (the "dumb"
  behavior; you remove what you don't want).

One model reload covers both.

## Smart auto-fill

The AI that powers smart groups (and applies equally to smart folders) is documented separately in
[`smart-fill.md`](smart-fill.md): an always-on on-device heuristic engine plus an optional,
provider-agnostic cloud LLM refiner.

## Follow-ups (want on-device iteration)

- Drag-outline / neighbour-wiggle polish (`DraggableView` / `Reorderable`) for groups.
- Dragging individual children in/out of a group as a distinct gesture.
