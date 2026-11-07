package com.alekseevich47.callmemo.telecom

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
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
        val account = PhoneAccount.builder(handle, "CallMemo")
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()
        runCatching { telecom.registerPhoneAccount(account) }
    }

    fun placeCall(activity: Activity, number: String): Boolean {
        val telecom = activity.getSystemService<TelecomManager>() ?: return false
        val uri = android.net.Uri.fromParts("tel", number, null)
        return runCatching {
            if (isDefaultDialer()) {
                telecom.placeCall(uri, null)
            } else {
                activity.startActivity(
                    Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            true
        }.getOrDefault(false)
    }
}
