package dev.yoanndev90.statusbarhider.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.yoanndev90.statusbarhider.R
import dev.yoanndev90.statusbarhider.core.settings.OverlayPrefs
import dev.yoanndev90.statusbarhider.core.settings.TapAction
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.BarTapActions
import dev.yoanndev90.statusbarhider.ui.state.PrefsViewModel

/**
 * One row per widget showing what a tap currently does; tapping a row opens
 * a single-choice picker. Picking "Default" removes the stored override, so
 * a later change to the built-in mapping keeps applying to that widget, and
 * the row subtitle always shows the *effective* action, override or default.
 */
@Composable
fun TapActionsGroup(
	prefs: OverlayPrefs,
	vm: PrefsViewModel
) {
	// Widget id being reconfigured; "" = no dialog. Saveable: a rotation keeps the picker up.
	var picking by rememberSaveable { mutableStateOf("") }

	Column(modifier = Modifier.fillMaxWidth()) {
		WidgetId.DEFAULT_ORDER
			.filter { it != WidgetId.SPACER }
			.forEach { id ->
				Row(
					modifier =
						Modifier
							.fillMaxWidth()
							.clickable { picking = id }
							.padding(vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = stringResource(WidgetId.labelRes(id)),
							style = MaterialTheme.typography.bodyLarge
						)
						Text(
							text = stringResource(actionLabel(BarTapActions.resolve(id, prefs.tapActions))),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					Icon(
						painter = painterResource(R.drawable.ic_chevron_right),
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(20.dp)
					)
				}
			}
	}

	if (picking.isNotEmpty()) {
		TapActionPicker(
			widgetId = picking,
			selected = prefs.tapActions[picking],
			onDismiss = { picking = "" },
			onPick = { action ->
				val id = picking
				vm.updatePrefs {
					copy(tapActions = if (action == null) tapActions - id else tapActions + (id to action))
				}
				picking = ""
			}
		)
	}
}

/** Single-choice list of every action, plus the "keep the default" entry. */
@Composable
private fun TapActionPicker(
	widgetId: String,
	selected: TapAction?,
	onDismiss: () -> Unit,
	onPick: (TapAction?) -> Unit
) {
	val widgetLabel = stringResource(WidgetId.labelRes(widgetId))
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.tap_dialog_title, widgetLabel)) },
		text = {
			LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
				item {
					TapChoiceRow(
						label =
							stringResource(
								R.string.tap_action_default,
								stringResource(actionLabel(BarTapActions.defaultOf(widgetId)))
							),
						selected = selected == null
					) { onPick(null) }
				}
				items(TapAction.entries) { action ->
					TapChoiceRow(label = stringResource(actionLabel(action)), selected = selected == action) {
						onPick(action)
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
		}
	)
}

/** One radio line of the picker; tapping it applies and closes through [onClick]. */
@Composable
private fun TapChoiceRow(
	label: String,
	selected: Boolean,
	onClick: () -> Unit
) {
	Row(
		modifier =
			Modifier
				.fillMaxWidth()
				.clickable(onClick = onClick)
				.padding(vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		RadioButton(selected = selected, onClick = null)
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(start = 4.dp)
		)
	}
}

/** String resource of the label shown for [action]; an unmapped id is "do nothing". */
@Suppress("CyclomaticComplexMethod") // Exhaustive one-line mapping; a `when` per row would hide the label table.
private fun actionLabel(action: TapAction?): Int =
	when (action) {
		TapAction.NONE, null -> R.string.tap_action_none
		TapAction.CLOCK -> R.string.tap_action_clock
		TapAction.CALENDAR -> R.string.tap_action_calendar
		TapAction.NOTIFICATIONS -> R.string.tap_action_notifications
		TapAction.BATTERY -> R.string.tap_action_battery
		TapAction.DATA_USAGE -> R.string.tap_action_data_usage
		TapAction.WIFI -> R.string.tap_action_wifi
		TapAction.MEDIA -> R.string.tap_action_media
		TapAction.LOCK_SCREEN -> R.string.tap_action_lock_screen
		TapAction.SCREENSHOT -> R.string.tap_action_screenshot
		TapAction.AUTO_ROTATE -> R.string.tap_action_auto_rotate
		TapAction.DND -> R.string.tap_action_dnd
		TapAction.PLAY_PAUSE -> R.string.tap_action_play_pause
		TapAction.TORCH -> R.string.tap_action_torch
	}
