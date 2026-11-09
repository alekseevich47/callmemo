package com.alekseevich47.callmemo.telecom

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DialerRoleHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isDefaultDialer(): Boolean {
        val telecom = context.getSystemService<TelecomManager>() ?: return false
        return telecom.defaultDialerPackage == context.packageName
    }

    fun createRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
        }
        val roleManager = context.getSystemService<RoleManager>() ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }

    fun registerPhoneAccount() {
        val telecom = context.getSystemService<TelecomManager>() ?: return
        val handle = PhoneAccountHandle(
            android.content.ComponentName(context, CallMemoConnectionService::class.java),
            "callmemo",
        )
        // CAPABILITY_CALL_PROVIDER keeps ROLE_DIALER happy on some OEMs, but real PSTN
        // must still use the SIM Telephony account (see placeCall).
        val account = PhoneAccount.builder(handle, "CallMemo")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL))
            .build()
        runCatching { telecom.registerPhoneAccount(account) }
    }

    fun placeCall(activity: Activity, number: String): Boolean {
        val telecom = activity.getSystemService<TelecomManager>() ?: return false
        val uri = android.net.Uri.fromParts("tel", number, null)
        return runCatching {
            if (isDefaultDialer()) {
                val extras = Bundle()
                resolveSimPhoneAccount(telecom)?.let { handle ->
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
                telecom.placeCall(uri, extras)
            } else {
                activity.startActivity(
                    Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Prefer a Telephony SIM PhoneAccount over our stub CallMemo ConnectionService so
     * outgoing calls are real carrier calls (and do not auto-ACTIVE in our stub).
     */
    private fun resolveSimPhoneAccount(telecom: TelecomManager): PhoneAccountHandle? {
        val own = context.packageName
        val capable = runCatching { telecom.callCapablePhoneAccounts }.getOrNull().orEmpty()
        val external = capable.firstOrNull { it.componentName.packageName != own }
        if (external != null) return external
        return runCatching {
            telecom.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
        }.getOrNull()?.takeIf { it.componentName.packageName != own }
    }
}
