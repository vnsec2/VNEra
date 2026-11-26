/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.ui.tv

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

/** A QR code's modules, one bit each: the screen scales them and adds the quiet zone. */
internal object TvQr {
    fun modules(text: String): BitMatrix =
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
}
