package com.example.tenantconnect

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import coil.load
import com.example.tenantconnect.databinding.ActivityLandlordDetailsBinding
import java.io.ByteArrayOutputStream

class LandlordDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLandlordDetailsBinding
    private var qrImageUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            qrImageUri = it
            binding.ivQrPreview.load(it)
            binding.ivQrPreview.isVisible = true
            binding.btnUploadQr.isVisible = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandlordDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val landlordId = intent.getStringExtra("LANDLORD_ID") ?: FirebaseManager.auth.currentUser?.uid

        binding.btnUploadQr.setOnClickListener {
            selectImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.ivQrPreview.setOnClickListener {
            selectImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnSubmitDetails.setOnClickListener {
            val name = binding.etPropertyName.text.toString().trim()
            val address = binding.etPropertyAddress.text.toString().trim()
            val totalRoomsStr = binding.etTotalRooms.text.toString().trim()

            if (name.isEmpty() || address.isEmpty() || totalRoomsStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalRooms = totalRoomsStr.toIntOrNull() ?: 0
            if (totalRooms <= 0) {
                binding.etTotalRooms.error = "Invalid number of units"
                return@setOnClickListener
            }

            if (landlordId != null) {
                processAndSave(landlordId, name, address, totalRooms)
            }
        }
    }

    private fun processAndSave(landlordId: String, name: String, address: String, totalRooms: Int) {
        showLoading(true)
        if (qrImageUri != null) {
            processImageToBase64(qrImageUri!!, 500) { base64 ->
                if (base64 != null) {
                    savePropertyDetails(landlordId, name, address, totalRooms, base64)
                } else {
                    showLoading(false)
                    Toast.makeText(this, "Failed to process QR code", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            savePropertyDetails(landlordId, name, address, totalRooms, null)
        }
    }

    private fun processImageToBase64(uri: Uri, maxSide: Int, callback: (String?) -> Unit) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (original == null) return callback(null)

            val width = original.width
            val height = original.height
            val ratio = width.toFloat() / height.toFloat()
            
            var newWidth = maxSide
            var newHeight = maxSide
            
            if (width > height) {
                newHeight = (maxSide / ratio).toInt()
            } else {
                newWidth = (maxSide * ratio).toInt()
            }

            val scaled = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
            val out = ByteArrayOutputStream()
            // Increased quality to 80
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val base64 = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            callback(base64)
        } catch (e: Exception) {
            callback(null)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        val loadingOverlay = findViewById<View>(R.id.loading_overlay)
        val tvMessage = findViewById<TextView>(R.id.tv_loading_message)
        
        loadingOverlay?.isVisible = isLoading
        binding.btnSubmitDetails.isEnabled = !isLoading
        
        if (isLoading) {
            tvMessage?.text = "Saving property info..."
        }
    }

    private fun savePropertyDetails(landlordId: String, name: String, address: String, totalRooms: Int, qrBase64: String?) {
        val propertyId = FirebaseManager.propertiesRef.push().key ?: return
        
        val property = Property(
            propertyId = propertyId,
            landlordId = landlordId,
            propertyName = name,
            address = address,
            totalRooms = totalRooms,
            coverPhotoUrl = qrBase64
        )

        FirebaseManager.propertiesRef.child(propertyId).setValue(property)
            .addOnSuccessListener {
                FirebaseManager.usersRef.child(landlordId).child("propertyId").setValue(propertyId)
                    .addOnCompleteListener {
                        showLoading(false)
                        val intent = Intent(this, DashboardLandlordActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
            }
            .addOnFailureListener {
                showLoading(false)
            }
    }
}
