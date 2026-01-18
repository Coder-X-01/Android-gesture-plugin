package com.trae.gestureplugin

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment
import android.widget.Toast

class CaptureActivity : Activity() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), 1000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == RESULT_OK && data != null) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            captureOnce()
        } else {
            finish()
        }
    }

    private fun captureOnce() {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WindowManager::class.java)
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, ImageFormat.RGB_565, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            imageReader?.surface,
            null,
            null
        )

        // Delay slightly to ensure frame is available
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                val plane = image.planes[0]
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.RGB_565)
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()
                
                // Crop out the padding
                val cleanBitmap = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)
                
                saveBitmap(cleanBitmap)
                if (cleanBitmap != bitmap) bitmap.recycle()
            }
            finishAndClean()
        }, 100)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        try {
            val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(path, "Screenshots")
            if (!dir.exists()) dir.mkdirs()
            
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "Screenshot_$timeStamp.png")
            
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            
            Toast.makeText(this, "截图已保存: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            
            // Notify gallery
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            mediaScanIntent.data = android.net.Uri.fromFile(file)
            sendBroadcast(mediaScanIntent)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "保存截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            bitmap.recycle()
        }
    }

    private fun finishAndClean() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        finish()
    }
}