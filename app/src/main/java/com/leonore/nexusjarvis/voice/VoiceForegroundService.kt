package com.leonore.nexusjarvis.voice

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.leonore.nexusjarvis.R
import com.leonore.nexusjarvis.container
import com.leonore.nexusjarvis.notifications.NotificationHelper
import com.leonore.nexusjarvis.security.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

enum class VoiceState { STOPPED, LISTENING, PAUSED, PROCESSING, ERROR }

object VoiceServiceState {
    val state = MutableStateFlow(VoiceState.STOPPED)
    val heard = MutableStateFlow("")
    val lastReply = MutableStateFlow("")
    val error = MutableStateFlow<String?>(null)
}

/**
 * Mode frasa pemicu: mendengarkan terus-menerus selama pengguna mengaktifkannya dan notifikasi permanen tampil.
 * Tidak ada audio yang disimpan; transkrip hanya dipakai untuk mengeksekusi perintah.
 */
class VoiceForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var input: SpeechInputManager
    private var running = false
    private var paused = false
    private var awaiting = false
    private var errors = 0

    override fun onCreate() {
        super.onCreate()
        input = SpeechInputManager(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> shutdown(null)
            else -> start()
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): android.app.Notification {
        fun action(act: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, VoiceForegroundService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, NotificationHelper.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_nexus)
            .setContentTitle("JARVIS: mikrofon aktif")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(NotificationHelper.appIntent(this))
        if (paused) b.addAction(0, "Lanjutkan", action(ACTION_RESUME, 11)) else b.addAction(0, "Jeda", action(ACTION_PAUSE, 12))
        b.addAction(0, "Berhenti", action(ACTION_STOP, 13))
        return b.build()
    }

    private fun refreshNotification(text: String) {
        try {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                NotificationManagerCompat.from(this).notify(NOTIF_ID, notification(text))
            }
        } catch (e: SecurityException) {}
    }

    private fun start() {
        if (running) return
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification("Mendengarkan frasa pemicu"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            VoiceServiceState.state.value = VoiceState.ERROR
            VoiceServiceState.error.value = "Tidak dapat memulai layanan: ${e.message}"
            stopSelf()
            return
        }
        if (!PermissionManager.microphoneGranted(this)) { shutdown("Izin mikrofon belum diberikan"); return }
        if (!input.isAvailable()) { shutdown("Pengenalan suara tidak tersedia di perangkat ini"); return }
        running = true
        paused = false
        awaiting = false
        errors = 0
        VoiceServiceState.error.value = null
        listen()
    }

    private fun listen() {
        if (!running || paused) return
        scope.launch {
            val s = container().settings.current()
            VoiceServiceState.state.value = VoiceState.LISTENING
            refreshNotification("Mendengarkan frasa \"${s.wakePhrase}\"")
            input.start(
                s.voiceLang,
                onPartial = { VoiceServiceState.heard.value = it },
                onResult = { text -> errors = 0; onTranscript(text) },
                onFail = { code, msg -> onRecognizerError(code, msg) }
            )
        }
    }

    private fun restartSoon(extraMs: Long = 0L) {
        scope.launch {
            val delaySec = container().settings.current().listenRestartDelaySec
            delay(delaySec * 1000L + extraMs)
            listen()
        }
    }

    private fun onTranscript(text: String) {
        scope.launch {
            val c = container()
            val s = c.settings.current()
            val cmd = if (awaiting) text.trim() else WakeWordManager.extractCommand(text, s.wakePhrase)
            if (cmd == null) { restartSoon(); return@launch }
            if (cmd.isBlank()) {
                awaiting = true
                c.speechOut.speak("Ya?", wait = true)
                restartSoon()
                return@launch
            }
            awaiting = false
            VoiceServiceState.state.value = VoiceState.PROCESSING
            VoiceServiceState.heard.value = cmd
            val outcome = c.engine.handle(cmd, emptyList(), allowSensitive = false)
            outcome.pending().forEach { c.pendingActions.add(it) }
            VoiceServiceState.lastReply.value = outcome.render()
            c.speechOut.speak(outcome.spoken(), wait = true)
            restartSoon()
        }
    }

    private fun onRecognizerError(code: Int, msg: String) {
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {}
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { shutdown("Izin mikrofon dicabut"); return }
            else -> { errors++; VoiceServiceState.error.value = msg }
        }
        if (errors >= 8) { shutdown("Terlalu banyak error pengenalan suara: $msg"); return }
        restartSoon(if (errors > 0) 3_000L else 0L)
    }

    private fun pause() {
        if (!running) return
        paused = true
        input.stop()
        VoiceServiceState.state.value = VoiceState.PAUSED
        refreshNotification("Dijeda; mikrofon tidak digunakan")
    }

    private fun resume() {
        if (!running) return
        paused = false
        listen()
    }

    private fun shutdown(error: String?) {
        running = false
        input.stop()
        VoiceServiceState.state.value = if (error != null) VoiceState.ERROR else VoiceState.STOPPED
        VoiceServiceState.error.value = error
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        input.stop()
        scope.cancel()
        if (VoiceServiceState.state.value != VoiceState.ERROR) VoiceServiceState.state.value = VoiceState.STOPPED
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "com.leonore.nexusjarvis.VOICE_PAUSE"
        const val ACTION_RESUME = "com.leonore.nexusjarvis.VOICE_RESUME"
        const val ACTION_STOP = "com.leonore.nexusjarvis.VOICE_STOP"
        private const val NOTIF_ID = 3001

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, VoiceForegroundService::class.java))
            true
        } catch (e: Exception) {
            false
        }

        fun command(context: Context, action: String) {
            try { context.startService(Intent(context, VoiceForegroundService::class.java).setAction(action)) } catch (e: Exception) {}
        }
    }
}
