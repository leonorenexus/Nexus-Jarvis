package com.leonore.nexusjarvis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.leonore.nexusjarvis.data.SettingsRepository
import com.leonore.nexusjarvis.ui.navigation.NexusNavHost
import com.leonore.nexusjarvis.ui.theme.NexusColors
import com.leonore.nexusjarvis.ui.theme.NexusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        setContent {
            NexusTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = NexusColors.Black) {
                    NexusNavHost(settings)
                }
            }
        }
    }
}
