package com.example.tenantconnect

import android.util.Base64
import android.widget.ImageView
import coil.load

object ImageUtils {
    /**
     * Loads an image into an ImageView using Coil.
     * Automatically handles standard URLs and Base64 encoded strings.
     */
    fun loadImage(view: ImageView, source: String?, placeholder: Int = R.drawable.ic_person) {
        if (source.isNullOrEmpty()) {
            view.setImageResource(placeholder)
            return
        }

        if (source.startsWith("data:image") || source.length > 50) {
            try {
                // If it contains a comma, it's a data URI scheme
                val base64Data = if (source.contains(",")) {
                    source.substringAfter(",")
                } else {
                    source
                }
                
                // Clean up string: remove whitespace, newlines, or invisible chars that break decoding
                val cleanBase64 = base64Data.replace("\\s".toRegex(), "")
                
                // Final validation: check if it's a likely base64 string
                if (cleanBase64.length < 50 && !cleanBase64.contains("/")) {
                    // Too short or doesn't look like base64, try loading as-is
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
                e.printStackTrace()
                // If decoding fails, fall back to trying to load it as a regular source 
                // in case it's a local URI that hasn't expired yet
                view.load(source) {
                    crossfade(true)
                    placeholder(placeholder)
                    error(placeholder)
                }
            }
        } else {
            // It's a regular URL or local path
            view.load(source) {
                crossfade(true)
                placeholder(placeholder)
                error(placeholder)
            }
        }
    }
}
