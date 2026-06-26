# Build & Release

## Local build

```bash
git submodule update --init --recursive
./build-lunchheir.sh        # = overlay/apply-overlay.sh && (cd upstream && ./gradlew assembleLawnWithQuickstepGithubDebug)
```

Needs JDK 21 + Android SDK. The APK is at `upstream/build/outputs/apk/`. Every build also bumps the
version (below), so `version.properties` will show as modified afterward — that is expected.

## Versioning

`version.properties` (repo root) is the single source of truth. The version **name** is
`versionMajor.versionMinor.versionPatch.versionBuild`:

| Field | Who bumps it | Reset |
|---|---|---|
| `versionMajor` | project owner (manual) | never |
| `versionMinor` | maintainer (manual, e.g. per release) | never |
| `versionPatch` | the build, **+1 every compile** | resets to **0** when `versionMinor` changes |
| `versionBuild` | the build, **+1 every compile** | **never** resets |

`overlay/lunchheir-overlay.gradle` reads → bumps → rewrites `version.properties` at configuration
time, on every build, for every flavor/build-type — so the counters advance no matter where or how
the app is compiled, not just in CI. It then forces `versionName = major.minor.patch.build` and
`versionCode = versionBuild` onto every variant output.

Two consequences worth knowing:

- The upstream **configuration cache is disabled** for the overlaid build (`apply_overlay.py` sets
  `org.gradle.configuration-cache=false`). A cached configuration wouldn't re-run the per-compile
  bump, and config-time file writes are incompatible with the cache. Trade-off: a slower config
  phase on big Lawnchair builds, in exchange for correct per-compile versioning.
- `versionBuild` is floored at the **git commit count**, so it stays monotonic across CI runs (which
  build every commit but don't commit the bumped file back) without needing a commit-back loop. Local
  per-compile builds still advance it past that floor.

Only hand-edit `versionMajor` / `versionMinor`. To start a new minor line, bump `versionMinor`; the
next build resets `versionPatch` to 0.

## Workflows

All in `.github/workflows/`.

### `merged_build.yml` — debug, every commit

On every push/PR: checkout submodules → JDK 21 → apply overlay → run unit tests
(`testLawnWithQuickstepGithubDebugUnitTest`) → build `assembleLawnWithQuickstepGithubDebug`
(**debug-keyed**) → on push, publish/refresh a rolling **debug pre-release**
(`latest-debug-v<major>.<minor>`) with the APK. A build failure files a Jules issue. Builds on every
commit with `cancel-in-progress` (a newer push cancels an older run).

### `release-apk.yml` — signed release APK, deliberate

Trigger: a `v*` tag push, or manual dispatch. Decodes the keystore (**required** here), builds
`assembleLawnWithQuickstepGithubRelease` (signed), **verifies** the APK's signer-cert SHA-256 against
`KEYSTORE_SHA256`, and publishes a GitHub **Release** with the signed APK.

### `play-release.yml` — signed AAB → Google Play, deliberate

Trigger: a `v*` tag push, or manual dispatch (pick the track: internal/alpha/beta/production, default
internal). Builds `bundleLawnWithQuickstepPlayRelease` (Play flavor, signed) and uploads via the
`r0adkll/upload-google-play` action with `PLAY_SERVICE_ACCOUNT_JSON`.

> **Cutting a release:** `git tag v1.2.0 && git push origin v1.2.0` fires both `release-apk` and
> `play-release`.

### `update-upstream.yml` — submodule auto-update

Every 6h (or manual): move `upstream/` to the latest `16-dev`, re-apply the overlay, build. Green →
commit the bump to `main`. Red → file an issue tasking `@gemini-code-assist /jules` to re-author the
drifted seam. Only builds when upstream actually moved.

### `ci.yml` — `Build Lunch Heir`

The original simple build check (submodules → JDK 21 → apply overlay → assemble GithubDebug → upload
the APK artifact). Runs on `main` and `claude/**` pushes + PRs.

### `bridge.yml`

Builds the Lunch Heir Bridge companion APK.

## Signing

Release builds are signed by the optional `signingConfig` in `lunchheir-overlay.gradle`, active when
`KEYSTORE_FILE` is exported (CI decodes it from `KEYSTORE_RAW`). **Only the `release` build type is
signed with the release keystore**; debug builds always use the debug key. On forks/PRs without the
secret, the release build falls back to the debug key so it never fails for lack of signing.

### Secrets

| Secret | Used by |
|---|---|
| `KEYSTORE_RAW` | base64 of the `.jks`, decoded in CI |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | the release `signingConfig` |
| `KEYSTORE_SHA256` | signature verification in `release-apk.yml` |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play upload in `play-release.yml` |
| `ARCORE_API_KEY` | injected into `local.properties` (optional) |

`KEYSTORE_OWNER / SHA1 / PRIVATE / PUBLIC / CHAIN / RSA` are alternate key material / fingerprints
not needed for the `.jks` flow.

### Play prerequisites (one-time, on Google's side)

- The app `com.hereliesaz.lunchheir.play` must already exist in the Play Console — Google rejects the
  **first** upload via API, so do one manual upload, then automation takes over.
- The `PLAY_SERVICE_ACCOUNT_JSON` service account needs release permission for it.
- The `play` flavor may require a `google-services.json` to build; it is not wired yet (the
  `GOOGLE_SERVICES_*` secrets exist if/when it's needed).
