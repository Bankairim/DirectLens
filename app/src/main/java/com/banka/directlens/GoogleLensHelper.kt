/*
 * Copyright (C) 2026 Asanoha Labs
 * DirectLens is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */

package com.banka.directlens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

fun searchWithGoogleLens(uri: Uri, context: Context) {
    try {
        val lensIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(lensIntent)
    } catch (e: Exception) {
        Log.e("DirectLens", "Échec du lancement de Lens", e)
    }
}
