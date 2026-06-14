package com.example.core.crypto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrCodeHelper {

    /**
     * Generates a modern styling QR Code bitmap.
     * Uses deep branding green instead of pure black, with strict margin for clear scanning.
     */
    fun generateQrCode(text: String, sizeInPixels: Int = 300): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H) // High error correction for center-logo or reliable scan
                put(EncodeHintType.MARGIN, 1)
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
            }

            val bitMatrix = MultiFormatWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                sizeInPixels,
                sizeInPixels,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Dynamic color: Brand Green 0xFF005129
            val qrColor = 0xFF005129.toInt()
            val bgColor = android.graphics.Color.WHITE

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) qrColor else bgColor)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Custom paints a beautiful, high-resolution AL-FAJAR security pass badge (ID card) with the QR code inside.
     * Perfect for printing, downloading as PNG, or sharing over chat apps.
     */
    fun generatePassBadge(
        ownerName: String,
        vehicleNo: String,
        cnic: String,
        expiryDate: String,
        issuedDate: String,
        qrBitmap: Bitmap
    ): Bitmap {
        val width = 640
        val height = 1000
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background Paint
        val bgPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        
        // Massive Outer Border Paint (M3-themed)
        val borderPaint = Paint().apply {
            color = 0xFF005129.toInt() // AL-FAJAR primary deep green
            style = Paint.Style.STROKE
            strokeWidth = 12f
            isAntiAlias = true
        }
        
        // Top Header Header paint
        val headerPaint = Paint().apply {
            color = 0xFF005129.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        val textHeaderTitle = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val textHeaderSubtitle = Paint().apply {
            color = android.graphics.Color.WHITE.and(0xD0FFFFFF.toInt()) // 80% white opacity
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        val labelPaint = Paint().apply {
            color = 0xFF475569.toInt() // slate-600
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val valuePaint = Paint().apply {
            color = 0xFF0F172A.toInt() // slate-900
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val vehicleNoBgPaint = Paint().apply {
            color = 0xFFF8FAFC.toInt() // slate-50
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        val vehicleNoTextPaint = Paint().apply {
            color = 0xFF005129.toInt()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        val footerNoticePaint = Paint().apply {
            color = 0xFF475569.toInt()
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val footerAppTag = Paint().apply {
            color = 0xFF94A3B8.toInt()
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // Draw solid background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Draw top header header bar
        canvas.drawRect(0f, 0f, width.toFloat(), 180f, headerPaint)
        
        // Top Titles
        canvas.drawText("AL-FAJAR VEHICLE PASS", width / 2f, 80f, textHeaderTitle)
        canvas.drawText("OFFLINE SECURITY DIGITAL CARD", width / 2f, 132f, textHeaderSubtitle)
        
        // Draw standard QR code
        val qrSize = 420
        val qrLeft = (width - qrSize) / 2
        val qrTop = 230
        val qrDestRect = Rect(qrLeft, qrTop, qrLeft + qrSize, qrTop + qrSize)
        canvas.drawBitmap(qrBitmap, null, qrDestRect, Paint(Paint.FILTER_BITMAP_FLAG))
        
        // Border enclosure for QR decoration
        val qrBorderBox = Paint().apply {
            color = 0xFFCBD5E1.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            (qrLeft - 10).toFloat(),
            (qrTop - 10).toFloat(),
            (qrLeft + qrSize + 10).toFloat(),
            (qrTop + qrSize + 10).toFloat(),
            12f, 12f,
            qrBorderBox
        )
        
        // Separator line
        val dividerPaint = Paint().apply {
            color = 0xFFEDF2F7.toInt()
            strokeWidth = 3f
        }
        canvas.drawLine(40f, 690f, (width - 40).toFloat(), 690f, dividerPaint)
        
        // Draw Bento-styled Pass details inside
        // Left details: Owner and CNIC
        canvas.drawText("OWNER / فائل مالک:", 52f, 742f, labelPaint)
        canvas.drawText(ownerName, 52f, 784f, valuePaint)
        
        canvas.drawText("CNIC / شناختی کارڈ:", 52f, 846f, labelPaint)
        canvas.drawText(cnic, 52f, 888f, valuePaint)
        
        // Right layouts: license plate design highlight
        val vehRectLeft = 390f
        val vehRectRight = (width - 46).toFloat()
        val vehRectTop = 720f
        val vehRectBottom = 810f
        val vehRect = RectF(vehRectLeft, vehRectTop, vehRectRight, vehRectBottom)
        canvas.drawRoundRect(vehRect, 14f, 14f, vehicleNoBgPaint)
        
        val vehBorderPaint = Paint().apply {
            color = 0xFF005129.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(vehRect, 14f, 14f, vehBorderPaint)
        canvas.drawText(vehicleNo, (vehRectLeft + vehRectRight) / 2f, 778f, vehicleNoTextPaint)
        
        // Expiry layout right column bottom
        val expRectLeft = 390f
        val expRectRight = (width - 46).toFloat()
        val expRectTop = 830f
        val expRectBottom = 890f
        val expRect = RectF(expRectLeft, expRectTop, expRectRight, expRectBottom)
        
        val expBgPaint = Paint().apply {
            color = 0xFFFFF1F2.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(expRect, 10f, 10f, expBgPaint)
        
        val expBorderPaint = Paint().apply {
            color = 0xFFBA1A1A.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(expRect, 10f, 10f, expBorderPaint)
        
        val expTextPaint = Paint().apply {
            color = 0xFFBA1A1A.toInt()
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("EXP: $expiryDate", (expRectLeft + expRectRight) / 2f, 866f, expTextPaint)
        
        // Footer labels
        canvas.drawText("SCAN WITH OFFICIAL AL-FAJAR SECURITY TERMINAL APP", width / 2f, 936f, footerNoticePaint)
        canvas.drawText("SECURE OFFLINE SYSTEM | ISSUED AT: $issuedDate", width / 2f, 962f, footerAppTag)
        
        // Massive outer border frame enclosing the entire card
        canvas.drawRoundRect(6f, 6f, (width - 6).toFloat(), (height - 6).toFloat(), 22f, 22f, borderPaint)
        
        return bitmap
    }
}
