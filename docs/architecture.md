# Architecture

Single Gradle module (`:app`), Kotlin + Jetpack Compose, package
`dev.yoanndev90.statusbarhider`, sources under
`app/src/main/kotlin/dev/yoanndev90/statusbarhider/`. Unit tests live in
`app/src/test/`. This page is the map; [CONTRIBUTING.md](../CONTRIBUTING.md)
carries the per-area conventions (strings vs logs, where new code goes).

## Packages

| Package | What lives there |
|---|---|
| `hide/` | The hide/restore pipeline: `HideInteractor` runs the OEM command set through Shizuku, persists the intended state and caches the last *observed* state; `DisableFlags` parses the `dumpsys` disable flags. |
| `overlay/` | The custom Compose status bar. `OverlayController` is the façade; `StatusBarOverlayService` hosts it, `overlay/window/` manages the window and its foreground notification, `overlay/widgets/` the bar composables, `overlay/system/` the system data sources (signal, Bluetooth, notifications, media), `overlay/poll/` the refresh scheduler, `overlay/model/` shared state. |
| `control/` | External control API: `ControlReceiver` (exported), `ControlAuth` (token gate), `ControlActions` (dispatch). |
| `data/` | Repositories and stores over SharedPreferences / Shizuku: overlay prefs, app flags, profiles, OEM repository, Shizuku state. |
| `core/` | Cross-cutting services: `command/` (`ShellRunner`), `settings/` (the settings model, spec registry, sections), `oem/` (OEM config parsing + device detection), `backup/` (export/import, sister/successor builds), `profiles/`, `log/` (`LogStore`), `permission/`, `usage/`, `watcher/` (SystemUI restart), `keepalive/`, `notif/`. |
| `tiles/` | The two Quick Settings tiles. |
| `widget/` | Home-screen widget (`BarWidgetProvider`). |
| `ui/` | All Compose UI under one roof: `screens/<feature>/` (a screen + its ViewModel), `components/` (reusable composables), `shell/` (nav host), `state/` (state shared between screens), `theme/`. |
| root | `MainActivity`, `BootReceiver`. |

OEM command sets are data, not code: one JSON file per OEM in
`app/src/main/assets/oem/` (plus the optional `keepAlive` screens), parsed by
`core/oem/`.

## Key flows

- **Hide / restore** — any caller (screen, QS tile, widget, control API, boot)
  goes through `HideInteractor`: OEM JSON commands via `ShellRunner`
  (Shizuku), then the persisted intent, the observed-state cache and the
  tile/widget/log refreshes follow.
- **Custom bar** — `OverlayPrefs` (specs in `core/settings/`) →
  `OverlayController` → `StatusBarOverlayService` and the Compose window.
- **Control API** — an explicit broadcast → token check (`ControlAuth`) →
  `ControlActions`, which reuses the same paths as the UI. See
  [Control API](control-api.md).
- **Backup** — `core/backup/SettingsBackup` exports/imports settings without
  ever carrying the control token; `SettingsProvider` serves the
  sister/successor build only.

## Quality gate

`mise run check` — ktlint + detekt + unit tests + Android lint — is the
single source of truth and runs in CI on every push and pull request
(`.github/workflows/ci.yml`). `mise run format` fixes formatting;
`mise run setup-hooks` installs the git hooks that run the same checks
locally before each commit. CI is the backstop.

## Docs index

- [How it works](how-it-works.md) — the hide pipeline end to end
- [Features](features.md) — what the app can do
- [Build & signing](building.md), [Release process](release-process.md)
- [Control API](control-api.md), [Limitations](limitations.md)
- [CONTRIBUTING.md](../CONTRIBUTING.md) — adding a new OEM, strings vs logs
- [SECURITY.md](../SECURITY.md) — privilege model and exported surfaces
