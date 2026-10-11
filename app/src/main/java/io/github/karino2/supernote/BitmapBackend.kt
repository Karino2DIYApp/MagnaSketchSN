package io.github.karino2.supernote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.createBitmap

/*
    BitmapとそのCanvasを保持するが、resizeも出来る。
 */
class BitmapBackend(iniWidth: Int, iniHeight: Int) {
    private var _bitmap = createBitmap(iniWidth, iniHeight).apply { eraseColor(Color.WHITE) }
    private var _canvas = Canvas(_bitmap)

    val bitmap : Bitmap
        get() = _bitmap
    val canvas : Canvas
        get() = _canvas


    fun drawBitmap(newbmp : Bitmap) = canvas.drawBitmap(newbmp, 0f, 0f, null)
    fun clear() {
        bitmap.eraseColor(Color.WHITE)
        canvas.drawColor(Color.WHITE)
    }

    fun resize(newWidth: Int, newHeight: Int) {
        if (_bitmap.width == newWidth && _bitmap.height == newHeight) return

        val oldbmp = bitmap
        val newbitmap = createBitmap(newWidth, newHeight).apply { eraseColor(Color.WHITE) }
        _canvas = Canvas(newbitmap)
        _canvas.drawBitmap(oldbmp,
            Rect(0, 0, oldbmp.width, oldbmp.height),
            Rect(0, 0, newWidth, newHeight),
            null)
        _bitmap = newbitmap
    }
}