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

## Creating a group (decided: **promote a folder**)
A group is created by converting an existing Lawnchair folder — reuse folder creation, then add a
**"Convert to group"** action to the folder's options menu. Two parts:
- `GroupPromotion.promote(launcher, folder)` (overlay) — the model surgery via `ModelWriter`: create
  a group row in the folder's cell sized to its children, reparent each child into the group with
  relative cells, delete the empty folder row, force a model reload. Touches only `ModelWriter`
  (no loader internals) → additive/rebase-safe.
- The menu entry — a one-line `apply_overlay.py` seam: long-pressing an open folder's label calls
  `GroupPromotion.onFolderLabelLongPress`, which offers **Smart group** / **Plain group** / Cancel.
- **Smart group** runs `SmartGroupSeeder.fill` after the conversion: the group's apps seed
  `SmartFill.suggest`, matching installed apps are minted as workspace items (`AppInfo.makeWorkspaceItem`)
  and added, the group is resized to fit, and the suggested title applied — all off the main thread,
  then one model reload. (Continuous re-evaluation on later installs/edits is the next step.)

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

### Backend (decided: **hybrid**)
- **On-device heuristics** — the always-on engine. Free, fully private, offline. Scores candidate
  apps against the current membership + title on four signals: `ApplicationInfo.category` match,
  shared installer package (developer proxy), title-keyword overlap, and label-token similarity to
  existing members. Drives the continuous self-reinforcing loop (members → title → more members).
  Good for type/developer; weaker on abstract "purpose".
- **Cloud LLM (provider-agnostic)** — an *optional* refiner, off by default, gated behind a
  per-feature consent toggle (`LunchHeirPrefs`) **and** a user-provided API key. **Any AI backend**
  is usable, not just Claude: the refiner is built around a pluggable `AiProvider` (configurable
  base URL + model + key + wire format). The OpenAI-compatible chat-completions shape is the default
  lingua franca (covers OpenAI, Gemini's compat endpoint, Groq, Together, local Ollama / LM Studio,
  etc.); Anthropic's messages shape is a second built-in; "custom" lets the user point at any
  endpoint. Adding a provider is configuration, not new code. When enabled it refines the
  pattern/title for purpose-based grouping; when absent the on-device engine stands alone. It sends
  the installed-app inventory (labels/packages/categories) off-device, hence the explicit opt-in.

### Build order (each CI-green; dormant until wired)
1. **Engine** — `SmartFillEngine` (pure Kotlin): `evaluate()` one-pass scorer + `suggestTitle()` +
   `converge()` self-reinforcing loop. No Android coupling beyond category ints. Instantiated
   nowhere yet → changes no behavior.
2. **Adapter** — build `AppSignals` from Lawnchair's app/model data; run `converge()` continuously
   (on install/uninstall and on membership/title edits) for a Smart Group/Folder.
3. **Remote refiner** — optional `SmartFillRemote` behind the consent flag + key (`SmartFillConfig`),
   built on the pluggable `AiProvider` so any AI backend works (OpenAI-compatible default, Anthropic,
   custom). Dependency-free (`HttpURLConnection` + `org.json`); refines the on-device baseline and
   falls back to it silently on any error. `SmartFill.suggest` runs baseline → optional refine.

Remaining to make it user-visible: a Smart-group/folder create + settings surface that supplies the
seeds/title, stores the provider config, and runs `SmartFill.suggest` continuously (on install/edit).

Build after the Group container exists (it does — increment 2 merged).
