package dev.yoanndev90.statusbarhider.overlay

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.session.MediaSessionManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.settings.TapAction
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.system.runSafely

/**
 * Tap actions of the custom bar widgets, shared by both overlay services
 * (status bar, lock screen) so the intent plumbing lives in one place.
 *
 * Each widget has a built-in default ([defaultOf]) which the user can
 * override per widget through [OverlayPrefs.tapActions]; an unknown id -
 * the spacer above all - resolves to nothing and the tap is ignored.
 */
internal object BarTapActions {
	private const val TAG = "BarTapActions"

	/** One-shot command; a pull-down must never wait on a queued sequence. */
	private const val EXPAND_NOTIFICATIONS = "cmd statusbar expand-notifications"
	private const val CMD_TIMEOUT_SEC = 5L

	/**
	 * The built-in target of [id], or null for a widget with none (the
	 * spacer, and any id a future version adds before wiring it). Kept
	 * separate from [open] so the table can be asserted without an Android
	 * runtime.
	 */
	internal fun defaultOf(id: String): TapAction? =
		when (id) {
			WidgetId.CLOCK, WidgetId.ALARM -> TapAction.CLOCK
			WidgetId.DATE, WidgetId.CALENDAR -> TapAction.CALENDAR
			WidgetId.NOTIFS -> TapAction.NOTIFICATIONS
			WidgetId.BATTERY -> TapAction.BATTERY
			WidgetId.BANDWIDTH -> TapAction.DATA_USAGE
			WidgetId.CONNECTIVITY -> TapAction.WIFI
			WidgetId.MEDIA -> TapAction.MEDIA
			else -> null
		}

	/**
	 * What a tap on [id] actually does: the user's override when one is
	 * stored, the built-in default otherwise. An absent key deliberately
	 * means "default" rather than NONE, so changing a default later never
	 * breaks data saved before the change; an id with no default at all
	 * (the spacer) stays inert whatever the map holds.
	 */
	internal fun resolve(id: String, overrides: Map<String, TapAction>): TapAction? =
		defaultOf(id)?.let { overrides[id] ?: it }

	/**
	 * Opens whatever the widget [id] stands for, honoring the per-widget
	 * overrides. Suspends for the shell-backed actions (pull-down, system
	 * shortcuts).
	 *
	 * @param overrides stored [OverlayPrefs.tapActions] map; empty means defaults.
	 */
	@Suppress("CyclomaticComplexMethod") // One flat dispatch line per action; splitting the table would scatter the map.
	suspend fun open(context: Context, id: String, overrides: Map<String, TapAction> = emptyMap()) {
		when (resolve(id, overrides)) {
			TapAction.CLOCK -> openAction(context, AlarmClock.ACTION_SHOW_ALARMS)
			TapAction.CALENDAR -> openDate(context)
			TapAction.NOTIFICATIONS -> expandNotifications(context)
			TapAction.BATTERY -> openAction(context, Intent.ACTION_POWER_USAGE_SUMMARY)
			// HyperOS ships no handler for the AOSP action (verified on device:
			// ActivityNotFoundException) and its data-usage screen sits behind
			// the MIUI network-assistant actions; ENTRANCE opens the real
			// "Data usage" page, SETTINGS is the older-MIUI fallback.
			TapAction.DATA_USAGE ->
				openAction(
					context,
					Settings.ACTION_DATA_USAGE_SETTINGS,
					"miui.intent.action.NETWORKASSISTANT_ENTRANCE",
					"miui.intent.action.NETWORKASSISTANT_SETTINGS"
				)
			TapAction.WIFI -> openAction(context, Settings.ACTION_WIFI_SETTINGS)
			TapAction.MEDIA -> openMedia(context)
			TapAction.LOCK_SCREEN -> shell(context, "input keyevent KEYCODE_SLEEP", "lock screen")
			// KEYCODE_SYSRQ is the screenshot key on this firmware; the capture
			// lands in /sdcard/DCIM/Screenshots (verified on device).
			TapAction.SCREENSHOT -> shell(context, "input keyevent KEYCODE_SYSRQ", "screenshot")
			TapAction.AUTO_ROTATE -> toggleAutoRotate(context)
			TapAction.DND -> toggleDnd(context)
			TapAction.PLAY_PAUSE ->
				shell(context, "input keyevent KEYCODE_MEDIA_PLAY_PAUSE", "play/pause")
			TapAction.TORCH -> toggleTorch(context)
			TapAction.NONE, null -> Unit
		}
	}

	/**
	 * Forwards the bar's pull-down gesture to SystemUI. The interactive window
	 * owns the touch stream - that is what makes the widgets tappable - so the
	 * shade can no longer open under the finger and has to be asked for
	 * through the same shell channel as the hide/restore commands, with the
	 * same Shizuku dependency.
	 */
	suspend fun expandNotifications(context: Context) {
		val result = ShellRunner.run(EXPAND_NOTIFICATIONS, timeoutSec = CMD_TIMEOUT_SEC)
		if (result.exit == 0) return
		// One line per failure kind: a dead Shizuku would otherwise flood the tab.
		val line = "expand notifications failed (exit ${result.exit}): ${result.out}"
		Log.w(TAG, line)
		LogStore.appendOnce(context, "expand-notifications#${result.exit}", "ERROR: $line")
	}

	/** Runs one shell shortcut and reports a refusal (dead Shizuku, bad syntax) once. */
	private suspend fun shell(context: Context, cmd: String, label: String) {
		val result = ShellRunner.run(cmd, timeoutSec = CMD_TIMEOUT_SEC)
		if (result.exit == 0) return
		val line = "$label failed (exit ${result.exit}): ${result.out}"
		Log.w(TAG, line)
		LogStore.appendOnce(context, "$label#${result.exit}", "ERROR: $line")
	}

	/**
	 * Flips auto-rotation: `settings get` first (no dedicated toggle exists
	 * through the shell), then writes the opposite value.
	 */
	private suspend fun toggleAutoRotate(context: Context) {
		val current = ShellRunner.run("settings get system accelerometer_rotation", timeoutSec = CMD_TIMEOUT_SEC)
		val enabled = current.out.trim() == "1"
		shell(context, "settings put system accelerometer_rotation ${if (enabled) 0 else 1}", "auto-rotate toggle")
	}

	/**
	 * Flips DND. `settings get secure zen_mode` returns null on HyperOS, so
	 * the live state comes from `dumpsys notification`; an unreadable state
	 * is treated as "off" and turns DND on.
	 */
	private suspend fun toggleDnd(context: Context) {
		val current = ShellRunner.run("dumpsys notification | grep -m1 mZenMode", timeoutSec = CMD_TIMEOUT_SEC)
		val enabled = current.out.contains("mZenMode=") && !current.out.contains("ZEN_MODE_OFF")
		shell(context, "cmd notification set_dnd ${if (enabled) "off" else "on"}", "dnd toggle")
	}

	/**
	 * Flips the flashlight through [CameraManager] (no shell involved). The
	 * torch callback delivers the current mode of each flash camera right
	 * after registration, which is the read side of the toggle; the first
	 * delivery unregisters so repeated taps never leak callbacks. No CAMERA
	 * permission means SecurityException - logged, and the tap does nothing.
	 */
	private fun toggleTorch(context: Context) {
		val camera = context.getSystemService(CameraManager::class.java) ?: return
		val hasTorch =
			camera.cameraIdList.any {
				camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
			}
		if (!hasTorch) {
			Log.w(TAG, "torch toggle: no flash unit")
			return
		}
		try {
			camera.registerTorchCallback(
				object : CameraManager.TorchCallback() {
					override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
						camera.unregisterTorchCallback(this)
						try {
							camera.setTorchMode(cameraId, !enabled)
						} catch (e: Exception) {
							val line = "torch toggle failed: $e"
							Log.w(TAG, line)
							LogStore.append(context, "ERROR: $line")
						}
					}
				},
				null
			)
		} catch (e: Exception) {
			val line = "torch toggle failed: $e"
			Log.w(TAG, line)
			LogStore.append(context, "ERROR: $line")
		}
	}

	/**
	 * Fire the first settings intent this firmware actually answers: some
	 * OEMs (HyperOS) drop the AOSP action entirely, so a single intent would
	 * land in the swallow-and-log path with the screen never opening. An
	 * unresolvable intent moves to the next candidate; anything else (a real
	 * platform error) propagates to [runSafely] as before.
	 */
	private fun openAction(context: Context, vararg actions: String) {
		runSafely(context) {
			for (action in actions) {
				try {
					context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
					return@runSafely
				} catch (_: ActivityNotFoundException) {
					// No handler here; the next candidate may have one.
				}
			}
			Log.w(TAG, "no handler for ${actions.joinToString()}")
		}
	}

	private fun openDate(context: Context) {
		runSafely(context) {
			try {
				context.startActivity(
					Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time"))
						.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				)
			} catch (_: Exception) {
				try {
					val launch =
						context.packageManager
							.getLaunchIntentForPackage("com.google.android.calendar")
							?: return@runSafely
					context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				} catch (e: Exception) {
					// Even the package-manager fallback failed (no calendar app at all).
					Log.w(TAG, "open calendar", e)
				}
			}
		}
	}

	/**
	 * The app behind the now-playing widget: the first active media session,
	 * which is the one the widget draws. Reading the sessions needs notification
	 * access - the same grant the widget itself needs - so without it there is
	 * nothing to open and nothing to report.
	 */
	private fun openMedia(context: Context) {
		runSafely(context) {
			val manager =
				context.getSystemService(MediaSessionManager::class.java) ?: return@runSafely
			val listener = ComponentName(context, NotifListenerService::class.java)
			val pkg =
				manager.getActiveSessions(listener).firstOrNull()?.packageName ?: return@runSafely
			val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return@runSafely
			context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		}
	}
}
