package com.alekseevich47.callmemo.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.alekseevich47.callmemo.MainActivity
import com.alekseevich47.callmemo.domain.model.CallDirection

/**
 * System InCall UI entry when CallMemo is the default dialer.
 */
class CallMemoInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallSessionTracker.onCallAdded(call)
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        val incoming = call.state == Call.STATE_RINGING
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            action = if (incoming) MainActivity.ACTION_SHOW_INCOMING else MainActivity.ACTION_SHOW_IN_CALL
            putExtra(MainActivity.EXTRA_NUMBER, number)
            putExtra(MainActivity.EXTRA_NAME, number)
            putExtra(
                MainActivity.EXTRA_DIRECTION,
                if (incoming) CallDirection.INCOMING.name else CallDirection.OUTGOING.name,
            )
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallSessionTracker.onCallRemoved(call)
    }
}

class CallMemoConnectionService : ConnectionService() {
    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = object : Connection() {
            override fun onAnswer() {
                setActive()
            }

            override fun onReject() {
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }
        connection.setInitializing()
        connection.setDialing()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(
            request?.address?.schemeSpecificPart,
            TelecomManager.PRESENTATION_ALLOWED,
        )
        connection.setActive()
        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = object : Connection() {
            override fun onAnswer() {
                setActive()
            }

            override fun onReject() {
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
                destroy()
            }

            override fun onDisconnect() {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
        }
        connection.setRinging()
        connection.setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
        return connection
    }
}

object CallSessionTracker {
    @Volatile
    var activeCall: Call? = null
        private set

    fun onCallAdded(call: Call) {
        activeCall = call
    }

    fun onCallRemoved(call: Call) {
        if (activeCall === call) activeCall = null
    }

    fun answer() {
        activeCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        activeCall?.reject(false, null)
    }

    fun disconnect() {
        activeCall?.disconnect()
    }
}
