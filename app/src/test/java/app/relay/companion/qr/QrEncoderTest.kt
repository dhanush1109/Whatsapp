package app.relay.companion.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The session screen re-encodes the payload WhatsApp Web puts in `data-ref` so the
 * login QR can be drawn at full screen size. A phone scanning it only accepts the
 * exact original string, so these tests decode our own output and compare.
 */
class QrEncoderTest {

    @Test
    fun linkPayloadDecodesBackToTheSameString() {
        // Same shape and length as a live linked-devices payload: a wa.me deep link
        // followed by 231 characters of base64-ish key material.
        val payload = "https://wa.me/settings/linked_devices#2@" +
            "G44B/1tODAbjQaasJtMsRN8E9s6Ti7po15j1dDc8FhKqW3zXbYnJ0iuEg7yVmA," +
            "PqRs+TuVwXyZ0123456789AbCdEfGhIjKlMnOpQrStUvWxYz/1a2B3c4D5e6F7g," +
            "hIjKlMnOpQrStUvWxYzAbCdEfGh0123456789+/PqRsTuVwXyZa1b2c3d4e5f6g," +
            "Zm9vYmFyYmF6cXV1eA==Kj7mQz2Xb9Ld4Vn8Tc5Rp1Ws=="
        assertEquals("payload length drifted from the real one", 277, payload.length)

        val matrix = QrEncoder.matrix(payload, 720)
        assertNotNull("encoder returned nothing for a real payload", matrix)

        assertEquals(payload, decode(matrix!!))
    }

    @Test
    fun matrixIsSquareAndAtLeastTheRequestedSize() {
        val matrix = QrEncoder.matrix("https://wa.me/settings/linked_devices#2@abc", 720)!!
        assertEquals(matrix.width, matrix.height)
        assert(matrix.width >= 720) { "expected at least 720px, got ${matrix.width}" }
    }

    @Test
    fun blankTextIsRejected() {
        assertNull(QrEncoder.matrix("   ", 720))
    }

    private fun decode(matrix: BitMatrix): String {
        val source = BitMatrixLuminanceSource(matrix)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return QRCodeReader()
            .decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true))
            .text
    }
}

/** Minimal grey-scale view over a [BitMatrix] so ZXing can read back what it wrote. */
private class BitMatrixLuminanceSource(
    private val matrix: BitMatrix,
) : LuminanceSource(matrix.width, matrix.height) {

    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val out = if (row != null && row.size >= width) row else ByteArray(width)
        for (x in 0 until width) {
            out[x] = if (matrix.get(x, y)) 0 else WHITE
        }
        return out
    }

    override fun getMatrix(): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                out[offset + x] = if (matrix.get(x, y)) 0 else WHITE
            }
        }
        return out
    }

    private companion object {
        const val WHITE = 0xFF.toByte()
    }
}
