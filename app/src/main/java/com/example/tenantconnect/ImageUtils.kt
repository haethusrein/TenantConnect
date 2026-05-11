package com.example.tenantconnect

import android.content.res.ColorStateList
import android.util.Base64
import android.widget.ImageView
import coil.load

object ImageUtils {
    /**
     * Loads an image into an ImageView using Coil.
     * Automatically handles standard URLs and Base64 encoded strings.
     * Also manages tints to ensure photos aren't masked by placeholder colors.
     */
    fun loadImage(view: ImageView, source: String?, placeholder: Int = R.drawable.ic_person) {
        if (source.isNullOrEmpty()) {
            view.setImageResource(placeholder)
            return
        }

        // Clear any XML-defined tint when loading a real photo
        view.imageTintList = null

        if (source.startsWith("data:image") || source.length > 100) {
            try {
                val base64Data = if (source.contains(",")) source.substringAfter(",") else source
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                
                // Final validation: check if it's a likely base64 string
                if (cleanBase64.length < 50 && !cleanBase64.contains("/")) {
                    view.load(source) {
                        crossfade(true)
                        placeholder(placeholder)
                        error(placeholder)
                    }
                    return
                }

                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                view.load(bytes) {
                    crossfade(true)
                    placeholder(placeholder)
                    error(placeholder)
                }
            } catch (e: Exception) {
                // If decoding fails, try as-is
                view.load(source) {
                    crossfade(true)
                    placeholder(placeholder)
                    error(placeholder)
                }
            }
        } else {
            view.load(source) {
                crossfade(true)
                placeholder(placeholder)
                error(placeholder)
            }
        }
    }
}
