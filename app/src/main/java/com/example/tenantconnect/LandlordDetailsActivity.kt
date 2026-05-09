package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivityLandlordDetailsBinding

class LandlordDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLandlordDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandlordDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val landlordId = intent.getStringExtra("LANDLORD_ID") ?: FirebaseManager.auth.currentUser?.uid

        binding.btnSubmitDetails.setOnClickListener {
            val name = binding.etPropertyName.text.toString().trim()
            val address = binding.etPropertyAddress.text.toString().trim()

            if (name.isEmpty() || address.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (landlordId != null) {
                savePropertyDetails(landlordId, name, address)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        val loadingOverlay = findViewById<View>(R.id.loading_overlay)
        val tvMessage = findViewById<TextView>(R.id.tv_loading_message)
        
        loadingOverlay?.visibility = if (isLoading) View.VISIBLE else View.GONE
        tvMessage?.text = "Saving property info..."
        binding.btnSubmitDetails.isEnabled = !isLoading

        if (isLoading) {
            loadingOverlay?.postDelayed({
                if (loadingOverlay.visibility == View.VISIBLE) {
                    showLoading(false)
                    Toast.makeText(this, "Save timed out. Please check your connection.", Toast.LENGTH_LONG).show()
                }
            }, 10000)
        }
    }

    private fun savePropertyDetails(landlordId: String, name: String, address: String) {
        showLoading(true)
        val propertyId = FirebaseManager.propertiesRef.push().key ?: return
        val property = Property(
            propertyId = propertyId,
            landlordId = landlordId,
            propertyName = name,
            address = address
        )

        FirebaseManager.propertiesRef.child(propertyId).setValue(property)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Property Setup Complete!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DashboardLandlordActivity::class.java)
                intent.putExtra("SHOW_ADD_TENANT", true)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Failed to save property: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
