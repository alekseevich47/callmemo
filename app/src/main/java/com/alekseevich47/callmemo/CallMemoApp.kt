package com.alekseevich47.callmemo

import android.app.Application
import com.alekseevich47.callmemo.telecom.CallSessionController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CallMemoApp : Application() {
    // Eagerly create so CallSessionTracker is attached before the first Telecom callback.
    @Inject
    lateinit var callSessionController: CallSessionController

    override fun onCreate() {
        super.onCreate()
        // Touch the singleton after Hilt member-injection (runs in super.onCreate).
        callSessionController.state
    }
}
