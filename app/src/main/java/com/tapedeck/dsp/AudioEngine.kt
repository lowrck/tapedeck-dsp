package com.tapedeck.dsp

import android.content.Context
import android.net.Uri

enum class TapeType(val nativeValue: Int, val label: String) {
    TYPE_I(0, "Type I – Normal"),
    TYPE_II(1, "Type II – Chrome"),
    TYPE_IV(2, "Type IV – Metal"),
}

/** Kotlin-facing wrapper around the native TapeEngine (see app/src/main/cpp). */
class AudioEngine {

    private val handle: Long = nativeCreate()
    private var loadedSampleRate: Int = 48000

    var durationMs: Long = 0
        private set

    suspend fun load(context: Context, uri: Uri) {
        val decoded = AudioDecoder.decode(context, uri)
        loadedSampleRate = decoded.sampleRate
        durationMs = decoded.durationMs
        nativeLoadAudio(handle, decoded.samples, decoded.channelCount, decoded.sampleRate)
    }

    fun play() = nativePlay(handle)

    fun pause() = nativePause(handle)

    fun stop() = nativeStop(handle)

    fun seekToMs(ms: Long) {
        val frame = (ms * loadedSampleRate) / 1000
        nativeSeekToFrame(handle, frame)
    }

    val positionMs: Long
        get() = (nativeGetPositionFrames(handle) * 1000) / loadedSampleRate.coerceAtLeast(1)

    val isPlaying: Boolean
        get() = nativeIsPlaying(handle)

    fun setTapeAge(value01: Float) = nativeSetTapeAge(handle, value01)

    fun setDustDirt(value01: Float) = nativeSetDustDirt(handle, value01)

    fun setTapeType(type: TapeType) = nativeSetTapeType(handle, type.nativeValue)

    val vuLeft: Float get() = nativeGetVuLeft(handle)
    val vuRight: Float get() = nativeGetVuRight(handle)

    fun release() = nativeDestroy(handle)

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeLoadAudio(handle: Long, pcm: FloatArray, channelCount: Int, sampleRate: Int): Boolean
    private external fun nativePlay(handle: Long)
    private external fun nativePause(handle: Long)
    private external fun nativeStop(handle: Long)
    private external fun nativeSeekToFrame(handle: Long, frame: Long)
    private external fun nativeGetPositionFrames(handle: Long): Long
    private external fun nativeGetDurationFrames(handle: Long): Long
    private external fun nativeIsPlaying(handle: Long): Boolean
    private external fun nativeSetTapeAge(handle: Long, value: Float)
    private external fun nativeSetDustDirt(handle: Long, value: Float)
    private external fun nativeSetTapeType(handle: Long, type: Int)
    private external fun nativeGetVuLeft(handle: Long): Float
    private external fun nativeGetVuRight(handle: Long): Float

    companion object {
        init {
            System.loadLibrary("tapedeckdsp")
        }
    }
}
