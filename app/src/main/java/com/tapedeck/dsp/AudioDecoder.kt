package com.tapedeck.dsp

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DecodedAudio(
    val samples: FloatArray,
    val channelCount: Int,
    val sampleRate: Int,
    val durationMs: Long
)

/**
 * Decodes a local audio file (MP3/AAC/FLAC/WAV, whatever the platform codecs
 * support) into interleaved float PCM using the device's MediaCodec, so no
 * third-party decoding library is needed. The whole file is decoded up front,
 * which keeps the native playback/DSP path simple at the cost of holding the
 * full track in memory - fine for MVP-length tracks.
 */
object AudioDecoder {

    suspend fun decode(context: Context, uri: Uri): DecodedAudio = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (t: Throwable) {
            throw IllegalArgumentException("This doesn't look like a supported audio file.", t)
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(i)
            val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = candidate
                break
            }
        }
        val selectedFormat = requireNotNull(format) { "No audio track found in $uri" }
        extractor.selectTrack(trackIndex)

        val mime = requireNotNull(selectedFormat.getString(MediaFormat.KEY_MIME))
        val sampleRate = selectedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = selectedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = if (selectedFormat.containsKey(MediaFormat.KEY_DURATION)) {
            selectedFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(selectedFormat, null, null, 0)
        codec.start()

        val pcmChunks = ArrayList<FloatArray>()
        var totalSamples = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val chunk = pcm16ToFloat(outputBuffer)
                    pcmChunks.add(chunk)
                    totalSamples += chunk.size
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEos = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val samples = FloatArray(totalSamples)
        var offset = 0
        for (chunk in pcmChunks) {
            System.arraycopy(chunk, 0, samples, offset, chunk.size)
            offset += chunk.size
        }

        DecodedAudio(samples, channelCount, sampleRate, durationUs / 1000)
    }

    private fun pcm16ToFloat(buffer: ByteBuffer): FloatArray {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = buffer.asShortBuffer()
        val out = FloatArray(shortBuffer.remaining())
        for (i in out.indices) {
            out[i] = shortBuffer.get(i) / 32768.0f
        }
        return out
    }
}
