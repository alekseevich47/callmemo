package com.alekseevich47.callmemo

import com.alekseevich47.callmemo.util.PhoneNumberUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure logic covering in-call display-name resolution rules used by InCall UI.
 */
class InCallStatusLogicTest {
    @Test
    fun prefersContactNameOverNumber() {
        assertThat(PhoneNumberUtils.displayOrUnknown("Анна", "+79001112233"))
            .isEqualTo("Анна")
    }

    @Test
    fun fallsBackToNumberWhenNameMissing() {
        assertThat(PhoneNumberUtils.displayOrUnknown(null, "+79001112233"))
            .isEqualTo("+79001112233")
        assertThat(PhoneNumberUtils.displayOrUnknown("  ", "+79001112233"))
            .isEqualTo("+79001112233")
    }

    @Test
    fun treatsFormattedCallerIdAsSameSubscriber() {
        assertThat(PhoneNumberUtils.sameSubscriber("+7 900 111-22-33", "89001112233")).isTrue()
    }
}
