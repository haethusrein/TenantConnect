package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.ActivityLandlordDetailsBinding

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

class LandlordDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLandlordDetailsBinding
    private var qrImageUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            qrImageUri = it
            binding.ivQrPreview.setImageURI(it)
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
            selectImageLauncher.launch("image/*")
        }

        binding.ivQrPreview.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        binding.btnSubmitDetails.setOnClickListener {
            val name = binding.etPropertyName.text.toString().trim()
            val address = binding.etPropertyAddress.text.toString().trim()
            val totalRoomsStr = binding.etTotalRooms.text.toString().trim()

            if (name.isEmpty() || address.isEmpty() || totalRoomsStr.isEmpty()) {
                if (name.isEmpty()) binding.etPropertyName.error = "Property name required"
                if (address.isEmpty()) binding.etPropertyAddress.error = "Address required"
                if (totalRoomsStr.isEmpty()) binding.etTotalRooms.error = "Units required"
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalRooms = totalRoomsStr.toIntOrNull() ?: 0
            if (totalRooms <= 0) {
                binding.etTotalRooms.error = "Invalid number of units"
                return@setOnClickListener
            }

            if (landlordId != null) {
                savePropertyDetails(landlordId, name, address, totalRooms)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        val loadingOverlay = findViewById<View>(R.id.loading_overlay)
        val tvMessage = findViewById<TextView>(R.id.tv_loading_message)
        
        loadingOverlay.isVisible = isLoading
        binding.btnSubmitDetails.isEnabled = !isLoading
        
        if (isLoading) {
            tvMessage.text = "Saving property info..."
        }
    }

    private fun savePropertyDetails(landlordId: String, name: String, address: String, totalRooms: Int) {
        showLoading(true)
        val propertyId = FirebaseManager.propertiesRef.push().key ?: return
        
        // Note: For now, we store the local URI string as a placeholder for the upload.
        // In a real app, you would upload to Firebase Storage and store the resulting URL.
        val property = Property(
            propertyId = propertyId,
            landlordId = landlordId,
            propertyName = name,
            address = address,
            totalRooms = totalRooms,
            coverPhotoUrl = qrImageUri?.toString() // Using this as the payment QR placeholder for now
        )

        FirebaseManager.propertiesRef.child(propertyId).setValue(property)
            .addOnSuccessListener {
                // Also update the landlord's user record with the propertyId
                FirebaseManager.usersRef.child(landlordId).child("propertyId").setValue(propertyId)
                    .addOnCompleteListener {
                        showLoading(false)
                        Toast.makeText(this, "Property Setup Complete!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, DashboardLandlordActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        intent.putExtra("SHOW_ADD_TENANT", true)
                        startActivity(intent)
                        finish()
                    }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Failed to save property: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
