package com.raja.bingehwa.domain

/** Quiet hours are fixed at 23:00 to 07:00 so the setting stays a single switch. */
object QuietHours {
    fun isQuiet(hourOfDay: Int, startHour: Int = 23, endHour: Int = 7): Boolean =
        if (startHour > endHour) hourOfDay >= startHour || hourOfDay < endHour
        else hourOfDay in startHour until endHour
}
