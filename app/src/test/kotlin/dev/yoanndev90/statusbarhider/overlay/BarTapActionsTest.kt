package dev.yoanndev90.statusbarhider.overlay

import dev.yoanndev90.statusbarhider.core.settings.TapAction
import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BarTapActionsTest {
	@Test
	fun `every widget has a default action except the spacer`() {
		for (id in WidgetId.ALL) {
			val default = BarTapActions.defaultOf(id)
			if (id == WidgetId.SPACER) {
				assertNull("$id must stay inert", default)
			} else {
				// A new widget lands here until it is wired into the table.
				assertNotNull("$id needs a default action", default)
			}
		}
	}

	@Test
	fun `clock and alarm share the clock, date and calendar the calendar`() {
		assertEquals(TapAction.CLOCK, BarTapActions.defaultOf(WidgetId.CLOCK))
		assertEquals(TapAction.CLOCK, BarTapActions.defaultOf(WidgetId.ALARM))
		assertEquals(TapAction.CALENDAR, BarTapActions.defaultOf(WidgetId.DATE))
		assertEquals(TapAction.CALENDAR, BarTapActions.defaultOf(WidgetId.CALENDAR))
	}

	@Test
	fun `the remaining widgets each land on their own default`() {
		assertEquals(TapAction.NOTIFICATIONS, BarTapActions.defaultOf(WidgetId.NOTIFS))
		assertEquals(TapAction.BATTERY, BarTapActions.defaultOf(WidgetId.BATTERY))
		assertEquals(TapAction.DATA_USAGE, BarTapActions.defaultOf(WidgetId.BANDWIDTH))
		assertEquals(TapAction.WIFI, BarTapActions.defaultOf(WidgetId.CONNECTIVITY))
		assertEquals(TapAction.MEDIA, BarTapActions.defaultOf(WidgetId.MEDIA))
	}

	@Test
	fun `an override wins over the default and NONE disarms the widget`() {
		val overrides = mapOf(WidgetId.CLOCK to TapAction.SCREENSHOT, WidgetId.BATTERY to TapAction.NONE)
		assertEquals(TapAction.SCREENSHOT, BarTapActions.resolve(WidgetId.CLOCK, overrides))
		assertEquals(TapAction.NONE, BarTapActions.resolve(WidgetId.BATTERY, overrides))
		// Untouched widgets keep their built-in target...
		assertEquals(TapAction.WIFI, BarTapActions.resolve(WidgetId.CONNECTIVITY, overrides))
		// ...and an unknown id stays inert whatever the map says.
		assertNull(BarTapActions.resolve(WidgetId.SPACER, mapOf(WidgetId.SPACER to TapAction.TORCH)))
		assertNull(BarTapActions.resolve("future_widget", emptyMap()))
	}

	@Test
	fun `no override falls back to the built-in defaults`() {
		for (id in WidgetId.ALL) {
			assertEquals("resolve without override diverged on $id", BarTapActions.defaultOf(id), BarTapActions.resolve(id, emptyMap()))
		}
	}
}
