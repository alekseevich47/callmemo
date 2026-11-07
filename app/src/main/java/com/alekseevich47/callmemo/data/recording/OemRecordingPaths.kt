package com.alekseevich47.callmemo.data.recording

import android.os.Build
import java.util.Locale

/**
 * Known OEM call-recording folders relative to shared storage root.
 * Runtime also scans MediaStore; user can pick a custom tree URI.
 */
object OemRecordingPaths {

    data class OemProfile(
        val brandKeys: Set<String>,
        val relativePaths: List<String>,
        val enableHintRu: String,
    )

    val profiles: List<OemProfile> = listOf(
        OemProfile(
            brandKeys = setOf("tecno", "infinix", "itel"),
            relativePaths = listOf(
                "Music/PhoneRecorod",
                "Music/PhoneRecord",
                "Call",
                "Recordings/Call",
                "Record/Call",
            ),
            enableHintRu = "Телефон → ⚙ → Автозапись вызовов (формулировка зависит от HiOS)",
        ),
        OemProfile(
            brandKeys = setOf("samsung"),
            relativePaths = listOf("Recordings/Call", "Call", "Music/Call"),
            enableHintRu = "Телефон Samsung → ⋮ → Настройки → Запись звонков",
        ),
        OemProfile(
            brandKeys = setOf("xiaomi", "redmi", "poco"),
            relativePaths = listOf("MIUI/sound_recorder/call_rec", "Recordings/Call"),
            enableHintRu = "Телефон Xiaomi → Настройки → Запись звонков",
        ),
        OemProfile(
            brandKeys = setOf("vivo", "iqoo"),
            relativePaths = listOf("Record/Call", "VoiceRecorder/Calls", "Call"),
            enableHintRu = "Телефон Vivo → Настройки записи вызовов",
        ),
        OemProfile(
            brandKeys = setOf("oppo", "realme"),
            relativePaths = listOf(
                "Music/Recordings/Call Recordings",
                "Recordings/Call Recordings",
                "Record/Call",
            ),
            enableHintRu = "Телефон ColorOS → Запись вызовов",
        ),
        OemProfile(
            brandKeys = setOf("oneplus"),
            relativePaths = listOf(
                "Music/Recordings/Call Recordings",
                "Record/PhoneRecord",
                "Recordings/Call Recordings",
            ),
            enableHintRu = "ODialer / Phone → Call recording",
        ),
        OemProfile(
            brandKeys = setOf("huawei", "honor"),
            relativePaths = listOf("Recordings/Call", "Call", "Sounds/CallRecord"),
            enableHintRu = "Телефон Huawei/Honor → Запись вызовов",
        ),
        OemProfile(
            brandKeys = setOf("motorola", "lenovo"),
            relativePaths = listOf("CallRecordings", "Music/CallRecordings", "Recordings"),
            enableHintRu = "Телефон Motorola → запись (если доступна в регионе)",
        ),
        OemProfile(
            brandKeys = setOf("sony"),
            relativePaths = listOf("Music/Call Recording", "Record/Call"),
            enableHintRu = "Телефон Sony → Call recording",
        ),
        OemProfile(
            brandKeys = setOf("nokia", "hmd"),
            relativePaths = listOf("Call", "Recordings/Call"),
            enableHintRu = "Часто Google Phone — запись только через Share",
        ),
        OemProfile(
            brandKeys = setOf("nothing"),
            relativePaths = emptyList(),
            enableHintRu = "Google Phone: запись в private storage — Share в CallMemo",
        ),
        OemProfile(
            brandKeys = setOf("asus"),
            relativePaths = listOf("Recordings/Call", "CallRecordings"),
            enableHintRu = "Телефон ASUS → Call recorder",
        ),
        OemProfile(
            brandKeys = setOf("zte", "nubia", "redmagic"),
            relativePaths = listOf("CallRecord", "Recordings/Call"),
            enableHintRu = "Телефон ZTE/Nubia → запись вызовов",
        ),
        OemProfile(
            brandKeys = setOf("meizu"),
            relativePaths = listOf("Recorder/Call", "Call"),
            enableHintRu = "Телефон Meizu → запись вызовов",
        ),
        OemProfile(
            brandKeys = setOf("lg"),
            relativePaths = listOf("Voice_Recorder/CallRec", "Call"),
            enableHintRu = "Телефон LG → Voice recorder / CallRec",
        ),
        OemProfile(
            brandKeys = setOf("google", "pixel"),
            relativePaths = emptyList(),
            enableHintRu = "Google Phone: нет публичной папки — Share / встроенный ADB / диктовка",
        ),
    )

    fun detectBrandKeys(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND,
        model: String = Build.MODEL,
    ): Set<String> {
        val blob = listOf(manufacturer, brand, model)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return profiles
            .flatMap { it.brandKeys }
            .filter { blob.contains(it) }
            .toSet()
            .ifEmpty { setOf("unknown") }
    }

    fun pathsForDevice(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND,
        model: String = Build.MODEL,
    ): List<String> {
        val keys = detectBrandKeys(manufacturer, brand, model)
        val matched = profiles.filter { profile -> profile.brandKeys.any { it in keys } }
        val specific = matched.flatMap { it.relativePaths }
        val fallback = listOf(
            "Call",
            "Recordings/Call",
            "Record/Call",
            "Music/PhoneRecord",
            "Music/PhoneRecorod",
            "Music/Recordings/Call Recordings",
            "MIUI/sound_recorder/call_rec",
        )
        return (specific + fallback).distinct()
    }

    fun hintForDevice(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND,
        model: String = Build.MODEL,
    ): String {
        val keys = detectBrandKeys(manufacturer, brand, model)
        return profiles.firstOrNull { profile -> profile.brandKeys.any { it in keys } }
            ?.enableHintRu
            ?: "Включите автозапись в штатном Phone, затем укажите папку в CallMemo"
    }
}
