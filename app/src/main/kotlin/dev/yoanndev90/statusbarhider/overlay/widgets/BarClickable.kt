package dev.yoanndev90.statusbarhider.overlay.widgets

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs

/**
 * Tap affordance of one bar widget. Gated on [OverlayPrefs.interactive]
 * because that is the mode where the window receives touches at all - and
 * there the bar's drag handler hands everything that is not a drag back to
 * these children, so a tap still lands while a pull-down does not.
 */
internal fun Modifier.barClickable(prefs: OverlayPrefs, onClick: () -> Unit): Modifier =
	if (prefs.interactive) clickable(onClick = onClick) else this
