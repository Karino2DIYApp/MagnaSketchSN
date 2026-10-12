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

class EinkPenCanvas(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {
    val backend by lazy { BitmapBackend(width, height) }
    var isBackendDirty = false

    val einkPW by lazy {
        EinkPixelWriter(this, ::onHardwareStrokeFinished)
    }

    fun onHardwareStrokeFinished(bitmap: Bitmap, dirty: Rect) {
        backend.drawBitmap(bitmap, dirty, dirty)
        // これが無いとrefreshした時にクリアされたバックエンドのバッファで上書きされてしまう。
        einkPW.clearTransient(dirty)
        isBackendDirty = true
    }

    private fun visibleRect(): Rect {
        val limit = Rect()
        getLocalVisibleRect(limit)

        return limit
    }

    private var lastOpenedLimit: Rect? = null

    private fun applyRawDrawingSettings() {
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
        _requestRawDrawingPending = false
    }

    var initialBmp: Bitmap? = null
    private var _requestRawDrawingPending = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        backend.resize(w, h)
        if (w != 0 && h != 0) {
            initialBmp?.let {
                backend.drawBitmap(it)
            }
            initialBmp = null
        }
        super.onSizeChanged(w, h, oldw, oldh)
        if (_requestRawDrawingPending)
        {
            _requestRawDrawingPending = false
            applyRawDrawingSettings()
        }
    }

    fun requestRawDrawing() {
        if (width != 0 && height != 0)
        {
            applyRawDrawingSettings()
            _requestRawDrawingPending = false
        } else {
            _requestRawDrawingPending = true
        }
    }


    private fun isRawRenderingBecomesStale(limit: Rect): Boolean =
        einkPW.isEnabled && (lastOpenedLimit == null || limit.width() != lastOpenedLimit?.width() || limit.height() != lastOpenedLimit?.height())

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // タッチは無視。スタイラスオンリー
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER)
            return super.onTouchEvent(event)
        
        einkPW.forwardEvent(event)
        return true
    }


    override fun onDraw(canvas: Canvas) {
        backend.withLock {
            canvas.drawBitmap(backend.bitmap, 0f, 0f, null);
        }
    }
}