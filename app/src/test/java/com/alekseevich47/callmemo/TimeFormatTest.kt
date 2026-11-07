package com.alekseevich47.callmemo

import com.alekseevich47.callmemo.util.TimeFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimeFormatTest {
    @Test
    fun durationFormatsMinutesAndSeconds() {
        assertThat(TimeFormat.duration(125)).isEqualTo("2:05")
        assertThat(TimeFormat.duration(0)).isEqualTo("0:00")
    }

    @Test
    fun callWhenUsesYesterdayWithTime() {
        val today = LocalDate.of(2026, 11, 7)
        val yesterday = today.minusDays(1)
            .atTime(15, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        assertThat(TimeFormat.callWhen(yesterday, today)).isEqualTo("Вчера, 15:00")
    }

    @Test
    fun callWhenUsesClockForToday() {
        val today = LocalDate.of(2026, 11, 7)
        val instant = today.atTime(9, 5).atZone(ZoneId.systemDefault()).toInstant()
        assertThat(TimeFormat.callWhen(instant, today)).isEqualTo("09:05")
    }

    @Test
    fun callWhenUsesDateAndTimeForOlder() {
        val today = LocalDate.of(2026, 11, 7)
        val older = LocalDate.of(2026, 11, 5)
            .atTime(18, 40)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        val formatted = TimeFormat.callWhen(older, today)
        assertThat(formatted).contains("18:40")
        assertThat(formatted).doesNotContain("Вчера")
    }
}
