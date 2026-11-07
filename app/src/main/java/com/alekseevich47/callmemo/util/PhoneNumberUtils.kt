package com.alekseevich47.callmemo.util

object PhoneNumberUtils {
    private val nonDialable = Regex("[^0-9+*#]")

    fun normalizeForDial(raw: String): String = raw.replace(nonDialable, "")

    fun isValidDialNumber(raw: String): Boolean {
        val n = normalizeForDial(raw)
        return n.length in 3..20
    }

    fun displayOrUnknown(name: String?, number: String): String =
        name?.takeIf { it.isNotBlank() } ?: number
}
