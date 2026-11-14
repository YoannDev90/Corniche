package dev.yoanndev90.statusbarhider.overlay

import dev.yoanndev90.statusbarhider.core.settings.WidgetId
import dev.yoanndev90.statusbarhider.overlay.BarTapActions.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BarTapActionsTest {
	@Test
	fun `every widget has a tap target except the spacer`() {
		for (id in WidgetId.ALL) {
			val target = BarTapActions.targetOf(id)
			if (id == WidgetId.SPACER) {
				assertNull("$id must stay inert", target)
			} else {
				// A new widget lands here until it is wired into the table.
				assertNotNull("$id needs a tap target", target)
			}
		}
	}

	@Test
	fun `clock and alarm share the clock, date and calendar the calendar`() {
		assertEquals(Target.CLOCK, BarTapActions.targetOf(WidgetId.CLOCK))
		assertEquals(Target.CLOCK, BarTapActions.targetOf(WidgetId.ALARM))
		assertEquals(Target.CALENDAR, BarTapActions.targetOf(WidgetId.DATE))
		assertEquals(Target.CALENDAR, BarTapActions.targetOf(WidgetId.CALENDAR))
	}

	@Test
	fun `the remaining widgets each land on their own target`() {
		assertEquals(Target.NOTIFICATIONS, BarTapActions.targetOf(WidgetId.NOTIFS))
		assertEquals(Target.BATTERY, BarTapActions.targetOf(WidgetId.BATTERY))
		assertEquals(Target.DATA_USAGE, BarTapActions.targetOf(WidgetId.BANDWIDTH))
		assertEquals(Target.WIFI, BarTapActions.targetOf(WidgetId.CONNECTIVITY))
		assertEquals(Target.MEDIA, BarTapActions.targetOf(WidgetId.MEDIA))
	}
}
