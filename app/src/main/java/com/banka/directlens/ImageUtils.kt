package com.banka.directlens

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    fun saveBitmap(context: Context, bitmap: Bitmap): String {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "screenshot.png")
        val fileOutputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()
        return file.absolutePath
    }
}