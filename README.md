# Lunch Heir

**Lunch Heir** (`com.hereliesaz.lunchheir`) is an Android launcher built on
[Lawnchair 16](https://github.com/LawnchairLauncher/lawnchair) (a Launcher3 derivative).

It is designed to **track upstream Lawnchair with minimal effort**: upstream lives untouched in a
git submodule, and every Lunch Heir customization lives in an `overlay/` that is layered on at build
time. Updating Lawnchair is (mostly) a submodule pointer bump — and a workflow does it automatically.

Lunch Heir is GPLv3, like Lawnchair — see `LICENSE.txt`.

## What Lunch Heir adds

Every feature is an independent toggle (`LunchHeirPrefs`); **turn them all off and you have plain
Lawnchair** — there is no master switch by design.

- **Live recents bar + second hotseat** — a swipe-to-dismiss recent-apps row and a second persistent
  hotseat row, in the DragLayer so they persist across home pages.
- **Hax shell** — a summoned, flat, typography-forward menu (built on the AzNavRail library), a
  system-actions sheet, a settings sheet (per-feature toggles + smart-fill provider config), and a
  global **monochrome** mode.
- **Live Panels** — a flat kinetic-typography panel that can host a **real app widget** (picked via a
  system widget-picker), falling back to an animated clock.
- **Groups** — folders that **never collapse**: they render their apps inline, move as a unit,
  **auto-accept** new installs, and a **smart** group continuously pulls in apps that fit and
  auto-titles itself.
- **Smart auto-fill** — an on-device heuristic engine plus an optional, provider-agnostic cloud LLM
  refiner (bring your own key — OpenAI-compatible, Anthropic, or any custom endpoint).
- **Nested folders** — a folder inside a folder (opt-in), with a cycle/depth guard.
- **Pixel-Bridge feed** — a bundled, self-signed feed-provider companion for the Discover feed.
- **One-directional backup compatibility** — reads Lawnchair backups; writes its own that stock
  Lawnchair can't read back.

See [`docs/features.md`](docs/features.md) for the full catalogue, toggles, and entry points.

## Repository layout

```
.
├── upstream/        git submodule -> LawnchairLauncher/lawnchair (PRISTINE, tracks 16-dev)
├── overlay/         all Lunch Heir code & configuration (layered onto upstream)
│   ├── lunchheir-overlay.gradle  Gradle overlay: version, applicationId, signing, source dirs, deps
│   ├── apply_overlay.py          exact-anchor source edits to the submodule (seams)
│   ├── apply-overlay.sh          wrapper for apply_overlay.py
│   └── src/                      Lunch Heir feature source (compiled into the app)
├── version.properties   single source of truth for the auto-incrementing version
├── build-lunchheir.sh   one-shot: apply overlay + build the github debug APK
├── docs/                architecture, features, build & release, groups, smart-fill
└── .github/workflows/   CI: debug build, release APK, Play, submodule auto-update
```

How the overlay works in one paragraph, with the full version in
[`docs/architecture.md`](docs/architecture.md): the build runs with **upstream as the Gradle root**.
`overlay/apply_overlay.py` makes a small set of **idempotent, exact-anchor** edits to the submodule
working tree at build time (a launcher hook, the backup format, the feed-bridge trust, and a handful
of one-line feature seams), then appends a single `apply from:` line that pulls in
`lunchheir-overlay.gradle` (version, identity, signing, `overlay/src`, deps). It is idempotent and
**fails loudly if upstream drifts**, and the submodule stays pristine in git.

## Build

```bash
git submodule update --init --recursive   # fetch upstream Lawnchair + its nested submodule
./build-lunchheir.sh                       # apply overlay + assemble the github debug APK
```

`build-lunchheir.sh` is equivalent to:

```bash
overlay/apply-overlay.sh
cd upstream && ./gradlew assembleLawnWithQuickstepGithubDebug
```

Requires JDK 21 and the Android SDK (as upstream Lawnchair expects). The APK lands in
`upstream/build/outputs/apk/`.

## Versioning, releases & CI

Versioning is owned by Gradle and advances on **every compile** — see
[`docs/build-and-release.md`](docs/build-and-release.md). In short, `version.properties` holds
`major.minor.patch.build`: you bump `major`, the maintainer bumps `minor`, and `patch`/`build`
auto-increment each build (`patch` resets on a `minor` bump, `build` never resets).

CI (all in `.github/workflows/`):

| Workflow | Trigger | Result |
|---|---|---|
| `merged_build.yml` | every commit | debug-keyed **debug pre-release** |
| `release-apk.yml` | `v*` tag / manual | **signed release APK** GitHub Release (cert verified) |
| `play-release.yml` | `v*` tag / manual | **signed AAB → Google Play** |
| `update-upstream.yml` | every 6h / manual | auto-bump the Lawnchair submodule (verified, or tasks Jules) |

## Updating Lawnchair

The submodule tracks Lawnchair's **`16-dev`** branch. `update-upstream.yml` bumps it automatically:
it moves to the latest commit, re-applies the overlay, builds, and on success commits the bump to
`main` — on failure it files an issue tasking Jules to re-author the drifted seam. To do it by hand:

```bash
git -C upstream fetch && git -C upstream checkout <new-lawnchair-commit>
overlay/apply-overlay.sh        # re-apply seams; fails loudly if anchors moved
./build-lunchheir.sh            # rebuild & verify
git add upstream && git commit -m "chore(upstream): bump Lawnchair submodule"
```

**Do not** use GitHub's "Sync fork" — this repo's `main` is the overlay tree, not Lawnchair's; sync
would merge Lawnchair's entire root tree into it. Update via the submodule only.

## Backup compatibility

- Lunch Heir **restores Lawnchair `.lawnchairbackup` files** (it reads the `info.pb` entry).
- Lunch Heir **writes `.lunchheirbackup` files** whose metadata lives in a `lunchheir_info.pb` entry.
  Stock Lawnchair only looks for `info.pb`, so it **cannot restore a Lunch Heir backup**.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — the overlay mechanism, seams, submodule, versioning.
- [`docs/features.md`](docs/features.md) — every feature, its toggle, and its entry point.
- [`docs/build-and-release.md`](docs/build-and-release.md) — workflows, versioning, signing, Play.
- [`docs/groups.md`](docs/groups.md) — Groups (inline collections) in depth.
- [`docs/smart-fill.md`](docs/smart-fill.md) — the smart auto-fill engine + any-AI cloud refiner.
