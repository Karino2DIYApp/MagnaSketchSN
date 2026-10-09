package io.github.karino2.magnasketch

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.karino2.supernote.BitmapBackend
import io.github.karino2.supernote.SupernotePenEngine
import java.io.File


class MainActivity : AppCompatActivity() {

    val surface: SurfaceView by lazy { findViewById(R.id.canvas)}

    val bmpPaint = Paint(Paint.DITHER_FLAG)

    val backend by lazy {
        BitmapBackend(surface.width, surface.height).apply {
            try {
                val sbmp = openFileInput(fileName).use {
                    BitmapFactory.decodeStream(it)
                }
                sbmp?.let {
                    this.canvas.drawBitmap(it, 0f, 0f, bmpPaint)
                }
            }catch(_: java.io.FileNotFoundException)
            {}
        }
    }

    val penEngine by lazy {
        val pe = SupernotePenEngine(surface, SupernotePenEngine.Listener { b, r-> onHardwareStrokeFinished(b, r) })
        surface.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyRawDrawingSettings() }
        surface.holder.addCallback(surfaceCallback)
        pe
    }

    var isDirty = false

    fun onHardwareStrokeFinished(bitmap: Bitmap, dirty: Rect) {
        // val logical = RectF(dirty)

        Log.d("MagnaSketch", "dirty: ${dirty}")

        backend.canvas.drawBitmap(bitmap, dirty, dirty, null)
        /*
        surface.holder?.let { holder->
            holder.lockCanvas()?.let { scanv ->
                scanv.drawBitmap(bitmap, dirty, dirty, null)
                holder.unlockCanvasAndPost(scanv)
            }
        }
         */
        penEngine.clearTransient(dirty)
    }


    private var lastOpenedLimit: Rect? = null

    private fun applyRawDrawingSettings() {
        val limit = surfaceVisibleRect()
        if (!surface.holder.surface.isValid || limit.width() <= 0 || limit.height() <= 0) return

        // サイズが変わっていたら、バッファ不整合を防ぐため開き直す（黒画面対策の核心）
        if (isRawRenderingBecomesStale(limit)) {
            ensureCloseRawRendering()
        }

        if (!penEngine.isEnabled) {
            clearSurfaceView(backend.bitmap)
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

    private fun isRawRenderingBecomesStale(limit: Rect): Boolean =
        penEngine.isEnabled && (lastOpenedLimit == null || limit.width() != lastOpenedLimit?.width() || limit.height() != lastOpenedLimit?.height())

    private fun surfaceVisibleRect(): Rect {
        val limit = Rect()
        surface.getLocalVisibleRect(limit)

        return limit
    }

    private val surfaceCallback : SurfaceHolder.Callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            applyRawDrawingSettings()
        }
        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
        override fun surfaceDestroyed(holder: SurfaceHolder) {}
    }

    fun clearSurfaceView(bmp: Bitmap? = null): Boolean {
        val holder = surface.holder ?: return false
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        bmp?.let {
            canvas.drawBitmap(it, 0f, 0f, bmpPaint)
        }
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.let {
            it.setDisplayShowHomeEnabled(true)
            it.setIcon(R.mipmap.ic_launcher)
            it.title = ""
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
        surface.setOnTouchListener { view, event ->
            penEngine.forwardEvent(event)
            true
        }
        penEngine
    }

    override fun onResume() {
        super.onResume()
        applyRawDrawingSettings()
    }

    private fun updateBmpToSurface() {
        surface.holder?.let { holder->
            holder.lockCanvas()?.let { lockCanvas ->
                lockCanvas.drawColor(Color.WHITE)
                lockCanvas.drawBitmap(backend.bitmap, 0f, 0f, bmpPaint)
                holder.unlockCanvasAndPost(lockCanvas)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyRawDrawingSettings()
    }

    override fun onPause() {
        ensureCloseRawRendering()
        ensureSave()
        updateBmpToSurface()
        super.onPause()
    }

    override fun onDestroy() {
        ensureCloseRawRendering()
        penEngine.close()
        super.onDestroy()
    }

    private fun ensureCloseRawRendering() {
        if (penEngine.isEnabled) {
            penEngine.isEnabled = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    val fileName = "canvas.png"

    fun ensureSave() {
        if(!isDirty) return

        doSave()
        isDirty = false
    }

    private fun doSave() {
        openFileOutput(fileName, MODE_PRIVATE).use {
            backend.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.flush()
        }
    }

    fun withTempNoRawRendering(f: ()->Unit) {
        val isRawRendering = penEngine.isEnabled
        if (isRawRendering)
        {
            penEngine.isEnabled = false
        }
        f()
        if (isRawRendering)
        {
            penEngine.isEnabled = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_clear -> {
                withTempNoRawRendering {
                    clearSurfaceView()
                    backend.clear()
                    penEngine.clear()
                    doSave()
                }
                isDirty = false
                return true
            }
            R.id.action_share-> {
                withTempNoRawRendering {
                    ensureSave()

                    val path = File.createTempFile("magna_sketch_share_", ".png", cacheDir)
                    openFileInput(fileName).use { src->
                        path.outputStream().use { dest ->
                            src.copyTo(dest)
                            dest.flush()
                        }
                    }
                    val uri = FileProvider.getUriForFile(this, applicationContext.packageName+".provider", path)
                    ShareCompat.IntentBuilder(this).apply {
                        setChooserTitle("Sharing MagnaSketch")
                        setStream(uri)
                        setType("image/png")
                    }.startChooser()
                }
            }

        }
        return super.onOptionsItemSelected(item)
    }

}
