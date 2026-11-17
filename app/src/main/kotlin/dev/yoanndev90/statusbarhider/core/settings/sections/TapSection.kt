package dev.yoanndev90.statusbarhider.core.settings.sections

import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.SectionGroup
import dev.yoanndev90.statusbarhider.core.settings.SettingRow
import dev.yoanndev90.statusbarhider.core.settings.SettingSection
import dev.yoanndev90.statusbarhider.core.settings.Subtitle
import dev.yoanndev90.statusbarhider.ui.screens.settings.TapActionsGroup

/**
 * What a tap on each bar widget does: one row per widget, a picker per row.
 * The per-widget list shares its labels with the bar itself, and the stored
 * overrides (a ValueSpec) live behind a single Custom row the picker writes.
 */
object TapSection :
	SettingSection<OverlayPrefs>(
		id = "taps",
		icon = R.drawable.ic_touch_app,
		title = R.string.cat_taps,
		subtitle = Subtitle.Res(R.string.cat_taps_sub),
		group = SectionGroup.CUSTOM_BAR,
		keywords = listOf("tap", "click", "action", "widget", "shortcut", "torch", "screenshot", "dnd", "lock"),
		rows = listOf(SettingRow.Custom { prefs, vm -> TapActionsGroup(prefs, vm) })
	)
