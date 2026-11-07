package com.alekseevich47.callmemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.alekseevich47.callmemo.ui.CallMemoAppRoot
import com.alekseevich47.callmemo.ui.theme.CallMemoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallMemoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CallMemoAppRoot()
                }
            }
        }
    }
}
