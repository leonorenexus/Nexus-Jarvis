package com.leonore.nexusjarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.leonore.nexusjarvis.data.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Speech-to-text. Semua pemanggilan harus dari main thread. Audio diproses oleh layanan pengenalan suara perangkat. */
class SpeechInputManager(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        lang: String,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onFail: (Int, String) -> Unit
    ) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) { onFail(error, describe(error)) }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onFail(SpeechRecognizer.ERROR_NO_MATCH, describe(SpeechRecognizer.ERROR_NO_MATCH)) else onResult(text)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        r.startListening(i)
    }

    fun finish() { recognizer?.stopListening() }

    fun stop() {
        recognizer?.let {
            try { it.cancel() } catch (e: Exception) {}
            it.destroy()
        }
        recognizer = null
    }

    companion object {
        fun describe(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "Masalah audio"
            SpeechRecognizer.ERROR_CLIENT -> "Kesalahan klien pengenal suara"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon tidak cukup"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Masalah jaringan pada pengenal suara"
            SpeechRecognizer.ERROR_NO_MATCH -> "Suara tidak dikenali"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Pengenal suara sibuk"
            SpeechRecognizer.ERROR_SERVER -> "Kesalahan server pengenal suara"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada suara terdeteksi"
            else -> "Error pengenal suara ($code)"
        }
    }
}

class SpeechOutputManager(private val context: Context, private val settings: SettingsRepository) {
    private var tts: TextToSpeech? = null
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) { utteranceId?.let { pending.remove(it)?.complete(Unit) } }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { utteranceId?.let { pending.remove(it)?.complete(Unit) } }
    }

    private fun ensureInit() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(listener)
                _ready.value = true
            }
        }
    }

    /** Membacakan teks. Jika [wait] true, menunggu selesai (maks 20 detik) agar mikrofon tidak menangkap suara TTS. */
    suspend fun speak(text: String, wait: Boolean = false) {
        val s = settings.current()
        if (!s.ttsEnabled || text.isBlank()) return
        withContext(Dispatchers.Main) { ensureInit() }
        if (withTimeoutOrNull(5_000) { ready.first { it } } == null) return
        val t = tts ?: return
        val id = "u${System.nanoTime()}"
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        withContext(Dispatchers.Main) {
            t.setLanguage(Locale.forLanguageTag(s.voiceLang))
            t.setSpeechRate(s.ttsRate)
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
        if (wait) withTimeoutOrNull(20_000) { done.await() }
    }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        _ready.value = false
    }
}

object WakeWordManager {
    const val DEDICATED_ENGINE_AVAILABLE = false
    const val STATUS_TEXT =
        "Mode frasa pemicu mendukung perintah bebas setelah kata pemicu: bahasa sehari-hari, pertanyaan, dan instruksi panjang. " +
            "Contoh: 'JARVIS, buka WhatsApp', 'Hai JARVIS, cek baterai dong', atau ucapkan 'JARVIS' lalu lanjutkan perintah setelah ia menjawab. " +
            "Pendeteksian memakai pengenal suara Android, bukan mesin wake-word lokal khusus; baterai dan jaringan dapat memengaruhi hasil."

    /** null = frasa tidak terdengar; empty = hanya frasa; selain itu = perintah bebas setelah frasa. */
    fun extractCommand(transcript: String, phrase: String): String? {
        val t = transcript.trim()
        if (t.isEmpty()) return null
        val configured = normalize(phrase)
        val candidates = linkedSetOf<String>()
        if (configured.isNotBlank()) candidates += configured
        // Common casual variants; the user can still configure a different trigger phrase.
        if (configured == "jarvis" || configured.isBlank()) {
            candidates += listOf("hey jarvis", "hai jarvis", "hei jarvis", "oi jarvis", "halo jarvis", "jarvis")
        }
        // Match normalized words while keeping the original character offset for command extraction.
        val lower = t.lowercase(Locale.ROOT)
        val match = candidates.mapNotNull { candidate ->
            val idx = findPhraseOffset(lower, candidate)
            if (idx >= 0) idx to candidate else null
        }.minByOrNull { it.first } ?: return null
        return t.substring(match.first + match.second.length).trim().trimStart(',', '.', ':', '!', '-', '?').trim()
    }

    private fun findPhraseOffset(text: String, normalizedPhrase: String): Int {
        val words = normalizedPhrase.split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return -1
        val pattern = words.joinToString("\\W+") { Regex.escape(it) }
        return Regex("(?<![\\p{L}\\p{N}])$pattern(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).find(text)?.range?.first ?: -1
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
