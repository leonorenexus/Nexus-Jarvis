package com.leonore.nexusjarvis.ui.appcontrol

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leonore.nexusjarvis.container
import com.leonore.nexusjarvis.data.AppSettings
import com.leonore.nexusjarvis.data.SettingKeys
import com.leonore.nexusjarvis.ui.components.CyberCard
import com.leonore.nexusjarvis.ui.components.OutlineNeonButton
import com.leonore.nexusjarvis.ui.components.SectionTitle
import com.leonore.nexusjarvis.ui.theme.NexusColors
import com.leonore.nexusjarvis.util.ActivityLauncher
import com.leonore.nexusjarvis.util.AppIndex
import com.leonore.nexusjarvis.util.FileSearch
import com.leonore.nexusjarvis.util.LaunchableApp
import com.leonore.nexusjarvis.util.LaunchOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val quickSettings = listOf(
    "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
    "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
    "Baterai" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
    "Layar" to Settings.ACTION_DISPLAY_SETTINGS,
    "Suara" to Settings.ACTION_SOUND_SETTINGS,
    "Penyimpanan" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
    "Lokasi" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
    "Aplikasi" to Settings.ACTION_APPLICATION_SETTINGS
)

@Composable
fun AppControlScreen() {
    val context = LocalContext.current
    val c = context.container()
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var fileQuery by remember { mutableStateOf("") }
    var fileResults by remember { mutableStateOf<List<String>?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { AppIndex.list(context.packageManager) } }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                scope.launch { c.settings.setString(SettingKeys.SAF_TREE, uri.toString()) }
            } catch (e: SecurityException) {
                fileError = "Gagal menyimpan izin folder: ${e.message}"
            }
        }
    }

    val shown = if (query.isBlank()) emptyList() else apps.filter { it.label.contains(query, ignoreCase = true) }.take(15)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CyberCard {
            SectionTitle("BUKA APLIKASI")
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Cari aplikasi terpasang") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (query.isNotBlank() && shown.isEmpty()) Text("Tidak ada aplikasi cocok.", color = NexusColors.Muted, fontSize = 12.sp)
            shown.forEach { app ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(app.label, color = NexusColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val intent = AppIndex.launchIntent(context.packageManager, app.packageName)
                        val out = if (intent == null) LaunchOutcome.FAILED else ActivityLauncher.launch(context, intent, "Buka ${app.label}")
                        if (out == LaunchOutcome.FAILED) Toast.makeText(context, "Gagal membuka ${app.label}", Toast.LENGTH_SHORT).show()
                    }) { Text("Buka", color = NexusColors.Red) }
                }
            }
        }

        CyberCard {
            SectionTitle("HALAMAN PENGATURAN")
            quickSettings.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    pair.forEach { (label, action) ->
                        OutlineNeonButton(label, {
                            if (ActivityLauncher.launch(context, Intent(action), "Pengaturan $label") == LaunchOutcome.FAILED) {
                                Toast.makeText(context, "Halaman tidak tersedia", Toast.LENGTH_SHORT).show()
                            }
                        }, Modifier.weight(1f))
                    }
                }
            }
        }

        CyberCard {
            SectionTitle("CARI FILE (FOLDER PILIHAN ANDA)")
            Text(
                "Pencarian hanya di folder yang Anda pilih lewat Storage Access Framework. Aplikasi tidak meminta akses penyimpanan penuh.",
                color = NexusColors.Muted, fontSize = 12.sp
            )
            Text(if (settings.safTreeUri.isBlank()) "Folder: belum dipilih" else "Folder: ${Uri.parse(settings.safTreeUri).lastPathSegment}", color = NexusColors.Text, fontSize = 12.sp)
            OutlineNeonButton("Pilih folder", { pickFolder.launch(null) })
            OutlinedTextField(value = fileQuery, onValueChange = { fileQuery = it }, label = { Text("Nama file") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlineNeonButton("Cari", {
                fileError = null
                if (settings.safTreeUri.isBlank()) { fileError = "Pilih folder terlebih dahulu" }
                else scope.launch {
                    fileResults = null
                    val r = withContext(Dispatchers.IO) {
                        try { FileSearch.search(context, Uri.parse(settings.safTreeUri), fileQuery) } catch (e: Exception) { null }
                    }
                    if (r == null) fileError = "Gagal membaca folder. Pilih ulang folder." else fileResults = r
                }
            })
            fileError?.let { Text(it, color = NexusColors.Red, fontSize = 12.sp) }
            fileResults?.let { r ->
                if (r.isEmpty()) Text("Tidak ada file cocok.", color = NexusColors.Muted, fontSize = 12.sp)
                r.forEach { Text("• $it", color = NexusColors.Text, fontSize = 12.sp) }
            }
        }
    }
}
