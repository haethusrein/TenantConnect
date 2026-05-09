package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivityProfileTenantBinding

class ProfileTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileTenantBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            loadUserProfile(userId)
        }

        setupBottomNavigation()
    }

    private fun loadUserProfile(userId: String) {
        FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                binding.tvProfileName.text = "Name: ${user.firstName} ${user.middleName ?: ""} ${user.lastName}"
                binding.tvProfileRole.text = "Role: ${user.role}"
                binding.tvProfileSex.text = "Sex: ${user.gender}"
                binding.tvBirthDate.text = "Birth Date: ${user.birthDate ?: "N/A"}"
                binding.tvOriginalAddress.text = "Original Address: ${user.originalAddress ?: "N/A"}"
                binding.tvCivilStatus.text = "Civil Status: ${user.civilStatus ?: "N/A"}"
                binding.tvOccupation.text = "Occupation: ${user.occupation ?: "N/A"}"
                
                checkLeaseStatus(userId)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkLeaseStatus(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                binding.tvResidence.text = "Residence: None"
                binding.tvLeaseAddress.text = "Address: None"
                binding.tvContractType.text = "Contract: None"
                binding.tvBaseRate.text = "Base rate: None"
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener {
            val intent = Intent(this, DashboardTenantActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        binding.bottomNav.navNotifications.setOnClickListener {
            val intent = Intent(this, InboxTenantActivity::class.java)
            startActivity(intent)
        }

        binding.bottomNav.navPayments.setOnClickListener {
            val intent = Intent(this, PaymentHistoryTenantActivity::class.java)
            startActivity(intent)
        }

        binding.bottomNav.navProfile.setOnClickListener {
            // Already here
        }
    }
}
