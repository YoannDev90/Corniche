# Security policy

## Reporting a vulnerability

Please report anything exploitable **privately**: use *Report a vulnerability*
on this repository's **Security** tab, or contact the maintainer directly
through the GitHub profile linked in the README. Do not open a public issue
for a security report.

Supported versions: the latest release and `master`.

## Privilege model

The app hides the system status bar through **Shizuku**, i.e. it runs
`settings` / `cmd statusbar` commands with *shell* privileges once the user
grants them. That grant — not the app — is the privileged boundary: without
Shizuku the app falls back to its own overlay only.

The app declares **no `INTERNET` permission** and opens no sockets; it never
talks to a network. The only URLs it holds are links handed to the browser
(source, issues, license).

## Exported components and their gates

| Component | Why exported | Gate |
|---|---|---|
| `control.ControlReceiver` (control API) | Tasker / `am broadcast` must reach it | No intent filter (explicit broadcasts only) **and** a 16-byte random token checked before dispatch; see [Control API](docs/control-api.md) |
| `core.backup.SettingsProvider` | The debug/release sister build (and the future renamed successor) is a differently signed package | `call()` answers only packages the **system** resolves from the caller's UID; read-only, and the served blob never carries the control token |
| `rikka.shizuku.ShizukuProvider` | Standard Shizuku setup, delivers the Shizuku binder | `INTERACT_ACROSS_USERS_FULL` (Shizuku's own documented configuration) |
| `overlay.LockScreenOverlayService` | The system binds accessibility services | `BIND_ACCESSIBILITY_SERVICE` (signature) |
| `overlay.NotifListenerService` | SystemUI / Settings bind notification listeners | `BIND_NOTIFICATION_LISTENER_SERVICE` (signature) |
| `tiles.HideRestoreTile`, `tiles.CustomBarTile` | SystemUI binds Quick Settings tiles | `BIND_QUICK_SETTINGS_TILE` (signature) |
| `widget.BarWidgetProvider` | Launcher hosts the widget | Control taps carry the control token inside the PendingIntent and are checked in `onReceive` |
| `MainActivity` | Launcher entry point | Standard activity export |
| `FileProvider` (`*.fileprovider`) | Sharing the on-disk app log | `exported="false"` — only explicit URI grants |
| `BootReceiver` | — | `exported="false"`; the system still delivers `BOOT_COMPLETED` |

The control token is generated from `SecureRandom`, lives in app-private
`SharedPreferences`, is **never** included in a settings export or backup
(a restore generates a fresh one), and is copied into the UI only by the
explicit *Copy control API token* action.

## Data at rest

Everything is local, in app-private storage: hide state and app flags
(`SharedPreferences`), the OEM pick, profiles, the overlay settings blob and
the in-app log. A settings backup carries user settings only — never the
control token.

## Out of scope

- **Anyone with shell/ADB access to the device** (including a connected
  computer): shell can read and write these settings directly anyway, and can
  read app-private data of a debuggable build.
- **Shizuku itself** and the ADB pairing flow — upstream project.
- **OEM config commands** (`app/src/main/assets/oem/*.json`): reviewed like
  code, but they do run with shell privileges once Shizuku is granted; a
  malicious config requires a malicious build of the app.
