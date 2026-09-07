package com.example.bruffwalkingtour

import android.content.Context
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.Locale

/**
 * Phase 1 audio guide: reads a stop's history aloud with the device's built-in
 * text-to-speech. No files, no network, works offline. A single shared engine
 * so narration started from the arrival card keeps going when the detail screen
 * opens, and both screens' buttons reflect the same state.
 *
 * No background service yet — playback stops when the whole app goes to the
 * background (that's Phase 2, along with real recorded narration).
 */
object NarrationPlayer {

    private const val UTTERANCE_ID = "stop_narration"

    private var tts: TextToSpeech? = null
    private var engineReady = false
    private var pendingText: String? = null

    /** True while narration is being spoken. */
    private val _speaking = MutableLiveData(false)
    val speaking: LiveData<Boolean> = _speaking

    /** True once a usable TTS engine + language is confirmed; drives button visibility. */
    private val _available = MutableLiveData(false)
    val available: LiveData<Boolean> = _available

    fun init(context: Context) {
        if (tts != null) return
        val appContext = context.applicationContext

        tts = TextToSpeech(appContext) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            if (engineReady) {
                var langOk = trySetLanguage(Locale.UK) || trySetLanguage(Locale.getDefault())
                if (!langOk) langOk = trySetLanguage(Locale.ENGLISH)
                engineReady = langOk
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = set(_speaking, true)
                    override fun onDone(utteranceId: String?) = set(_speaking, false)
                    @Deprecated("kept for older API levels")
                    override fun onError(utteranceId: String?) = set(_speaking, false)
                    override fun onError(utteranceId: String?, errorCode: Int) = set(_speaking, false)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = set(_speaking, false)
                })
                if (engineReady) pendingText?.let { speak(it) }
            }
            pendingText = null
            set(_available, engineReady)
        }

        // Stop when the app as a whole goes to the background — Phase 1 has no
        // media service to keep it going with the screen off.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) stop()
            },
        )
    }

    private fun trySetLanguage(locale: Locale): Boolean {
        val r = tts?.setLanguage(locale) ?: return false
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** Start narrating [text], or stop if it's already speaking. */
    fun toggle(text: String) {
        if (_speaking.value == true) stop() else speak(text)
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !engineReady) {
            pendingText = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        set(_speaking, true) // optimistic; onStart/onDone keep it honest
    }

    fun stop() {
        tts?.stop()
        set(_speaking, false)
    }

    private fun <T> set(target: MutableLiveData<T>, value: T) {
        if (Looper.myLooper() == Looper.getMainLooper()) target.value = value
        else target.postValue(value)
    }
}
