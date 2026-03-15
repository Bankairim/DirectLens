package com.banka.directlens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

class TrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si l'activité est lancée via l'action ASSIST (appui long Home)
        if (Intent.ACTION_ASSIST == intent.action) {
            DirectLensService.instance?.let { service ->
                service.executeActionSequence(skipVibration = true)
                finish()
                return
            }
        }

        // Logique standard (via l'overlay)
        val bitmap = BitmapCache.bitmap
        if (bitmap != null) {
            val path = ImageUtils.saveBitmap(this, bitmap)
            val file = File(path)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            searchWithGoogleLens(uri, this)
            BitmapCache.bitmap = null
        }

        finish()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
