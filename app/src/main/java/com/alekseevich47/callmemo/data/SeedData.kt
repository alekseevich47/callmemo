package com.alekseevich47.callmemo.data

import com.alekseevich47.callmemo.data.local.CalendarItemEntity
import com.alekseevich47.callmemo.data.local.CallLogEntity
import com.alekseevich47.callmemo.data.local.ContactEntity
import com.alekseevich47.callmemo.data.local.NoteEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object SeedData {
    private val zone: ZoneId = ZoneId.systemDefault()

    fun contacts(): List<ContactEntity> = listOf(
        ContactEntity(name = "Анна Смирнова", number = "+7 900 111-22-33"),
        ContactEntity(name = "Банк", number = "900"),
        ContactEntity(name = "Доктор Клиника", number = "+7 495 100-20-30"),
        ContactEntity(name = "Игорь Петров", number = "+7 926 555-66-77"),
        ContactEntity(name = "Мама", number = "+7 916 222-33-44"),
        ContactEntity(name = "Офис", number = "+7 495 777-88-99"),
    )

    fun calls(nowMs: Long = System.currentTimeMillis()): List<CallLogEntity> {
        val hour = 3_600_000L
        return listOf(
            CallLogEntity(
                contactName = "Анна Смирнова",
                number = "+7 900 111-22-33",
                direction = "OUTGOING",
                startedAtEpochMs = nowMs - hour,
                durationSeconds = 720,
                transcript = "Привет! Давай встретимся в пятницу в 19:00 в кафе «Уют»?",
                recordingUri = "demo://anna-1",
            ),
            CallLogEntity(
                contactName = "Банк",
                number = "900",
                direction = "INCOMING",
                startedAtEpochMs = nowMs - 5 * hour,
                durationSeconds = 180,
                transcript = "Напоминаем: завтра в 10:30 звонок по кредитной карте.",
                recordingUri = "demo://bank-1",
            ),
            CallLogEntity(
                contactName = "Игорь Петров",
                number = "+7 926 555-66-77",
                direction = "MISSED",
                startedAtEpochMs = nowMs - 26 * hour,
                durationSeconds = 0,
                transcript = null,
                recordingUri = null,
            ),
            CallLogEntity(
                contactName = "Мама",
                number = "+7 916 222-33-44",
                direction = "INCOMING",
                startedAtEpochMs = nowMs - 30 * hour,
                durationSeconds = 940,
                transcript = "Не забудь купить хлеб и молоко по дороге домой.",
                recordingUri = "demo://mom-1",
            ),
            CallLogEntity(
                contactName = "Офис",
                number = "+7 495 777-88-99",
                direction = "OUTGOING",
                startedAtEpochMs = nowMs - 50 * hour,
                durationSeconds = 420,
                transcript = "Сделай отчёт к понедельнику и пришли в общий чат.",
                recordingUri = "demo://office-1",
            ),
        )
    }

    fun calendarItems(today: LocalDate = LocalDate.now()): List<CalendarItemEntity> {
        fun at(date: LocalDate, hour: Int, minute: Int): Long =
            LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

        return listOf(
            CalendarItemEntity(
                title = "Встреча с Анной (кафе «Уют»)",
                notes = "Из звонка",
                startsAtEpochMs = at(today, 19, 0),
                type = "EVENT",
                remindMinutesBefore = 30,
            ),
            CalendarItemEntity(
                title = "Звонок в банк",
                notes = "Напомнить за 10 минут",
                startsAtEpochMs = at(today.plusDays(1), 10, 30),
                type = "REMINDER",
                remindMinutesBefore = 10,
            ),
            CalendarItemEntity(
                title = "Визит к врачу",
                notes = null,
                startsAtEpochMs = at(today.with(java.time.DayOfWeek.FRIDAY).let {
                    if (it.isBefore(today)) it.plusWeeks(1) else it
                }, 16, 0),
                type = "EVENT",
                remindMinutesBefore = 60,
            ),
            CalendarItemEntity(
                title = "Купить хлеб",
                notes = "Задача из звонка с мамой",
                startsAtEpochMs = at(today, 18, 0),
                type = "TASK",
                remindMinutesBefore = null,
            ),
        )
    }

    fun notes(nowMs: Long = System.currentTimeMillis()): List<NoteEntity> = listOf(
        NoteEntity(
            title = "Идеи по проекту",
            body = "CallMemo: OEM-запись → встроенный ADB → диктовка. STT on-device.",
            createdAtEpochMs = nowMs - 86_400_000L,
            relatedNumber = null,
        ),
        NoteEntity(
            title = "Список покупок",
            body = "Молоко, хлеб, яйца",
            createdAtEpochMs = nowMs - 3_600_000L,
            relatedNumber = "+7 916 222-33-44",
        ),
        NoteEntity(
            title = "Отчёт",
            body = "Сделать отчёт к понедельнику и отправить в общий чат.",
            createdAtEpochMs = nowMs - 7_200_000L,
            relatedNumber = "+7 495 777-88-99",
        ),
    )
}
