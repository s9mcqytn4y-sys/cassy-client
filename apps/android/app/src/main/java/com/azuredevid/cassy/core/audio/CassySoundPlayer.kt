package com.azuredevid.cassy.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import com.azuredevid.cassy.R

enum class CassyAudioCue {
    ORDER_PAID,
    NEW_QR_ORDER,
    CALL_WAITER,
    SOFT_ERROR
}

/**
 * Preloaded SoundPool audio cue player with graceful ToneGenerator fallback.
 * All WAV chimes in res/raw are synthesized license-free assets under 20 KB.
 */
class CassySoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var released = false
    private val soundPool: SoundPool? = runCatching {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }.getOrNull()

    private val loadedIds: Map<CassyAudioCue, Int> = if (soundPool != null) {
        mapOf(
            CassyAudioCue.ORDER_PAID to soundPool.load(appContext, R.raw.chime_order_paid, 1),
            CassyAudioCue.NEW_QR_ORDER to soundPool.load(appContext, R.raw.chime_new_qr_order, 1),
            CassyAudioCue.CALL_WAITER to soundPool.load(appContext, R.raw.chime_call_waiter, 1),
            CassyAudioCue.SOFT_ERROR to soundPool.load(appContext, R.raw.chime_soft_error, 1)
        )
    } else {
        emptyMap()
    }

    // One fallback generator, created on first use and freed in release(); a new one per cue leaked native audio.
    private var fallbackTone: ToneGenerator? = null

    fun play(cue: CassyAudioCue) {
        if (released) return
        val id = loadedIds[cue]
        val played = if (soundPool != null && id != null && id != 0) {
            soundPool.play(id, 0.85f, 0.85f, 1, 0, 1.0f) != 0
        } else {
            false
        }
        if (!played && cue == CassyAudioCue.SOFT_ERROR) {
            runCatching {
                val tone = fallbackTone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45).also { fallbackTone = it }
                tone.startTone(ToneGenerator.TONE_PROP_NACK, 120)
            }
        }
    }

    fun release() {
        released = true
        runCatching { soundPool?.release() }
        runCatching { fallbackTone?.release() }
        fallbackTone = null
    }
}
