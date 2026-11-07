package com.alekseevich47.callmemo.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))
    private val dayFmt = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
    private val fullFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("ru"))

    fun callWhen(instant: Instant, today: LocalDate = LocalDate.now()): String {
        val local = LocalDateTime.ofInstant(instant, zone)
        val date = local.toLocalDate()
        return when (date) {
            today -> timeFmt.format(local)
            today.minusDays(1) -> "Вчера"
            else -> dayFmt.format(local)
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
        yearMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
}
