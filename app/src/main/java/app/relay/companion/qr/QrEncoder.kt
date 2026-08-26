package app.relay.companion.qr

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrEncoder {
    private val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )

    /** Separated from [encode] so the encoding itself can be tested off-device. */
    fun matrix(text: String, size: Int): BitMatrix? {
        if (text.isBlank()) return null
        return runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        }.getOrNull()
    }

    fun encode(
        text: String,
        size: Int = 1024,
        foreground: Int = 0xFF0F172A.toInt(),
        background: Int = 0xFFFFFFFF.toInt(),
    ): Bitmap? {
        val matrix = matrix(text, size) ?: return null
        return runCatching {
            val bitmap = createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    bitmap[x, y] = if (matrix[x, y]) foreground else background
                }
            }
            bitmap
        }.getOrNull()
    }
}
