package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.system.runSafely

/**
 * Tap targets of the custom bar widgets, shared by both overlay services
 * (status bar, lock screen) so the intent plumbing lives in one place.
 * Unknown ids are ignored: a widget without a target simply does nothing.
 */
internal object BarTapActions {
	private const val TAG = "BarTapActions"

	/** One-shot command; a pull-down must never wait on a queued sequence. */
	private const val EXPAND_NOTIFICATIONS = "cmd statusbar expand-notifications"
	private const val EXPAND_TIMEOUT_SEC = 5L

	/** Opens whatever the widget [id] stands for. */
	fun open(context: Context, id: String) {
		when (id) {
			WidgetId.CLOCK -> openClock(context)
			WidgetId.DATE -> openDate(context)
			else -> Unit
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

	private fun openClock(context: Context) {
		runSafely(context) {
			context.startActivity(
				Intent(AlarmClock.ACTION_SHOW_ALARMS)
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
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
}
