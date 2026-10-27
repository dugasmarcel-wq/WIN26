package rocks.gorjan.gokixp.apps.notepad

/** At most one local notification per hourly slot, for the first 24 hours. */
internal object ReminderSchedule {
    const val HOUR = 3_600_000L
    const val WINDOW = 24 * HOUR

    fun next(due: Long, now: Long): Long {
        if (due <= 0L || now >= due + WINDOW) return 0L
        if (now < due) return due
        val next = due + ((now - due) / HOUR + 1L) * HOUR
        return if (next < due + WINDOW) next else 0L
    }
}
