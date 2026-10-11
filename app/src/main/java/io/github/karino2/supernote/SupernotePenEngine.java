package io.github.karino2.supernote;


import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;

/*
    This file is fork from AnimInk.
    https://github.com/YoramDevGH/AnimInk
 */


/** Runtime bridge to the pen-write engine shipped in Supernote's boot class path. */
public final class SupernotePenEngine {
    private static final String TAG = "MagnaSketch";

    public static final int TOOL_PENCIL = 0;
    public static final int TOOL_INK = 1;
    public static final int TOOL_ERASER = 2;


    @FunctionalInterface
    public interface Listener {
        void onStrokeFinished(Bitmap bitmap, Rect dirtyRect);
    }

    private final View host;
    private final Listener listener;
    private Object controller;
    @SuppressWarnings("FieldCanBeLocal")
    private final Object drawEventProxy;
    private final Object pendingLock = new Object();
    private Rect pendingDirtyRect;
    private int pendingCallbacks;

    static SupernotePenEngine create(View host, Listener listener) {
        try {
            return new SupernotePenEngine(host, listener);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void listMethod() {
        // Method[] methods = controller.getClass().getDeclaredMethods();
        // Method[] methods = host.getClass().getDeclaredMethods();
        Method[] methods = View.class.getDeclaredMethods();
        for (Method method : methods) {
            StringBuilder params = new StringBuilder();
            for (Class<?> paramType : method.getParameterTypes()) {
                if (params.length() > 0) {
                    params.append(", ");
                }
                params.append(paramType.getSimpleName());
            }

            Log.d(TAG, "Method: " + method.getName()
                    + "(" + params + ")"
                    + " : " + method.getReturnType().getSimpleName());
        }
    }

    Object getController() throws ReflectiveOperationException {
        Method getter = View.class.getMethod("getPWInterFace");
        return getter.invoke(host);
    }

    public SupernotePenEngine(View host, Listener listener) throws ReflectiveOperationException {
        this.host = host;
        this.listener = listener;
        controller = getController();
        if (controller == null) throw new IllegalStateException("Moteur PW indisponible");

        // listMethod();
        /*
        Method mt = View.class.getMethod("setEinkUpdateMode", int.class, int.class);
        Log.d(TAG, "Method: " + mt.getName());

         */

        Class<?> callbackType = Class.forName("android.view.EinkPWInterface$PWDrawEventWithPoint");
        drawEventProxy = Proxy.newProxyInstance(host.getClass().getClassLoader(),
                new Class<?>[]{callbackType}, (proxy, method, args) -> {
                    if ("onTouchDrawEnd".equals(method.getName()) && args != null && args.length >= 2
                            && args[0] instanceof Bitmap && args[1] instanceof Rect) {
                        Bitmap bitmap = (Bitmap) args[0];
                        Rect dirty = new Rect((Rect) args[1]);
                        synchronized (pendingLock) {
                            if (pendingDirtyRect == null) pendingDirtyRect = new Rect(dirty);
                            else pendingDirtyRect.union(dirty);
                            pendingCallbacks++;
                        }
                        Log.d(TAG, "Native stroke finished: " + dirty);
                        host.post(() -> {
                            try {
                                listener.onStrokeFinished(bitmap, dirty);
                            } finally {
                                synchronized (pendingLock) {
                                    pendingCallbacks--;
                                    if (pendingCallbacks <= 0) {
                                        pendingCallbacks = 0;
                                        pendingDirtyRect = null;
                                    }
                                }
                            }
                        });
                    }
                    return null;
                });
        invoke("setDrawEventListener", new Class<?>[]{callbackType}, drawEventProxy);
        invoke("setDrawObjectPaintAntiAlias", new Class<?>[]{int.class}, 0);
        invoke("setOneWordDelayMs", new Class<?>[]{int.class}, 0);
        invoke("setPenColor", new Class<?>[]{int.class}, Color.BLACK);
        invoke("disablePenErase", new Class<?>[]{boolean.class}, false);
        // Keep stylus events available to the host View. Some Chauvet builds do
        // not activate the raw PW input route for sideloaded applications.
        invoke("enableTouchDispatch", new Class<?>[]{int.class}, 1);
        setEnabled(true);
    }

    public void setTool(int tool, int color) {
        try {
            if (tool == TOOL_ERASER) {
                invoke("setPenStdEraseWidth", new Class<?>[]{float.class}, 42f);
                invoke("setDrawObjectType", new Class<?>[]{int.class}, 5);
            } else if (tool == TOOL_INK) {
                invoke("setPenType", new Class<?>[]{int.class}, 2);
                invoke("setPenSettingWidth", new Class<?>[]{int.class}, 4);
            } else {
                invoke("setPenType", new Class<?>[]{int.class}, 1);
                invoke("setPenSettingWidth", new Class<?>[]{int.class}, 2);
            }
            invoke("setPenColor", new Class<?>[]{int.class}, color);
        } catch (ReflectiveOperationException ignored) { }
    }

    private boolean _enabled = false;

    public void setEnabled(boolean enabled) {
        _enabled = enabled;
        try { invoke("setPWEnabled", new Class<?>[]{boolean.class}, enabled); }
        catch (ReflectiveOperationException e) {
            Log.d(TAG, "setEnabled: Exp:" + e);

        }
    }

    public boolean isEnabled() {
        // I can't find how to get setPWEnable result. just store field and return it.
        // This might be different from device state.
        return _enabled;

        /*
                try {
            // always false
            Log.d(TAG, "isEnabled2: " + View.class.getMethod("isEinkModeSet").invoke(host));
        }catch(ReflectiveOperationException e) {
            Log.d(TAG, "isEnable: Exp: " + e);
        }

         */

        /*
        try {
            Log.d(TAG, "isEnabled2: " + invoke("isWriteNoneDraw", new Class<?>[0]));
            // Log.d(TAG, "isEnabled2-2: " + invoke("isCurrentWriting", new Class<?>[0]));
            return Boolean.TRUE.equals(invoke("getPWEnabled", new Class<?>[0])); }
        catch (ReflectiveOperationException e) {
            Log.d(TAG, "isEnable: Exp: " + e);
            return false;
        }
         */
    }

    public void setBitmapVisible(boolean visible) {
        try {
            invoke("setPWBitmapInVisible", new Class<?>[]{boolean.class}, !visible);
            invoke("invalidateHost", new Class<?>[]{Rect.class}, (Object) null);
        } catch (ReflectiveOperationException ignored) { }
    }

    /** Presents an already-rendered page with the firmware's non-flashing UI waveform. */
    void refreshPageWithoutFlash(Rect rect) {
        try {
            invoke("invalidateHost", new Class<?>[]{Rect.class},
                    rect == null ? null : new Rect(rect));
        } catch (ReflectiveOperationException ignored) {
            if (rect == null) host.invalidate();
            else host.invalidate(rect);
        }
    }

    public void setWritableRect(Rect rect) {
        try {
            invoke("rmAllWritableRects", new Class<?>[0]);
            invoke("addWritableRects", new Class<?>[]{java.util.List.class},
                    Collections.singletonList(new Rect(rect)));
        } catch (ReflectiveOperationException ignored) { }
    }

    public void loadBitmap(Bitmap bitmap) {
        try {
            // A silent clear only repaints the host View. On Chauvet 3.24 the
            // PW backing bitmap can then retain pixels from the previous frame;
            // its first dirty update exposes those pixels around the new stroke.
            // Clear and present the whole native surface before installing the
            // next frame so both the visible e-ink page and PW's cache agree.
            invoke("clearContent", new Class<?>[]{Rect.class, boolean.class, boolean.class},
                    null, true, true);
            invoke("setPWBitmap", new Class<?>[]{Bitmap.class, Rect.class, Rect.class, boolean.class},
                    bitmap, null, null, true);
            invoke("invalidateHost", new Class<?>[]{Rect.class}, (Object) null);
        } catch (ReflectiveOperationException ignored) {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    /** Clears only the transient PW pixels and asks the host to reveal its raster below. */
    public void clearTransient(Rect rect) {
        try {
            invoke("clearContent", new Class<?>[]{Rect.class, boolean.class, boolean.class},
                    rect == null ? null : new Rect(rect),
                    true, true);
                    // false, false);
                    // false, true);
                    // true, false);
        } catch (ReflectiveOperationException e) { Log.d(TAG, "clearTransient: Ex: " + e); }
    }

    public void clear() {
        try {
            invoke("clearContent", new Class<?>[]{Rect.class, boolean.class, boolean.class, boolean.class},
                    null, true, true, true);
        } catch (ReflectiveOperationException e) {
            Log.d(TAG, "fail to call: " + e.toString());
        }
    }

    public Bitmap getBitmap() {
        try { return (Bitmap) invoke("getPureWriteBitmap", new Class<?>[0]); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    public Bitmap getBitmapRef() {
        try { return (Bitmap) invoke("getPureWriteBitmapRef", new Class<?>[0]); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    // allocNewPWBitmap
    public Bitmap allocNewPWBitmap() {
        try { return (Bitmap) invoke("allocNewPWBitmap", new Class<?>[0]); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    public Canvas getPWCanvas() {
        try { return (Canvas) invoke("getPwCanvas", new Class<?>[0]); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    public boolean isReady() {
        try { return invoke("getPureWriteBitmap", new Class<?>[0]) != null; }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    public boolean isWriting() {
        try { return Boolean.TRUE.equals(invoke("isCurrentWriting", new Class<?>[0])); }
        catch (ReflectiveOperationException ignored) { return false; }
    }

    public Rect getPendingDirtyRect() {
        synchronized (pendingLock) {
            return pendingDirtyRect == null ? null : new Rect(pendingDirtyRect);
        }
    }

    public void forwardEvent(MotionEvent event) {
        try {
            invoke("sendBackEvent", new Class<?>[]{MotionEvent.class, boolean.class}, event, false);
        } catch (ReflectiveOperationException ignored) { }
    }

    public void reassignCallback() {
        try {
            Class<?> callbackType = Class.forName("android.view.EinkPWInterface$PWDrawEventWithPoint");
            invoke("setDrawEventListener", new Class<?>[]{callbackType}, (Object) drawEventProxy);
        } catch (ReflectiveOperationException e) { Log.d(TAG, "reassign Ex:" + e); }
    }

    public void close() {
        try {
            Class<?> callbackType = Class.forName("android.view.EinkPWInterface$PWDrawEventWithPoint");
            invoke("setDrawEventListener", new Class<?>[]{callbackType}, (Object) null);
        } catch (ReflectiveOperationException ignored) { }
    }

    private Object invoke(String name, Class<?>[] types, Object... args)
            throws ReflectiveOperationException {
        try {
            Method method = controller.getClass().getMethod(name, types);
            return method.invoke(controller, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof ReflectiveOperationException)
                throw (ReflectiveOperationException) cause;
            throw error;
        }
    }
}
