package com.alekseevich47.callmemo

import com.alekseevich47.callmemo.data.recording.OemRecordingPaths
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OemRecordingPathsTest {
    @Test
    fun tecnoGetsTranssionFolders() {
        val paths = OemRecordingPaths.pathsForDevice(
            manufacturer = "TECNO",
            brand = "TECNO",
            model = "TECNO Camon",
        )
        assertThat(paths).contains("Music/PhoneRecorod")
        assertThat(paths).contains("Call")
    }

    @Test
    fun samsungGetsRecordingsCall() {
        val paths = OemRecordingPaths.pathsForDevice("samsung", "samsung", "SM-S911B")
        assertThat(paths).contains("Recordings/Call")
    }

    @Test
    fun unknownStillHasFallbackFolders() {
        val paths = OemRecordingPaths.pathsForDevice("WeirdCo", "Weird", "X1")
        assertThat(paths).isNotEmpty()
    }

    @Test
    fun detectBrandKeysForInfinix() {
        val keys = OemRecordingPaths.detectBrandKeys("INFINIX", "Infinix", "NOTE")
        assertThat(keys).contains("infinix")
    }
}
