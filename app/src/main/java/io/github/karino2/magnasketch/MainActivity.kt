package io.github.karino2.magnasketch

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.karino2.supernote.BitmapBackend
import io.github.karino2.supernote.SupernotePenCanvas
import java.io.File
import java.io.FileNotFoundException


class MainActivity : AppCompatActivity() {

    val penCanvas by lazy { findViewById<SupernotePenCanvas>(R.id.canvas)}

    val backend :BitmapBackend
        get() = penCanvas.backend

    var isDirty: Boolean
        get() = penCanvas.isBackendDirty
        set(value) {
            penCanvas.isBackendDirty = value
        }

    val handler = Handler(Looper.myLooper()!!)


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

        handler.post {
            // サイズが決まった後に呼びたいのだが、面倒なのでhandler.postで。
            try
            {
                openFileInput(fileName).use {
                    BitmapFactory.decodeStream(it)
                }?.let {
                    penCanvas.backend.drawBitmap(it)
                    penCanvas.invalidate()
                }
            }catch(ignore: FileNotFoundException) {
            }

            penCanvas.applyRawDrawingSettings()
        }

    }

    override fun onResume() {
        super.onResume()
        penCanvas.applyRawDrawingSettings()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) penCanvas.applyRawDrawingSettings()

    }

    override fun onPause() {
        penCanvas.ensureCloseRawRendering()
        ensureSave()
        super.onPause()
    }

    override fun onDestroy() {
        penCanvas.ensureCloseRawRendering()
        penCanvas.einkPW.close()
        super.onDestroy()
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
            backend.withLock { backend.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            it.flush()
        }
    }

    fun withTempNoRawRendering(f: ()->Unit) {
        val eink = penCanvas.einkPW
        val isRawRendering = eink.isEnabled
        if (isRawRendering)
        {
            eink.close()
        }
        f()
        if (isRawRendering)
        {
            eink.open()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.action_clear -> {
                withTempNoRawRendering {
                    backend.clear()
                    penCanvas.invalidate()
                    penCanvas.einkPW.clear()
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
