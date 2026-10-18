package com.leonore.nexusjarvis.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.leonore.nexusjarvis.BuildConfig
import com.leonore.nexusjarvis.R
import com.leonore.nexusjarvis.ui.components.CyberCard
import com.leonore.nexusjarvis.ui.components.InfoRow
import com.leonore.nexusjarvis.ui.theme.NexusColors

@Composable
fun AboutScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CyberCard {
            Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = "Logo", modifier = Modifier.size(96.dp).align(Alignment.CenterHorizontally))
            Text("NEXUS JARVIS", style = MaterialTheme.typography.titleLarge, color = NexusColors.Red)
            InfoRow("Developer", "Leonore")
            InfoRow("Versi", BuildConfig.VERSION_NAME)
            InfoRow("Platform", "Android (Kotlin + Jetpack Compose)")
            Text(
                "Asisten AI personal dan game booster untuk perangkat milik sendiri. Fitur ditandai jujur sesuai status implementasinya; hal yang dibatasi Android dijelaskan di tiap halaman.",
                color = NexusColors.Muted
            )
        }
    }
}
