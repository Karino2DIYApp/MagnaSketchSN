package io.github.karino2.supernote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SupernotePenCanvas(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {
    val backend by lazy { BitmapBackend(width, height) }
    var isBackendDirty = false

    val einkPW by lazy {
        EinkPixelWriter(this, ::onHardwareStrokeFinished)
    }

    fun onHardwareStrokeFinished(bitmap: Bitmap, dirty: Rect) {
        backend.drawBitmap(bitmap, dirty, dirty)
        einkPW.clearTransient(dirty)
        isBackendDirty = true
    }

    private fun visibleRect(): Rect {
        val limit = Rect()
        getLocalVisibleRect(limit)

        return limit
    }

    private var lastOpenedLimit: Rect? = null

    fun applyRawDrawingSettings() {
        val limit = visibleRect()
        if (limit.width() <= 0 || limit.height() <= 0) return

        // サイズが変わっていたら、バッファ不整合を防ぐため開き直す（黒画面対策の核心）
        if (isRawRenderingBecomesStale(limit)) {
            ensureCloseRawRendering()
        }

        if (!einkPW.isEnabled) {
            einkPW.open()
            einkPW.setPen(PenType.PENCIL, 4, Color.BLACK)
            einkPW.setWritableRect(limit)
            lastOpenedLimit = Rect(limit)

            backend.withLock {
                einkPW.loadBitmap(backend.bitmap)
            }
        } else {
            einkPW.setWritableRect(limit)
        }
    }

    fun ensureCloseRawRendering() {
        if (einkPW.isEnabled) {
            einkPW.isEnabled = false
        }
    }


    private fun isRawRenderingBecomesStale(limit: Rect): Boolean =
        einkPW.isEnabled && (lastOpenedLimit == null || limit.width() != lastOpenedLimit?.width() || limit.height() != lastOpenedLimit?.height())

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        einkPW.forwardEvent(event)
        return true
    }


    override fun onDraw(canvas: Canvas) {
        backend.withLock {
            canvas.drawBitmap(backend.bitmap, 0f, 0f, null);
        }
    }
}