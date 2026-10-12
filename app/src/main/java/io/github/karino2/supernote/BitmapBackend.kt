package io.github.karino2.supernote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import kotlin.concurrent.withLock

/*
    BitmapとそのCanvasを保持するが、resizeも出来る。
 */
class BitmapBackend(iniWidth: Int, iniHeight: Int) {
    val bitmapLock = java.util.concurrent.locks.ReentrantLock()

    private var _bitmap = createBitmap(iniWidth, iniHeight).apply { eraseColor(Color.WHITE) }
    private var canvas = Canvas(_bitmap)

    val bitmap : Bitmap
        get() = _bitmap


    fun drawBitmap(newbmp : Bitmap) = withLock { canvas.drawBitmap(newbmp, 0f, 0f, null) }
    fun drawBitmap(newbmp : Bitmap, src: Rect, dest: Rect) = withLock { canvas.drawBitmap(newbmp, src, dest, null) }

    fun clear() {
        withLock {
            bitmap.eraseColor(Color.WHITE)
            canvas.drawColor(Color.WHITE)
        }
    }

    inline fun <T> withLock(block: () -> T): T = bitmapLock.withLock(block)

    fun resize(newWidth: Int, newHeight: Int) {
        withLock {
            if (_bitmap.width == newWidth && _bitmap.height == newHeight) return

            val oldbmp = bitmap
            val newbitmap = createBitmap(newWidth, newHeight).apply { eraseColor(Color.WHITE) }
            canvas = Canvas(newbitmap)
            canvas.drawBitmap(oldbmp,
                Rect(0, 0, oldbmp.width, oldbmp.height),
                Rect(0, 0, newWidth, newHeight),
                null)
            _bitmap = newbitmap
        }
    }
}