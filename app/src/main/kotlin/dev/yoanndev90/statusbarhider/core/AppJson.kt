package dev.yoanndev90.statusbarhider.core

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration every stored blob is encoded and decoded with:
 * [dev.yoanndev90.statusbarhider.core.settings.OverlayPrefsCodec],
 * [dev.yoanndev90.statusbarhider.core.profiles.ProfilesStore],
 * [dev.yoanndev90.statusbarhider.core.backup.SettingsBackup] and
 * [dev.yoanndev90.statusbarhider.core.oem.OemConfig] share this instance, so
 * an unknown key or a missing field can never mean different things in two of
 * them. Declared once because the flags *are* the format: a reader that
 * drifted from a writer would start rejecting or silently dropping fields.
 *
 * `encodeDefaults` only affects writing, and the OEM configs are parsed,
 * never encoded — sharing the instance changes nothing for them.
 */
internal val appJson = Json {
	ignoreUnknownKeys = true
	coerceInputValues = true
	encodeDefaults = true
}
