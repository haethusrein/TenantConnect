package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivityProfileTenantBinding

import java.util.Locale

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
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                binding.tvProfileName.text = "Name: ${user.firstName} ${user.middleName ?: ""} ${user.lastName}"
                binding.tvProfileRole.text = "Role: ${user.role}"
                binding.tvProfileSex.text = "Sex: ${user.gender}"
                binding.tvBirthDate.text = "Birth Date: ${user.birthDate ?: "N/A"}"
                binding.tvOriginalAddress.text = "Original Address: ${user.originalAddress ?: "N/A"}"
                binding.tvCivilStatus.text = "Civil Status: ${user.civilStatus ?: "N/A"}"
                binding.tvOccupation.text = "Occupation: ${user.occupation ?: "N/A"}"
                
                if (user.role == "Landlord") {
                    setupLandlordProfile(user)
                } else {
                    setupTenantProfile(userId)
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLandlordProfile(user: User) {
        binding.tvPropertyTitle.text = "Property Information"
        binding.tvContractType.visibility = View.GONE
        binding.tvBaseRate.visibility = View.GONE
        binding.tvExtraInfo.visibility = View.VISIBLE

        user.propertyId?.let { propertyId ->
            FirebaseManager.propertiesRef.child(propertyId).get().addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val property = snapshot.getValue(Property::class.java)
                if (property != null) {
                    binding.tvResidence.text = "Property: ${property.propertyName}"
                    binding.tvLeaseAddress.text = "Location: ${property.address}"
                    binding.tvExtraInfo.text = "Total Rooms: ${property.totalRooms}"
                }
            }
        } ?: run {
            binding.tvResidence.text = "Property: Not set up"
            binding.tvLeaseAddress.text = "Location: N/A"
            binding.tvExtraInfo.text = "Click 'Edit' to set up property"
        }
    }

    private fun setupTenantProfile(userId: String) {
        binding.tvPropertyTitle.text = "Lease Information"
        binding.tvContractType.visibility = View.VISIBLE
        binding.tvBaseRate.visibility = View.VISIBLE
        binding.tvExtraInfo.visibility = View.GONE
        fetchContractAndProperty(userId)
    }

    private fun fetchContractAndProperty(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                
                val contract = snapshot.children.firstOrNull { 
                    it.child("status").getValue(String::class.java) == "Active" 
                }?.getValue(Contract::class.java)

                if (contract != null) {
                    binding.tvContractType.text = "Contract: ${contract.renewalTerm ?: "N/A"}"
                    binding.tvBaseRate.text = "Base rate: ₱${String.format(Locale.US, "%.2f", contract.baseRentAmount)}"
                    
                    contract.propertyId?.let { fetchPropertyDetails(it) }
                } else {
                    binding.tvResidence.text = "Residence: None"
                    binding.tvLeaseAddress.text = "Address: None"
                    binding.tvContractType.text = "Contract: None"
                    binding.tvBaseRate.text = "Base rate: None"
                }
            }
    }

    private fun fetchPropertyDetails(propertyId: String) {
        FirebaseManager.propertiesRef.child(propertyId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            val property = snapshot.getValue(Property::class.java)
            if (property != null) {
                binding.tvResidence.text = "Residence: ${property.propertyName}"
                binding.tvLeaseAddress.text = "Address: ${property.address}"
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
