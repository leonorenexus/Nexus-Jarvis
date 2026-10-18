package com.leonore.nexusjarvis.ui.automation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leonore.nexusjarvis.automation.RoutineRepository
import com.leonore.nexusjarvis.automation.RoutineStep
import com.leonore.nexusjarvis.automation.StepFactory
import com.leonore.nexusjarvis.automation.StepsJson
import com.leonore.nexusjarvis.automation.TaskStatus
import com.leonore.nexusjarvis.container
import com.leonore.nexusjarvis.data.db.TaskEntity
import com.leonore.nexusjarvis.ui.components.ChoiceRow
import com.leonore.nexusjarvis.ui.components.ConfirmDialog
import com.leonore.nexusjarvis.ui.components.CyberCard
import com.leonore.nexusjarvis.ui.components.InfoRow
import com.leonore.nexusjarvis.ui.components.NeonButton
import com.leonore.nexusjarvis.ui.components.OutlineNeonButton
import com.leonore.nexusjarvis.ui.components.SectionTitle
import com.leonore.nexusjarvis.ui.components.StatusChip
import com.leonore.nexusjarvis.ui.theme.NexusColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private fun fmt(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

private fun repeatLabel(min: Int) = when (min) {
    0 -> "Sekali"
    15 -> "Tiap 15 menit"
    60 -> "Tiap jam"
    1440 -> "Harian"
    10080 -> "Mingguan"
    else -> "Tiap $min menit"
}

private class StepForm(tool: String, arg: String) {
    var tool by mutableStateOf(tool)
    var arg by mutableStateOf(arg)
}

@Composable
fun AutomationScreen(onOpenTask: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.container().routines
    val scope = rememberCoroutineScope()
    val tasks by repo.tasks.collectAsStateWithLifecycle(initialValue = emptyList())
    var editor by remember { mutableStateOf<TaskEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TaskEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NeonButton("Tugas / rutinitas baru", { editor = null; showEditor = true })
            Text(
                "Penjadwalan memakai WorkManager: waktu eksekusi bersifat perkiraan dan bisa terlambat beberapa menit akibat penghemat baterai. Tugas bertahan setelah reboot. Interval ulang minimum 15 menit.",
                color = NexusColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (tasks.isEmpty()) item { Text("Belum ada tugas.", color = NexusColors.Muted, fontSize = 13.sp) }
        items(tasks, key = { it.id }) { t ->
            CyberCard(Modifier.clickable { onOpenTask(t.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.title, color = NexusColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    StatusChip(t.status, t.status == TaskStatus.SUCCESS || t.status == TaskStatus.WAITING)
                }
                Text("${fmt(t.triggerAt)} • ${repeatLabel(t.repeatMinutes)}", color = NexusColors.Muted, fontSize = 12.sp)
                t.lastError?.let { Text("Error: $it", color = NexusColors.Red, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (t.status == TaskStatus.PAUSED || t.status == TaskStatus.CANCELLED) {
                        TextButton(onClick = { scope.launch { repo.resume(t.id) } }) { Text("Lanjut", color = NexusColors.Red) }
                    } else {
                        TextButton(onClick = { scope.launch { repo.pause(t.id) } }) { Text("Jeda", color = NexusColors.Red) }
                    }
                    TextButton(onClick = { editor = t; showEditor = true }) { Text("Edit", color = NexusColors.Red) }
                    TextButton(onClick = { deleteTarget = t }) { Text("Hapus", color = NexusColors.Muted) }
                }
            }
        }
    }

    deleteTarget?.let { t ->
        ConfirmDialog("Hapus tugas?", "\"${t.title}\" dan riwayatnya akan dihapus.", "Hapus", { scope.launch { repo.delete(t.id) } }, { deleteTarget = null })
    }

    if (showEditor) {
        TaskEditorDialog(
            initial = editor,
            onDismiss = { showEditor = false },
            onSave = { title, steps, triggerAt, repeat ->
                scope.launch {
                    val e = editor
                    if (e == null) repo.create(title, steps, triggerAt, repeat) else repo.edit(e.id, title, steps, triggerAt, repeat)
                }
                showEditor = false
            }
        )
    }
}

@Composable
private fun TaskEditorDialog(initial: TaskEntity?, onDismiss: () -> Unit, onSave: (String, List<RoutineStep>, Long, Int) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var triggerAt by remember { mutableStateOf(initial?.triggerAt ?: (System.currentTimeMillis() + 10 * 60_000L)) }
    var repeat by remember { mutableStateOf(initial?.repeatMinutes ?: 0) }
    var error by remember { mutableStateOf<String?>(null) }
    val steps = remember {
        mutableStateListOf<StepForm>().apply {
            val existing = initial?.let { StepsJson.decode(it.stepsJson) }.orEmpty()
            if (existing.isEmpty()) add(StepForm("show_notification", "")) else existing.forEach { add(StepForm(it.tool, StepFactory.argOf(it))) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Tugas baru" else "Edit tugas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Judul") }, singleLine = true)
                SectionTitle("LANGKAH")
                steps.forEachIndexed { index, step ->
                    var menu by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box {
                            TextButton(onClick = { menu = true }) { Text("${index + 1}. ${StepFactory.label(step.tool)} ▾", color = NexusColors.Red) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                StepFactory.tools.forEach { t ->
                                    DropdownMenuItem(text = { Text(StepFactory.label(t)) }, onClick = { step.tool = t; menu = false })
                                }
                            }
                        }
                        StepFactory.argLabel(step.tool)?.let { lbl ->
                            OutlinedTextField(value = step.arg, onValueChange = { step.arg = it }, label = { Text(lbl) }, singleLine = true)
                        }
                        if (steps.size > 1) TextButton(onClick = { steps.removeAt(index) }) { Text("Hapus langkah", color = NexusColors.Muted, fontSize = 12.sp) }
                    }
                }
                TextButton(onClick = { steps.add(StepForm("show_notification", "")) }) { Text("+ Tambah langkah", color = NexusColors.Red) }

                SectionTitle("WAKTU")
                Text(fmt(triggerAt), color = NexusColors.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlineNeonButton("Tanggal", {
                        val c = Calendar.getInstance().apply { timeInMillis = triggerAt }
                        DatePickerDialog(context, { _, y, m, d ->
                            val n = Calendar.getInstance().apply { timeInMillis = triggerAt; set(y, m, d) }
                            triggerAt = n.timeInMillis
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                    }, Modifier.weight(1f))
                    OutlineNeonButton("Jam", {
                        val c = Calendar.getInstance().apply { timeInMillis = triggerAt }
                        TimePickerDialog(context, { _, h, min ->
                            val n = Calendar.getInstance().apply { timeInMillis = triggerAt; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0) }
                            triggerAt = n.timeInMillis
                        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                    }, Modifier.weight(1f))
                }
                ChoiceRow("Pengulangan", listOf("Sekali" to 0, "15 mnt" to 15, "Jam" to 60, "Harian" to 1440, "Mingguan" to 10080), repeat) { repeat = it }
                error?.let { Text(it, color = NexusColors.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = title.trim()
                val problem = when {
                    t.isEmpty() -> "Judul wajib diisi"
                    steps.isEmpty() -> "Minimal satu langkah"
                    steps.any { StepFactory.argLabel(it.tool) != null && it.arg.isBlank() } -> "Semua langkah harus diisi"
                    initial == null && triggerAt <= System.currentTimeMillis() -> "Waktu harus di masa depan"
                    else -> null
                }
                if (problem != null) error = problem
                else onSave(t, steps.map { StepFactory.toStep(it.tool, it.arg.trim(), t) }, triggerAt, repeat)
            }) { Text("Simpan", color = NexusColors.Red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun TaskDetailScreen(taskId: Long, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container().routines
    val scope = rememberCoroutineScope()
    val task by repo.observe(taskId).collectAsStateWithLifecycle(initialValue = null)
    val runs by repo.runs(taskId).collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    val t = task
    if (t == null) {
        Text("Tugas tidak ditemukan.", color = NexusColors.Muted, modifier = Modifier.padding(16.dp))
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CyberCard {
            SectionTitle(t.title)
            InfoRow("ID", "#${t.id}")
            InfoRow("Status", t.status)
            InfoRow("Jadwal", fmt(t.triggerAt))
            InfoRow("Pengulangan", repeatLabel(t.repeatMinutes))
            t.lastRunAt?.let { InfoRow("Terakhir jalan", fmt(it)) }
            t.lastResult?.takeIf { it.isNotBlank() }?.let { Text("Hasil: $it", color = NexusColors.Text, fontSize = 12.sp) }
            t.lastError?.let { Text("Error: $it", color = NexusColors.Red, fontSize = 12.sp) }
        }
        CyberCard {
            SectionTitle("LANGKAH")
            StepsJson.decode(t.stepsJson).forEachIndexed { i, s ->
                Text("${i + 1}. ${StepFactory.label(s.tool)}${StepFactory.argOf(s).let { if (it.isBlank()) "" else ": $it" }}", color = NexusColors.Text, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (t.status == TaskStatus.PAUSED || t.status == TaskStatus.CANCELLED) {
                NeonButton("Lanjutkan", { scope.launch { repo.resume(t.id) } }, Modifier.weight(1f))
            } else {
                OutlineNeonButton("Jeda", { scope.launch { repo.pause(t.id) } }, Modifier.weight(1f))
                OutlineNeonButton("Batalkan", { scope.launch { repo.cancel(t.id) } }, Modifier.weight(1f))
            }
        }
        OutlineNeonButton("Hapus tugas", { confirmDelete = true })
        CyberCard {
            SectionTitle("RIWAYAT EKSEKUSI")
            if (runs.isEmpty()) Text("Belum pernah dijalankan.", color = NexusColors.Muted, fontSize = 12.sp)
            runs.forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(it.startedAt), color = NexusColors.Muted, fontSize = 12.sp)
                    StatusChip(it.status, it.status == TaskStatus.SUCCESS)
                }
                Text((it.result ?: it.error ?: "").take(200), color = NexusColors.Text, fontSize = 12.sp)
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Hapus tugas?", "Tugas dan riwayatnya akan dihapus.", "Hapus", { scope.launch { repo.delete(t.id); onDeleted() } }, { confirmDelete = false })
}
