package com.alekseevich47.callmemo

import com.alekseevich47.callmemo.util.TimeFormat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimeFormatTest {
    @Test
    fun durationFormatsMinutesAndSeconds() {
        assertThat(TimeFormat.duration(125)).isEqualTo("2:05")
        assertThat(TimeFormat.duration(0)).isEqualTo("0:00")
    }

    @Test
    fun callWhenUsesYesterdayLabel() {
        val today = LocalDate.of(2026, 11, 7)
        val yesterday = today.minusDays(1)
            .atTime(15, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        assertThat(TimeFormat.callWhen(yesterday, today)).isEqualTo("Вчера")
    }

    @Test
    fun callWhenUsesClockForToday() {
        val today = LocalDate.of(2026, 11, 7)
        val instant = today.atTime(9, 5).atZone(ZoneId.systemDefault()).toInstant()
        assertThat(TimeFormat.callWhen(instant, today)).isEqualTo("09:05")
    }
}
