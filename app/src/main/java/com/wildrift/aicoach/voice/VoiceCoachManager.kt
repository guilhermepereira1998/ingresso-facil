package com.wildrift.aicoach.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.wildrift.aicoach.brain.TacticalCallout
import java.util.Locale
import java.util.UUID
import java.util.PriorityQueue

/** All queue and TTS state is confined to the main thread. */
class VoiceCoachManager(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val queue = PriorityQueue(compareByDescending<TacticalCallout> { it.priority.weight }.thenBy { it.createdAtMs })
    private var ready = false
    private var speaking = false
    private var closed = false
    private var paused = false
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
        }, handler).build()
    private var tts: TextToSpeech? = null

    init {
        handler.post {
            if (!closed) tts = TextToSpeech(context.applicationContext) { result ->
                handler.post {
                    if (!closed && result == TextToSpeech.SUCCESS) {
                        val language = tts?.setLanguage(Locale("pt", "BR"))
                        ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
                        tts?.setSpeechRate(1.15f)
                        tts?.setAudioAttributes(attributes)
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) {}
                            override fun onDone(id: String?) { complete() }
                            @Deprecated("Deprecated in Java")
                            override fun onError(id: String?) { complete() }
                            override fun onStop(id: String?, interrupted: Boolean) { complete() }
                        })
                        drain()
                    }
                }
            }
        }
    }

    private fun complete() { handler.post {
        speaking = false
        audioManager.abandonAudioFocusRequest(focus)
        drain()
    } }
    fun enqueue(callout: TacticalCallout) { handler.post {
        if (!closed && !paused) {
            if (queue.size >= 16) queue.clear()
            queue.offer(callout)
            drain()
        }
    } }
    private fun drain() {
        if (closed || paused || !ready || speaking) return
        val now = System.currentTimeMillis()
        var next = queue.poll()
        while (next != null && now - next.createdAtMs > 4000L) next = queue.poll()
        if (next == null) return
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        speaking = true
        if (tts?.speak(next.message, TextToSpeech.QUEUE_ADD, Bundle(), UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) complete()
    }
    fun pause() { handler.post {
        if (!paused) {
            paused = true
            queue.clear()
            tts?.stop()
            speaking = false
            audioManager.abandonAudioFocusRequest(focus)
        }
    } }
    fun resume() { handler.post { paused = false; drain() } }
    fun shutdown() { handler.post {
        closed = true
        queue.clear()
        tts?.stop(); tts?.shutdown(); tts = null
        audioManager.abandonAudioFocusRequest(focus)
    } }
}
