package dev.yoanndev90.statusbarhider.overlay

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.yoanndev90.statusbarhider.core.command.ShellRunner
import dev.yoanndev90.statusbarhider.core.log.LogStore
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.data.OverlayPrefsRepository
import dev.yoanndev90.statusbarhider.overlay.widgets.OverlayBar
import dev.yoanndev90.statusbarhider.overlay.window.OverlayWindow
import dev.yoanndev90.statusbarhider.overlay.window.SharedBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Which window currently holds input focus, asked through the same shell channel as the tap commands. */
private const val FOCUS_QUERY = "dumpsys window | grep -m1 mCurrentFocus"
private const val FOCUS_TIMEOUT_SEC = 5L

/**
 * Hosts the bar window as a [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]
 * (window layer 31) whenever this service is enabled:
 *
 * - the window policy hides TYPE_APPLICATION_OVERLAY windows behind the keyguard
 *   (mPolicyVisibility=false), but layer 31 sits above the NotificationShade,
 *   which is what sameerasw/essentials does for its lock-screen bar;
 * - layer 31 also sits above the system StatusBar window, whose touch region
 *   (the top ~94px) would otherwise swallow taps meant for the widgets drawn
 *   there - only a bound accessibility service may open such a window.
 *
 * While bound, this service owns the only bar window and [StatusBarOverlayService]
 * keeps publishing state (indicators, prefs, suppression) with its own window
 * down; with the service off, the regular overlay is the bar again.
 */
class LockScreenOverlayService : AccessibilityService() {
	/** Lazy: the base Context only exists after Service.attach(), unlike during construction. */
	private val prefsRepo by lazy { OverlayPrefsRepository.getInstance(this) }
	private val prefsState = mutableStateOf(OverlayPrefs())
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private var window: OverlayWindow? = null
	private var barState by SharedBar.barState

	override fun onServiceConnected() {
		super.onServiceConnected()
		val w =
			OverlayWindow(
				context = this,
				prefsFlow = prefsRepo.state,
				windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
				content = {
					OverlayBar(
						prefs = prefsState.value,
						state = SharedBar.barState.value,
						onWidgetClick = ::onWidgetClick
					)
				}
			)
		window = w
		// This window is the one on screen: the burn-in shift has to move it,
		// and the regular service must stand its own window down.
		SharedBar.shiftBurnIn = { w.shiftForBurnIn() }
		SharedBar.a11yHost.value = true
		scope.launch { prefsRepo.state.collect { prefsState.value = it } }
		scope.launch {
			snapshotFlow { shouldAttach() }
				.distinctUntilChanged()
				.collect { w.sync(it) }
		}
		scope.launch {
			snapshotFlow { SharedBar.barState.value.screenOn }
				.distinctUntilChanged()
				.collect { w.setRenderingActive(it) }
		}
		scope.launch {
			w.cameraGeometry.collect { geometry ->
				if (w.view != null && geometry != barState.camera) barState = barState.copy(camera = geometry)
			}
		}
	}

	/**
	 * The regular service's rule - the bar is up unless a blacklisted app is
	 * in front, or the keyguard is up with the lock-screen toggle off - plus
	 * the shade: this window draws above the NotificationShade, so while the
	 * shade is expanded (and the keyguard is not) it must go, or it would
	 * cover the shade and eat its taps.
	 */
	private fun shouldAttach(): Boolean {
		val s = SharedBar.barState.value
		return prefsState.value.enabled &&
			!SharedBar.suppressed.value &&
			!(s.locked && !prefsState.value.showOnLockScreen) &&
			(s.locked || !SharedBar.shadeOpen.value)
	}

	private fun onWidgetClick(id: String) {
		if (!prefsState.value.interactive) return
		val overrides = prefsState.value.tapActions
		scope.launch { BarTapActions.open(this@LockScreenOverlayService, id, overrides) }
	}

	private var shadeEventSeq = 0

	override fun onAccessibilityEvent(event: AccessibilityEvent?) {
		if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
		// A window became active: anything but SystemUI means the shade is gone.
		val pkg = event.packageName?.toString()
		val cls = event.className?.toString()
		val seq = ++shadeEventSeq
		// Evidence line for the shade filter: one entry per window class seen.
		LogStore.appendOnce(this, "a11y:$pkg/$cls", "a11y window state: pkg=$pkg cls=$cls")
		if (pkg != "com.android.systemui") {
			SharedBar.shadeOpen.value = false
			return
		}
		// A SystemUI window became active - the shade, but also a toast (the DND
		// confirmation) or a volume panel, which would otherwise park the bar
		// until the next app switch. Only the shade takes input focus, so ask
		// the window manager instead of trusting the package; an unreadable
		// answer (no Shizuku) falls back to stepping aside as before.
		scope.launch {
			val focused = shadeHasFocus()
			if (seq == shadeEventSeq) SharedBar.shadeOpen.value = focused
		}
	}

	/** True while a shade-like window (NotificationShade, a SystemUI dialog) holds focus. */
	private suspend fun shadeHasFocus(): Boolean {
		val result = ShellRunner.run(FOCUS_QUERY, timeoutSec = FOCUS_TIMEOUT_SEC)
		if (result.exit != 0) return true
		return result.out.contains("NotificationShade") || result.out.contains("com.android.systemui")
	}

	override fun onInterrupt() = Unit

	override fun onDestroy() {
		scope.cancel()
		window?.detach()
		window = null
		SharedBar.shiftBurnIn = null
		SharedBar.a11yHost.value = false
		super.onDestroy()
	}
}
