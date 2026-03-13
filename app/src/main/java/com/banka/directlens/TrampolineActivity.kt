package com.banka.directlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.io.File

class TrampolineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bitmap = BitmapCache.bitmap
        if (bitmap != null) {
            // Sauvegarde de l'image
            val path = ImageUtils.saveBitmap(this, bitmap)
            val file = File(path)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            // Lancement de Lens avec ton Helper
            searchWithGoogleLens(uri, this)

            // Nettoyage de la RAM
            BitmapCache.bitmap = null
        }

        // Destruction immédiate
        finish()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0) // Supprime l'animation de fermeture
    }
}