package com.alekseevich47.callmemo

import com.alekseevich47.callmemo.util.PhoneNumberUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneNumberUtilsTest {
    @Test
    fun normalizeStripsFormatting() {
        assertThat(PhoneNumberUtils.normalizeForDial("+7 (900) 111-22-33"))
            .isEqualTo("+79001112233")
    }

    @Test
    fun rejectsTooShort() {
        assertThat(PhoneNumberUtils.isValidDialNumber("12")).isFalse()
    }

    @Test
    fun acceptsNormalMobile() {
        assertThat(PhoneNumberUtils.isValidDialNumber("+79001112233")).isTrue()
    }

    @Test
    fun displayFallsBackToNumber() {
        assertThat(PhoneNumberUtils.displayOrUnknown(null, "900")).isEqualTo("900")
        assertThat(PhoneNumberUtils.displayOrUnknown("Анна", "900")).isEqualTo("Анна")
    }

    @Test
    fun sameSubscriberIgnoresFormatting() {
        assertThat(PhoneNumberUtils.sameSubscriber("+7 900 111-22-33", "89001112233")).isTrue()
        assertThat(PhoneNumberUtils.sameSubscriber("+79001112233", "+79009998877")).isFalse()
    }
}
