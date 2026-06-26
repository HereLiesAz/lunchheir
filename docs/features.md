# Features

Every feature gates on its own toggle in `LunchHeirPrefs.Feature`, checked at its entry point. With
**all toggles off, Lunch Heir is plain Lawnchair** — there is no master switch. Toggles are stored in
private `SharedPreferences`; the settings sheet (Hax shell → TWEAKS) flips them.

## Toggles

| Toggle (`Feature`) | Key | Default | What it gates |
|---|---|---|---|
| `LIVE_RECENTS_BAR` | `live_recents_bar` | **on** | the swipe-to-dismiss recent-apps bar |
| `SECOND_ROW` | `second_row` | **on** | the second persistent hotseat row |
| `HAX_MENU` | `hax_menu` | **on** | the Hax menu button + shell |
| `GROUPS` | `groups` | **on** | group creation, drag, auto-accept, smart-fill |
| `LIVE_PANEL` | `live_panel` | off | the Live Panel surface |
| `NESTED_FOLDERS` | `nested_folders` | off | folder-inside-a-folder |
| `MONOCHROME` | `monochrome` | off | global grayscale UI |

Defaults: features that are safe and additive default **on**; surfaces that overlap placement or
change folder behavior default **off** (opt-in) until tuned on-device.

## Home surfaces

All attached in `LunchHeirHome.onCreate`, in the DragLayer so they persist across home pages.

- **Live recents bar** (`LiveRecentsBar`) — a live recent-apps row fed by QuickStep's `RecentsModel`;
  swipe a task up to dismiss. Listens only while home is visible (no background `getTasks` IPC).
- **Second hotseat row** (`SecondHotseatRow`) — a second persistent app row above the hotseat.

## Hax shell

The "Hax"-style launcher shell — flat, monotone, typography-forward — built on the **AzNavRail**
(`com.github.HereLiesAz:AzNavRail`) library, hosted in Lawnchair's `ComposeBottomSheet`.

- **Menu** (`HaxShell`) — a menu button (bottom-start) summons sections: APPS, SEARCH, SETTINGS,
  SYSTEM, TWEAKS, ADD PANEL — each a kinetic AzNavRail entry wired to the real launcher action.
- **System** (`HaxSystem`) — a system-actions sheet.
- **Settings** (`LunchHeirSettings`, the TWEAKS sheet) — flips every feature toggle and configures
  the smart-fill AI provider (enable, base URL, model, key, wire format).
- **Monochrome** (`MonochromeShell`) — renders the whole launcher UI grayscale via a saturation-0
  color filter on a hardware layer over the DragLayer. Reversible; wallpaper (a separate window)
  stays in colour.
- **Kinetic type** — the menu entries and the Live Panel clock animate in (spring scale + fade).

## Live Panels

A flat kinetic-typography panel (`LivePanelView`) for the home screen.

- With **no widget bound**, it shows an animated, battery-friendly clock (re-renders only on the
  minute change).
- **ADD PANEL** in the Hax menu opens `LivePanelWidgetPickerActivity` (declared via the manifest
  seam), which runs the system widget **pick → bind → configure** flow and stores the chosen id.
- The panel then **hosts that real app widget** (`LivePanelHost`), reusing the launcher's own running
  `AppWidgetHost` so it updates live. Falls back to the clock if the widget is gone.

## Groups

Folders that never collapse — see [`groups.md`](groups.md) for the full design.

- **Create** by long-pressing an open folder's label → **Plain group** or **Smart group**
  (`GroupPromotion`). The folder is converted to a `GroupInfo` in place via `ModelWriter`.
- **Render** inline on the workspace (`GroupView`), spanning multiple cells.
- **Drag** the whole group as a unit (standard Launcher3 drag; each child icon forwards its
  long-press to the group).
- **Auto-accept** — on a new install, the first plain group with a free cell absorbs it
  (`GroupAppMonitor`).
- **Smart groups** re-seed continuously: on each install, matching installed apps are added and the
  title re-derived (`SmartGroupSeeder`, gated by `SmartGroupRegistry`).

## Smart auto-fill

The AI behind smart groups/folders — see [`smart-fill.md`](smart-fill.md).

- **On-device engine** (`SmartFillEngine`) — always-on, free, private, offline heuristic scorer +
  title suggester + self-reinforcing convergence loop.
- **Cloud refiner** (`SmartFillRemote`, `AiProvider`) — optional, off by default, behind a consent
  toggle **and** a user key. Provider-agnostic: OpenAI-compatible (default), Anthropic, or any custom
  endpoint. Refines the on-device baseline; falls back to it silently on any error.

## Nested folders

A folder inside a folder (opt-in, `NESTED_FOLDERS`). Built by relaxing four upstream gates (load,
accept, render, and a cycle/depth guard) via `apply_overlay.py`, all no-ops when the toggle is off.
Created by dragging a folder onto another folder (reuses Launcher3's drag-to-folder machinery).

## Pixel-Bridge feed

The Discover feed needs a feed provider, and Google only serves it to *debuggable* apps — so the
provider is a **separate companion app** (`com.hereliesaz.lunchheir.bridge`), not part of the release
launcher. Lunch Heir does **not** bundle or install it (that would be hostile and a Play-policy
risk): `LunchHeirBridge.openDownloadPage` guides the user to **download and install it themselves**
(published by `bridge.yml` as the `bridge-latest` release, signed with the launcher's key). Once
installed, the patched `FeedBridge` prefers and trusts it by **signature match** (no hard-coded hash).

## Backup compatibility

Reads Lawnchair `.lawnchairbackup` (the `info.pb` entry) and writes `.lunchheirbackup` (a
`lunchheir_info.pb` entry stock Lawnchair can't read). See the README.
