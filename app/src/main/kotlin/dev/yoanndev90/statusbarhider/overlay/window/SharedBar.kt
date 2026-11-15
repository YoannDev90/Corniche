package dev.yoanndev90.statusbarhider.overlay.window

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.yoanndev90.statusbarhider.overlay.model.OverlayBarState

/**
 * State shared inside the app process between [StatusBarOverlayService]
 * (which maintains it) and [LockScreenOverlayService], the accessibility
 * service that hosts the bar window itself: a TYPE_ACCESSIBILITY_OVERLAY
 * sits above the system StatusBar window (so taps reach the widgets where
 * they are drawn) and above the keyguard (where a TYPE_APPLICATION_OVERLAY
 * is hidden by the window policy).
 */
internal object SharedBar {
	val barState: MutableState<OverlayBarState> = mutableStateOf(OverlayBarState())

	/** True while the bar is detached because a blacklisted app is up front. */
	val suppressed: MutableState<Boolean> = mutableStateOf(false)

	/**
	 * True while the accessibility service hosts the bar window. The regular
	 * service then keeps its own window down - two bars would stack - and
	 * only publishes state.
	 */
	val a11yHost: MutableState<Boolean> = mutableStateOf(false)

	/**
	 * True while the notification shade is expanded, from the window-state
	 * events the accessibility service receives. The hosted window sits above
	 * the shade, so it steps aside for it instead of drawing over it and
	 * stealing its taps. Only consulted while unlocked: on the keyguard the
	 * bar has always floated above the shade.
	 */
	val shadeOpen: MutableState<Boolean> = mutableStateOf(false)

	/**
	 * Burn-in shift of the window that is actually on screen, registered by
	 * whichever service hosts it; null falls back to the regular service's
	 * own window.
	 */
	var shiftBurnIn: (() -> Unit)? = null
}
