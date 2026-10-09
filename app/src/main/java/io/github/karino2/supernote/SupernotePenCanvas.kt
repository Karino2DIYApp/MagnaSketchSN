package io.github.karino2.supernote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class SupernotePenCanvas(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {
    val backend by lazy { BitmapBackend(width, height) }

    val penEngine by lazy {
        SupernotePenEngine(
            this,
            SupernotePenEngine.Listener { b, r -> onHardwareStrokeFinished(b, r) })
    }

    init {
        penEngine.setTool(SupernotePenEngine.TOOL_PENCIL, Color.BLACK)
    }

    fun onHardwareStrokeFinished(bitmap: Bitmap, dirty: Rect) {
        backend.canvas.drawBitmap(bitmap, null, dirty, null)
        penEngine.clearTransient(dirty)
    }
}