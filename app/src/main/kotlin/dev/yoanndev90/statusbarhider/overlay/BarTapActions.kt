package dev.yoanndev90.statusbarhider.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.system.runSafely

/**
 * Tap targets of the custom bar widgets, shared by both overlay services
 * (status bar, lock screen) so the intent plumbing lives in one place.
 * Unknown ids are ignored: a widget without a target simply does nothing.
 */
internal object BarTapActions {
	private const val TAG = "BarTapActions"

	/** Opens whatever the widget [id] stands for. */
	fun open(context: Context, id: String) {
		when (id) {
			WidgetId.CLOCK -> openClock(context)
			WidgetId.DATE -> openDate(context)
			else -> Unit
		}
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
