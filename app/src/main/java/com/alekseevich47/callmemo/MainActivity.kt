package com.alekseevich47.callmemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alekseevich47.callmemo.telecom.DialerRoleHelper
import com.alekseevich47.callmemo.ui.CallMemoAppRoot
import com.alekseevich47.callmemo.ui.theme.CallMemoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dialerRoleHelper: DialerRoleHelper

    private var dialNumber by mutableStateOf<String?>(null)
    private var telecomAction by mutableStateOf<String?>(null)
    private var telecomName by mutableStateOf<String?>(null)
    private var telecomNumber by mutableStateOf<String?>(null)
    private var telecomDirection by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            CallMemoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CallMemoAppRoot(
                        dialerRoleHelper = dialerRoleHelper,
                        initialDialNumber = dialNumber,
                        telecomAction = telecomAction,
                        telecomName = telecomName,
                        telecomNumber = telecomNumber,
                        telecomDirection = telecomDirection,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_SHOW_INCOMING -> {
                telecomAction = "incoming"
                telecomName = intent.getStringExtra(EXTRA_NAME)
                telecomNumber = intent.getStringExtra(EXTRA_NUMBER)
            }
            ACTION_SHOW_IN_CALL -> {
                telecomAction = "in_call"
                telecomName = intent.getStringExtra(EXTRA_NAME)
                telecomNumber = intent.getStringExtra(EXTRA_NUMBER)
                telecomDirection = intent.getStringExtra(EXTRA_DIRECTION)
            }
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, Intent.ACTION_CALL_BUTTON -> {
                dialNumber = extractTel(intent.data) ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            }
            Intent.ACTION_CALL -> {
                dialNumber = extractTel(intent.data)
            }
        }
    }

    private fun extractTel(uri: Uri?): String? {
        if (uri == null) return null
        return when {
            uri.scheme.equals("tel", ignoreCase = true) -> uri.schemeSpecificPart
            else -> null
        }
    }

    companion object {
        const val ACTION_SHOW_INCOMING = "com.alekseevich47.callmemo.SHOW_INCOMING"
        const val ACTION_SHOW_IN_CALL = "com.alekseevich47.callmemo.SHOW_IN_CALL"
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_DIRECTION = "extra_direction"
    }
}
