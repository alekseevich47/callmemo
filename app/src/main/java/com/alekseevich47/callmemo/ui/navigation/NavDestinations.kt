package com.alekseevich47.callmemo.ui.navigation

enum class TopLevelDestination(val label: String, val route: String) {
    CALLS("Звонки", "calls"),
    CONTACTS("Контакты", "contacts"),
    CALENDAR("Календарь", "calendar"),
    NOTES("Заметки", "notes"),
}

object Routes {
    const val CALL_DETAIL = "call_detail/{callId}"
    const val DIALPAD = "dialpad"
    const val SETTINGS = "settings"
    const val IN_CALL = "in_call/{name}/{number}/{direction}"
    const val INCOMING = "incoming/{name}/{number}"

    fun callDetail(callId: Long) = "call_detail/$callId"

    fun inCall(name: String, number: String, direction: String = "OUTGOING") =
        "in_call/${encode(name)}/${encode(number)}/$direction"

    fun incoming(name: String, number: String) =
        "incoming/${encode(name)}/${encode(number)}"

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
