package com.example.hamstertravelsapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reporting runs on 24 hour windows from midday to midday (UTC), each labelled with the date the
 * window starts. The window always runs from yesterday's midday to today's midday, so it tracks
 * the most recent night all day long: at 17:30 on 31 Oct it is 30 Oct 12:00 -> 31 Oct 12:00, just
 * closed; at 08:00 on 1 Nov it is 31 Oct 12:00 -> 1 Nov 12:00, still open until midday.
 */
object ReportingWindow {

    const val WINDOW_MS = 24L * 60 * 60 * 1000

    private const val BOUNDARY_HOUR = 12

    private val utc: TimeZone get() = TimeZone.getTimeZone("UTC")

    /**
     * Exclusive end of the reporting window: always today's midday. Before midday that is still in
     * the future and the window is open, which is intended - it keeps the most recent night on
     * screen in the morning rather than falling back to the night before.
     */
    fun reportingWindowEnd(nowMillis: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance(utc).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, BOUNDARY_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * Inclusive start of the window [index] windows before the reporting window, where index 0 is
     * the reporting window itself.
     */
    fun startOfWindow(windowEnd: Long, index: Int): Long =
        windowEnd - ((index + 1).toLong() * WINDOW_MS)

    /**
     * Which window [timestamp] falls in: 0 for the reporting window, 1 for the one before it, and
     * so on. Returns -1 for timestamps at or after [windowEnd], i.e. past today's midday.
     */
    fun windowIndexOf(timestamp: Long, windowEnd: Long): Int {
        val offset = windowEnd - timestamp
        if (offset <= 0L) return -1
        return ((offset - 1L) / WINDOW_MS).toInt()
    }

    /** Formats [millis] the way ThingSpeak's `start`/`end` query parameters expect (UTC). */
    fun toThingSpeakParam(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = utc
        }
        return formatter.format(Date(millis)).replace(" ", "%20")
    }
}
