package com.alekseevich47.callmemo.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val locale = Locale("ru")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", locale)
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", locale)

    /**
     * Call log timestamp:
     * - today → `14:32`
     * - yesterday → `Вчера, 14:32`
     * - older → `6 нояб., 14:32`
     */
    fun callWhen(instant: Instant, today: LocalDate = LocalDate.now()): String {
        val local = LocalDateTime.ofInstant(instant, zone)
        val date = local.toLocalDate()
        val clock = timeFmt.format(local)
        return when (date) {
            today -> clock
            today.minusDays(1) -> "Вчера, $clock"
            else -> dateTimeFmt.format(local)
        }
    }

    fun full(instant: Instant): String =
        fullFmt.format(LocalDateTime.ofInstant(instant, zone))

    fun duration(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    fun monthTitle(yearMonth: java.time.YearMonth): String =
        yearMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
