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

    val penEngine by lazy {
        SupernotePenEngine(
            this,
            SupernotePenEngine.Listener { b, r -> onHardwareStrokeFinished(b, r) })
    }

    fun onHardwareStrokeFinished(bitmap: Bitmap, dirty: Rect) {
        backend.canvas.drawBitmap(bitmap, dirty, dirty, null)
        penEngine.clearTransient(dirty)
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

        if (!penEngine.isEnabled) {
            penEngine.isEnabled = true
            penEngine.setTool(SupernotePenEngine.TOOL_PENCIL, Color.BLACK)
            penEngine.setBitmapVisible(true)
            penEngine.setWritableRect(limit)
            lastOpenedLimit = Rect(limit)

            penEngine.loadBitmap(backend.bitmap.copy(Bitmap.Config.ARGB_8888, true))
        } else {
            penEngine.setWritableRect(limit)
        }
    }

    fun ensureCloseRawRendering() {
        if (penEngine.isEnabled) {
            penEngine.isEnabled = false
        }
    }


    private fun isRawRenderingBecomesStale(limit: Rect): Boolean =
        penEngine.isEnabled && (lastOpenedLimit == null || limit.width() != lastOpenedLimit?.width() || limit.height() != lastOpenedLimit?.height())

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        penEngine.forwardEvent(event)
        return true
    }


    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(backend.bitmap, 0f, 0f, null);
    }
}