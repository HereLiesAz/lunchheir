# Smart auto-fill

Smart auto-fill is the AI behind **smart groups** (and, by the same engine, smart folders). The
title and the app set are **mutually- and self-informing**, and re-evaluated **continuously** (not
one-shot) as state changes:

- **apps → title:** propose a best-guess title from the current apps, and re-derive it as membership
  changes.
- **title → apps:** a (user- or AI-set) title steers population — "Adobe" pulls Adobe apps, "Photo
  editing" pulls photo editors.
- **apps → apps:** the current membership re-informs which other installed apps belong; the set
  self-reinforces (its own apps imply more apps).

Either side may be user-provided; the engine fills in and keeps refining whichever is missing or
stale. Net: a living group whose title and contents keep re-deriving from its own evolving state
(seed → new installs → user edits → re-evaluation).

All code is overlay-only under `overlay/src/com/hereliesaz/lunchheir/smartfill/`.

## Backend — hybrid

### On-device engine (always on)

`SmartFillEngine` is pure Kotlin, free, fully private, offline. It scores candidate apps against the
current membership + title on a few signals: `ApplicationInfo.category` match, shared installer
package (a developer proxy), title-keyword overlap, and label-token similarity to existing members.
It exposes a one-pass `evaluate()` scorer, a `suggestTitle()`, and a `converge()` self-reinforcing
loop (members → title → more members). Good for type/developer grouping; weaker on abstract
"purpose". This engine always runs and drives the continuous loop.

Inputs are `AppSignals` (label, package, category, installer, tokens) built from the device's app
inventory by `InstalledAppsSource` (backed by `LauncherApps`).

### Cloud refiner (optional, off by default)

`SmartFillRemote` is an **optional** refiner, gated behind a per-feature consent toggle **and** a
user-provided API key (`SmartFillConfig`, stored in private `SharedPreferences`). It refines the
on-device baseline for purpose-based grouping; on **any** error it falls back silently to the
on-device result. It sends the installed-app inventory (labels/packages/categories) off-device, hence
the explicit opt-in. Dependency-free (`HttpURLConnection` + `org.json`).

It is **provider-agnostic** — any AI backend works, via `AiProvider`:

- `baseUrl`, `model`, `apiKey`, `format`, optional `extraHeaders`.
- `Format.OPENAI` (default) — the OpenAI-compatible chat-completions shape, the lingua franca that
  covers OpenAI, Gemini's compat endpoint, Groq, Together, Mistral, local Ollama / LM Studio, etc.
- `Format.ANTHROPIC` — Anthropic's messages shape.
- `Format.CUSTOM` — point at any endpoint.

Adding a provider is **configuration, not code**: set base URL + model + key + format in the settings
sheet (Hax shell → TWEAKS).

## Entry point

`SmartFill.suggest(context, seeds, title)` runs the on-device baseline, then the optional cloud
refine, and returns the resulting members + suggested title. It is driven continuously:

- at group creation, `SmartGroupSeeder.fill` calls it to seed a new smart group;
- on later installs, `GroupAppMonitor` re-runs the seeder for every registered smart group.

See [`groups.md`](groups.md) for how smart-fill plugs into group creation and the install monitor.
