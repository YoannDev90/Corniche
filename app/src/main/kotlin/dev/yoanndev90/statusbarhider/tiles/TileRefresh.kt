package dev.yoanndev90.statusbarhider.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes

/**
 * Paints this tile from a single boolean: [Tile.STATE_ACTIVE] or
 * [Tile.STATE_INACTIVE], plus the matching "tap to …" subtitle.
 *
 * Both tiles in this package refresh the same way (state, subtitle,
 * [Tile.updateTile]), so the copy lives here once and each tile only
 * decides what "active" means for it.
 *
 * The subtitle is a Q+ API; older SystemUI never showed one, so the gate
 * just mirrors the platform rather than gating anything else.
 *
 * @param subtitleActive shown while the tile is active
 * @param subtitleInactive shown while the tile is inactive
 */
internal fun TileService.refreshTile(
	active: Boolean,
	@StringRes subtitleActive: Int,
	@StringRes subtitleInactive: Int
) {
	val tile = qsTile ?: return
	tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		tile.subtitle = getString(if (active) subtitleActive else subtitleInactive)
	}
	tile.updateTile()
}
