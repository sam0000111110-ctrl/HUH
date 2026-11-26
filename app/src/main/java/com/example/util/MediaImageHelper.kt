package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compresses picked gallery photos into compact JPEG Base64 data URIs (< 175 KB)
 * for cross-device private chat delivery without requiring external storage buckets.
 */
object MediaImageHelper {

    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"

    suspend fun encodeUriToDataUri(
        context: Context,
        uriString: String,
        maxDim: Int = 720,
        maxBytes: Int = 175_000
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: return@withContext Result.failure(IllegalStateException("تعذر فتح الصورة المختارة."))

            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) {
                return@withContext Result.failure(IllegalStateException("ملف الصورة غير صالح."))
            }

            var sampleSize = 1
            while (max(srcW / sampleSize, srcH / sampleSize) > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            } ?: return@withContext Result.failure(IllegalStateException("تعذر قراءة بيانات الصورة."))

            val scaled = if (max(decoded.width, decoded.height) > maxDim) {
                val ratio = maxDim.toFloat() / max(decoded.width, decoded.height).toFloat()
                val dstW = max(1, (decoded.width * ratio).roundToInt())
                val dstH = max(1, (decoded.height * ratio).roundToInt())
                Bitmap.createScaledBitmap(decoded, dstW, dstH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            try {
                var quality = 82
                var bytes: ByteArray
                do {
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    bytes = baos.toByteArray()
                    quality -= 8
                } while (bytes.size > maxBytes && quality >= 45)

                if (bytes.size > maxBytes) {
                    return@withContext Result.failure(
                        IllegalStateException("حجم الصورة كبير جداً بعد الضغط. يرجى اختيار صورة أصغر.")
                    )
                }

                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Result.success("$DATA_URI_PREFIX$b64")
            } finally {
                scaled.recycle()
            }
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("تعذر معالجة الصورة المختارة: ${t.localizedMessage ?: "خطأ غير معروف"}"))
        }
    }

    fun toCoilModel(mediaUri: String?, mediaResId: Int?): Any? {
        val raw = mediaUri?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            if (raw.startsWith("data:image", ignoreCase = true)) {
                val commaIdx = raw.indexOf(',')
                if (commaIdx in 0 until raw.length - 1) {
                    val b64 = raw.substring(commaIdx + 1)
                    val decoded = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
                    if (decoded != null && decoded.isNotEmpty()) {
                        return decoded
                    }
                }
                return mediaResId
            }
            return raw
        }
        return mediaResId
    }
}
