package com.alekseevich47.callmemo.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle

/**
 * Stubs for ROLE_DIALER wiring. Full call UI state machine lands in a follow-up;
 * services are registered so the app can request default-dialer role safely.
 */
class CallMemoInCallService : InCallService()

class CallMemoConnectionService : ConnectionService() {
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection = Connection.createFailedConnection(
        android.telecom.DisconnectCause(android.telecom.DisconnectCause.ERROR, "Not implemented yet"),
    )

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection = Connection.createFailedConnection(
        android.telecom.DisconnectCause(android.telecom.DisconnectCause.ERROR, "Not implemented yet"),
    )
}
