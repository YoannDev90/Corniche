package dev.yoanndev90.statusbarhider.core

/**
 * Name of the SharedPreferences file the app writes its own state to: the
 * hide state ([dev.yoanndev90.statusbarhider.hide.HideInteractor]), the app
 * flags ([dev.yoanndev90.statusbarhider.data.AppFlagsStore]) and the OEM pick
 * ([dev.yoanndev90.statusbarhider.core.oem.OemConfig]) all live in it.
 *
 * Declared once because the file name *is* the storage: a rename applied in
 * one package only would silently split every value across two files.
 */
internal const val APP_PREFS_NAME = "statusbarhider"
