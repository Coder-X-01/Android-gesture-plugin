package com.trae.gestureplugin

import android.app.Activity
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureActivity : Activity() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), 1000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == RESULT_OK && data != null) {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            captureOnce()
        }
        finish()
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

        val image = imageReader?.acquireLatestImage() ?: return
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.RGB_565)
        bitmap.copyPixelsFromBuffer(buffer)
        image.close()
        saveBitmap(bitmap)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val name = "Screenshot_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } else {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
}
