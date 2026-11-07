package com.alekseevich47.callmemo.util

object PhoneNumberUtils {
    private val nonDialable = Regex("[^0-9+*#]")
    private val nonDigits = Regex("\\D")

    fun normalizeForDial(raw: String): String = raw.replace(nonDialable, "")

    fun digitsOnly(raw: String): String = raw.replace(nonDigits, "")

    fun isValidDialNumber(raw: String): Boolean {
        val n = normalizeForDial(raw)
        return n.length in 3..20
    }

    fun sameSubscriber(a: String, b: String, tail: Int = 10): Boolean {
        val left = digitsOnly(a).takeLast(tail)
        val right = digitsOnly(b).takeLast(tail)
        if (left.isEmpty() || right.isEmpty()) return false
        val n = minOf(left.length, right.length, tail)
        return left.takeLast(n) == right.takeLast(n) && n >= 7
    }

    fun displayOrUnknown(name: String?, number: String): String =
        name?.takeIf { it.isNotBlank() } ?: number
}
