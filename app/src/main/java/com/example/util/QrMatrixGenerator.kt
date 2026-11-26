package com.example.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

data class QrMatrix(
    val size: Int,
    val bits: BooleanArray
) {
    fun get(row: Int, col: Int): Boolean = bits[row * size + col]
}

/**
 * Generates genuine ISO-18004 scannable QR matrices using ZXing [QRCodeWriter].
 */
object QrMatrixGenerator {
    fun encode(content: String): QrMatrix {
        val normalized = content.trim().ifBlank { "https://ayer.app" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(normalized, BarcodeFormat.QR_CODE, 0, 0, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val size = maxOf(width, height)
        val bits = BooleanArray(size * size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bits[y * size + x] = bitMatrix.get(x, y)
            }
        }
        return QrMatrix(size = size, bits = bits)
    }
}
