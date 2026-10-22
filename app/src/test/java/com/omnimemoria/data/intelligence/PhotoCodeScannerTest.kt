package com.omnimemoria.data.intelligence

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class PhotoCodeScannerTest {
    @Test fun readsCompleteUrlPayloadFromQrCode() {
        val payload = "https://example.com/store/details?id=com.example&hl=en"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 480, 480)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        }
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
            val found = PhotoCodeScanner.scan(bitmap)
            assertTrue("QR payload was not preserved: $found", found.any { payload in it.text })
        } finally {
            bitmap.recycle()
        }
    }
}
