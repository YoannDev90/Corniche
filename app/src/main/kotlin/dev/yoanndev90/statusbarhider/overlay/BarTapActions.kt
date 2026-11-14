package dev.yoanndev90.statusbarhider.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.system.runSafely

/**
 * Tap targets of the custom bar widgets, shared by both overlay services
 * (status bar, lock screen) so the intent plumbing lives in one place.
 * Ids without a target - the spacer above all - are ignored: a widget
 * nobody wired up simply does nothing.
 */
internal object BarTapActions {
	private const val TAG = "BarTapActions"

	/** One-shot command; a pull-down must never wait on a queued sequence. */
	private const val EXPAND_NOTIFICATIONS = "cmd statusbar expand-notifications"
	private const val EXPAND_TIMEOUT_SEC = 5L

	/** Where the tap on a widget leads; the whole dispatch table lives here. */
	internal enum class Target { CLOCK, CALENDAR, NOTIFICATIONS, BATTERY, DATA_USAGE, WIFI, MEDIA }

	/**
	 * The target of [id], or null for a widget with none (the spacer, and any
	 * id a future version adds before wiring it). Kept separate from [open] so
	 * the table can be asserted without an Android runtime.
	 */
	internal fun targetOf(id: String): Target? =
		when (id) {
			WidgetId.CLOCK, WidgetId.ALARM -> Target.CLOCK
			WidgetId.DATE, WidgetId.CALENDAR -> Target.CALENDAR
			WidgetId.NOTIFS -> Target.NOTIFICATIONS
			WidgetId.BATTERY -> Target.BATTERY
			WidgetId.BANDWIDTH -> Target.DATA_USAGE
			WidgetId.CONNECTIVITY -> Target.WIFI
			WidgetId.MEDIA -> Target.MEDIA
			else -> null
		}

	/**
	 * Opens whatever the widget [id] stands for. Suspends only for
	 * [WidgetId.NOTIFS], which asks SystemUI for the panel through the shell.
	 */
	suspend fun open(context: Context, id: String) {
		when (targetOf(id)) {
			Target.CLOCK -> openAction(context, AlarmClock.ACTION_SHOW_ALARMS)
			Target.CALENDAR -> openDate(context)
			Target.NOTIFICATIONS -> expandNotifications(context)
			Target.BATTERY -> openAction(context, Intent.ACTION_POWER_USAGE_SUMMARY)
			Target.DATA_USAGE -> openAction(context, Settings.ACTION_DATA_USAGE_SETTINGS)
			Target.WIFI -> openAction(context, Settings.ACTION_WIFI_SETTINGS)
			Target.MEDIA -> openMedia(context)
			null -> Unit
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
		val result = ShellRunner.run(EXPAND_NOTIFICATIONS, timeoutSec = EXPAND_TIMEOUT_SEC)
		if (result.exit == 0) return
		// One line per failure kind: a dead Shizuku would otherwise flood the tab.
		val line = "expand notifications failed (exit ${result.exit}): ${result.out}"
		Log.w(TAG, line)
		LogStore.appendOnce(context, "expand-notifications#${result.exit}", "ERROR: $line")
	}

	/** Fire one settings intent; an activity that answers it is all we ask. */
	private fun openAction(context: Context, action: String) {
		runSafely(context) {
			context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		}
	}

	private fun openDate(context: Context) {
		runSafely(context) {
			try {
				context.startActivity(
					Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android/calendar/time"))
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
