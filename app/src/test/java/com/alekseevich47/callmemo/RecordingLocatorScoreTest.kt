package com.alekseevich47.callmemo

import com.alekseevich47.callmemo.data.recording.RecordingLocator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingLocatorScoreTest {

    @Test
    fun tecnoCallFileWithNumberScoresHigh() {
        val score = RecordingLocator.scoreCandidate(
            name = "Call_79001234567_20261107.m4a",
            relativePath = "Music/PhoneRecord/",
            digits = "79001234567",
        )
        assertThat(score).isAtLeast(RecordingLocator.MIN_ACCEPT_SCORE)
        assertThat(score).isAtLeast(12)
    }

    @Test
    fun randomMusicInSameWindowIsRejected() {
        val score = RecordingLocator.scoreCandidate(
            name = "Shape of You.mp3",
            relativePath = "Music/",
            digits = "79001234567",
        )
        assertThat(score).isLessThan(RecordingLocator.MIN_ACCEPT_SCORE)
    }

    @Test
    fun whatsappVoiceNoteIsRejected() {
        val score = RecordingLocator.scoreCandidate(
            name = "PTT-20261107.opus",
            relativePath = "WhatsApp/Media/WhatsApp Voice Notes/",
            digits = "79001234567",
        )
        assertThat(score).isLessThan(RecordingLocator.MIN_ACCEPT_SCORE)
    }

    @Test
    fun callKeywordInOemFolderAcceptedWithoutExactNumber() {
        val score = RecordingLocator.scoreCandidate(
            name = "call_recording_01.m4a",
            relativePath = "Recordings/Call/",
            digits = "79001234567",
        )
        assertThat(score).isAtLeast(RecordingLocator.MIN_ACCEPT_SCORE)
    }
}
