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
            val activeContract = snapshot.children.firstOrNull { 
                it.child("status").getValue(String::class.java) == "Active" 
            }?.getValue(Contract::class.java)

            if (activeContract == null) {
                binding.tvContractStatus.text = "None"
                binding.tvContractStatus.setBackgroundResource(R.drawable.bg_status_badge)
                
                binding.tvLandlordName.text = "None"
                binding.tvTenantName.text = "None"
                binding.tvPropertyName.text = "None"
                binding.tvSignedDate.text = "No active contract found."
            } else {
                binding.tvContractStatus.text = activeContract.status
                binding.tvSignedDate.text = "Started: ${activeContract.startDate}"
                
                // Fetch Landlord Name
                FirebaseManager.usersRef.child(activeContract.landlordId ?: "").get().addOnSuccessListener { userSnap ->
                    val landlord = userSnap.getValue(User::class.java)
                    binding.tvLandlordName.text = "${landlord?.firstName} ${landlord?.lastName}"
                }

                // Fetch Tenant Name
                FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { userSnap ->
                    val tenant = userSnap.getValue(User::class.java)
                    binding.tvTenantName.text = "${tenant?.firstName} ${tenant?.lastName}"
                }

                // Fetch Property Name
                FirebaseManager.propertiesRef.child(activeContract.propertyId ?: "").get().addOnSuccessListener { propSnap ->
                    val property = propSnap.getValue(Property::class.java)
                    binding.tvPropertyName.text = property?.propertyName ?: "Unknown Property"
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error checking contract", Toast.LENGTH_SHORT).show()
        }
    }
}
