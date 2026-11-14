package dev.yoanndev90.statusbarhider.overlay.widgets

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.core.settings.LockScreenMode
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.BarTapActions
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState
import dev.yoanndev90.statusbarhider.overlay.model.OverlayProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * System-wide custom status bar, rendered with Compose inside a
 * TYPE_APPLICATION_OVERLAY window. Layout mirrors the former
 * view_custom_status_bar.xml (sizes, paddings, order), except that a detected
 * cutout splits the widget row around the camera and can carry a progress ring.
 */
@Composable
fun OverlayBar(
	prefs: OverlayPrefs,
	state: OverlayBarState,
	onWidgetClick: (String) -> Unit
) {
	val fg = Color(prefs.textColor())
	val context = LocalContext.current
	// Owns the shell round-trip of a pull-down; dies with the bar.
	val gestureScope = rememberCoroutineScope()
	val density = LocalDensity.current
	val camera = state.camera
	val progress = state.progress
	val padStart = prefs.padStartDp.dp
	val padEnd = prefs.padEndDp.dp
	val padTop = prefs.padTopDp.dp
	val padBottom = prefs.padBottomDp.dp
	val spacing = prefs.widgetSpacingDp.dp
	val showRing = camera != null && prefs.cameraRing && progress != null
	val ringProgress: OverlayProgress? = if (showRing) progress else null
	val ringStrokePx = with(density) { prefs.cameraRingStrokeDp.dp.toPx() }
	// Widgets keep clear of the ring too, not just of the cutout slot.
	val ringClearancePx = if (showRing) ringStrokePx else 0f

	// The bar grows so the camera (and its ring) always fit inside it:
	// one window, no touch dead strip below the bar.
	val minHeight =
		if (camera != null) {
			with(density) { (camera.centerY + camera.radius + ringClearancePx).toDp() }
		} else {
			0.dp
		}

	val cell: @Composable RowScope.(String) -> Unit = { id ->
		key(id) { WidgetSlot(id, prefs, state, fg, onWidgetClick) }
	}

	Box(
		modifier =
			Modifier
				.fillMaxWidth()
				.defaultMinSize(minHeight = minHeight)
				.background(Color(prefs.backgroundColor()))
				.pointerInput(context, gestureScope) { pullDownGesture(gestureScope, context) }
	) {
		val clockOnly = state.locked && prefs.lockScreenMode == LockScreenMode.CLOCK_ONLY
		BarLayout(
			prefs = prefs,
			clockOnly = clockOnly,
			camera = camera,
			cell = cell,
			spacing = spacing,
			padStart = padStart,
			padTop = padTop,
			padEnd = padEnd,
			padBottom = padBottom,
			ringClearancePx = ringClearancePx
		)
		if (showRing && !clockOnly) {
			CameraRing(camera, ringProgress, prefs, fg)
		}
	}
}

/** Dispatches one widget id to the widget that draws it. */
@Composable
private fun RowScope.WidgetSlot(
	id: String,
	prefs: OverlayPrefs,
	state: OverlayBarState,
	fg: Color,
	onWidgetClick: (String) -> Unit
) {
	when (id) {
		WidgetId.CLOCK -> ClockWidget(prefs, state.screenOn, fg) { onWidgetClick(WidgetId.CLOCK) }
		WidgetId.DATE -> DateWidget(prefs, state.screenOn, fg) { onWidgetClick(WidgetId.DATE) }
		WidgetId.CALENDAR -> state.calendarText?.let { CalendarWidget(prefs, it, fg) }
		WidgetId.NOTIFS -> NotifWidget(prefs, state, fg)
		WidgetId.MEDIA -> state.mediaText?.let { MediaWidget(prefs, it, fg) }
		WidgetId.SPACER -> Spacer(Modifier.weight(1f))
		WidgetId.CONNECTIVITY -> ConnectivityWidget(prefs, state, fg)
		WidgetId.BATTERY -> BatteryWidget(prefs, state, fg)
		WidgetId.ALARM -> state.alarmText?.let { AlarmWidget(prefs, it, fg) }
		WidgetId.BANDWIDTH -> BandwidthWidget(prefs, state, fg)
	}
}

/**
 * Drag-down on the bar. The interactive window owns the touch stream - that is
 * what makes the widgets tappable - so the notification shade can no longer
 * open under the finger: once the drag crosses the touch slop downwards, ask
 * SystemUI for it through the shell and swallow the rest of the gesture. A
 * movement that never crosses the slop (a plain tap) stays untouched for the
 * widgets' own clickable.
 *
 * The consumption happens on the Initial pass on purpose: children read the
 * very same event on Main afterwards, so a drag is cancelled as a tap before
 * it can fire, without ever touching the down event itself.
 */
private suspend fun PointerInputScope.pullDownGesture(
	scope: CoroutineScope,
	context: Context
) {
	awaitEachGesture {
		val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
		val startX = down.position.x
		val startY = down.position.y
		val slop = viewConfiguration.touchSlop
		var dragged = false
		var pulled = false
		while (true) {
			val event = awaitPointerEvent(PointerEventPass.Initial)
			val change = event.changes.firstOrNull { it.id == down.id }
			// One exit: the pointer is gone, or the finger lifted.
			if (change == null || change.changedToUp()) break
			val dx = change.position.x - startX
			val dy = change.position.y - startY
			if (!dragged && dx * dx + dy * dy > slop * slop) dragged = true
			if (dragged) {
				change.consume()
				// Downwards and dominant: a sideways drag is no shade request.
				if (!pulled && dy > slop && dy >= abs(dx)) {
					pulled = true
					scope.launch { BarTapActions.expandNotifications(context) }
				}
			}
		}
	}
}
