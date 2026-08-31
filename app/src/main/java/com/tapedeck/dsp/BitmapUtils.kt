package com.tapedeck.dsp

import android.graphics.Bitmap
import android.graphics.BitmapFactory

object BitmapUtils {

    /** Decodes only enough resolution to cover [targetSizePx], avoiding huge embedded-art allocations. */
    fun decodeSampledBitmap(bytes: ByteArray, targetSizePx: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= targetSizePx && bounds.outHeight / (sampleSize * 2) >= targetSizePx) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    } catch (t: Throwable) {
        null
    }
}
