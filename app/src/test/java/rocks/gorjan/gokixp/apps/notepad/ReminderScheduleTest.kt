package rocks.gorjan.gokixp.apps.notepad

import org.junit.Assert.*
import org.junit.Test

class ReminderScheduleTest {
    private val due = 1_800_000_000_000L
    @Test fun futureReminderRetainsItsOriginalTime() {
        assertEquals(due, ReminderSchedule.next(due, due - 5000L))
    }
    @Test fun lateDeliveryContinuesFromDueTimeWithoutBurstCatchup() {
        assertEquals(due + 4 * ReminderSchedule.HOUR,
            ReminderSchedule.next(due, due + 3 * ReminderSchedule.HOUR + 1000L))
    }
    @Test fun notificationsStopAfterTheTwentyFourthSlot() {
        for (hour in 0..22) assertEquals(due + (hour + 1) * ReminderSchedule.HOUR,
            ReminderSchedule.next(due, due + hour * ReminderSchedule.HOUR))
        assertEquals(0L, ReminderSchedule.next(due, due + 23 * ReminderSchedule.HOUR))
        assertEquals(0L, ReminderSchedule.next(due, due + 24 * ReminderSchedule.HOUR))
        assertEquals(0L, ReminderSchedule.next(0L, due))
    }
}
