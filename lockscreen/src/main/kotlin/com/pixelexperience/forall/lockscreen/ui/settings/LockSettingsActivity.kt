package com.pixelexperience.forall.lockscreen.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pixelexperience.forall.lockscreen.ui.theme.LockTheme

class LockSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LockTheme { LockSettingsScreen(onBack = { finish() }) } }
    }
}
