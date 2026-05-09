package com.example.tenantconnect

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivityViewContractBinding

class ViewContractActivity : AppCompatActivity() {
    private lateinit var binding: ActivityViewContractBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewContractBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            checkContractStatus(userId)
        }
    }

    private fun checkContractStatus(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                binding.tvContractStatus.text = "None"
                binding.tvContractStatus.setBackgroundResource(R.drawable.bg_status_badge)
                
                binding.tvLandlordName.text = "None"
                binding.tvTenantName.text = "None"
                binding.tvPropertyName.text = "None"
                binding.tvSignedDate.text = "No active contract found."
            } else {
                // Load real data if available
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error checking contract", Toast.LENGTH_SHORT).show()
        }
    }
}
