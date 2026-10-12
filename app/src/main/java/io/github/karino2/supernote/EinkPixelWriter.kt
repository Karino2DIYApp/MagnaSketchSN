package io.github.karino2.supernote

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import java.lang.reflect.Method
import java.lang.reflect.Proxy

enum class PenType(val value: Int) {
    PENCIL(1),
    INK(2),
}

/*
    SupernoteのE-inkの低レベルレンダリングを処理するクラス。
    基本的にはViewの隠しメソッドであるgetPWInterFaceの返すEinkPWInterfaceのラッパ。

    https://github.com/YoramDevGH/AnimInk のSupernotePenEngineと同じような役割だがUIスレッド以外から
    コールバックを呼ぶのを許してより簡素化している。
 */
class EinkPixelWriter(val host: View, val onStrokeFinished: (Bitmap, Rect) -> Unit) {
    val pwObj = View::class.java.getMethod("getPWInterFace").invoke(host)

    fun methodT(name: String, vararg args: Class<*>) : Method {
        return pwObj.javaClass.getMethod(name, *args)
    }

    fun toJavaType(jclass: Class<*>) : Class<*> {
        return when(jclass.name) {
            "java.lang.Integer" -> Integer.TYPE
            "java.lang.Boolean" -> Boolean::class.java
            "java.lang.String" -> String::class.java
            else -> jclass
        }
    }


    fun invoke(name: String, vararg args: Any?): Any? {
        val atype = args.map { toJavaType(it!!.javaClass) }.toTypedArray<Class<*>>()
        val m =  methodT(name, *atype)
        return m.invoke(pwObj, *args)
        // return pwObj.javaClass.getMethod(name, *args.map { it!!.javaClass }.toTypedArray()).invoke(pwObj, *args)
    }


    @SuppressLint("PrivateApi")
    val drawCallbackType = Class.forName("android.view.EinkPWInterface\$PWDrawEventWithPoint")

    val drawCallbackProxy = Proxy.newProxyInstance(
            host.javaClass.getClassLoader(),
            arrayOf<Class<*>?>(drawCallbackType), { proxy, method, args ->
                if ("onTouchDrawEnd" == method.getName() && args != null && args.size >= 2 && args[0] is Bitmap && args[1] is Rect
                ) {
                    val bitmap = args[0] as Bitmap
                    val dirty = Rect(args[1] as Rect)
                    onStrokeFinished(bitmap, dirty)
                }
                null
            })

    fun setPenType(ptype: PenType) {
        invoke("setPenType", ptype.value)
    }

    fun setPenWidth(width: Int) {
        invoke("setPenSettingWidth", width)
    }

    fun setPenColor(color: Int) {
        invoke("setPenColor", color)
    }

    fun setPen(penType: PenType, penWidth: Int, penColor: Int) {
        setPenType(penType)
        setPenWidth(penWidth)
        setPenColor(penColor)
    }

    fun _setEnabled(enabled: Boolean) {
        _isEnabled = enabled
        invoke("setPWEnabled", enabled)
    }

    fun setWritableRect(rect: Rect) {
        invoke("rmAllWritableRects")
        methodT("addWritableRects", MutableList::class.java).invoke(pwObj, mutableListOf(rect))
    }

    private var _isEnabled  = false
    var isEnabled: Boolean
        get() = _isEnabled
        set(value) = _setEnabled(value)



    /*
    PWInterfaceに関わる細々としたセットアップ全般。
    closeしてまたopenしたり出来るようにしておく。
     */
    fun open() {
        methodT("setDrawEventListener", drawCallbackType).invoke(pwObj, drawCallbackProxy)
        invoke("setDrawObjectPaintAntiAlias", 0)
        invoke("setOneWordDelayMs", 0)
        /*
        invoke("setPenColor", new Class<?>[]{int.class}, Color.BLACK);
        invoke("disablePenErase", new Class<?>[]{boolean.class}, false);

         */

        // AniminkのSupernotePenEngineのコメントより
        // Keep stylus events available to the host View. Some Chauvet builds do
        // not activate the raw PW input route for sideloaded applications.
        invoke("enableTouchDispatch", 1)
        _setEnabled(true)
    }

    fun close() {
        methodT("setDrawEventListener", drawCallbackType).invoke(pwObj, null)
        _setEnabled(false)
    }

    fun clearRect(rect: Rect?) {
        methodT("clearContent", Rect::class.java, Boolean::class.java, Boolean::class.java).invoke(pwObj, rect, true, true)
    }

    private fun setPWBitmap(bitmap: Bitmap) {
        methodT("setPWBitmap", Bitmap::class.java, Rect::class.java, Rect::class.java, Boolean::class.java).invoke( pwObj,bitmap, null, null, true)
    }

    fun invalidateHost(rect: Rect?) {
        invoke("invalidateHost", rect)
    }

    fun loadBitmap(src: Bitmap) {
        val bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
        try {
            // AniminkのSupernotePenEngineのコメントより
            //
            // A silent clear only repaints the host View. On Chauvet 3.24 the
            // PW backing bitmap can then retain pixels from the previous frame;
            // its first dirty update exposes those pixels around the new stroke.
            // Clear and present the whole native surface before installing the
            // next frame so both the visible e-ink page and PW's cache agree.
            clearRect(null)
            setPWBitmap(bitmap)
            invalidateHost(null)
        }catch (ignored: Exception) {
            if(!bitmap.isRecycled)
                bitmap.recycle()
        }
    }

    // AniminkのSupernotePenEngineのコメントより
    /** Clears only the transient PW pixels and asks the host to reveal its raster below. */
    fun clearTransient(rect: Rect?) = clearRect(rect)

    fun clear() {
        methodT("clearContent", Rect::class.java, Boolean::class.java, Boolean::class.java, Boolean::class.java ).invoke(pwObj, null, true, true, true)
    }

    fun forwardEvent(motionEvent: MotionEvent)  {
        invoke("sendBackEvent", motionEvent, false)
    }


}