package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.tenantconnect.databinding.ActivityViewContractBinding

class ViewContractActivity : AppCompatActivity() {
    private lateinit var binding: ActivityViewContractBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewContractBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val contractId = intent.getStringExtra("CONTRACT_ID")
        val userId = FirebaseManager.auth.currentUser?.uid
        
        if (userId != null) {
            if (contractId != null) {
                loadSpecificContract(contractId)
            } else {
                checkContractStatus(userId)
            }
        }

        setupMenu()
        setupBottomNavigation()
    }

    private fun loadSpecificContract(contractId: String) {
        FirebaseManager.contractsRef.child(contractId).get().addOnSuccessListener { snapshot ->
            val contract = snapshot.getValue(Contract::class.java)
            if (contract != null) {
                displayContractDetails(contract)
            } else {
                showEmptyState()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading contract", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkContractStatus(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            val activeContract = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                .firstOrNull { it.status == "Active" }

            if (activeContract == null) {
                showEmptyState()
            } else {
                displayContractDetails(activeContract)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error checking contract", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyState() {
        binding.tvContractStatus.text = "None"
        binding.tvContractStatus.setBackgroundResource(R.drawable.bg_status_badge)
        binding.tvLandlordName.text = "None"
        binding.tvTenantName.text = "None"
        binding.tvPropertyName.text = "None"
        binding.tvPropertyAddress.text = "None"
        binding.tvSignedDate.text = "No active contract found."
    }

    private fun displayContractDetails(contract: Contract) {
        binding.tvContractStatus.text = contract.status
        binding.tvSignedDate.text = "Started: ${contract.startDate ?: "N/A"}"
        
        // Fetch Landlord Name
        FirebaseManager.usersRef.child(contract.landlordId ?: "").get().addOnSuccessListener { userSnap ->
            val landlord = userSnap.getValue(User::class.java)
            binding.tvLandlordName.text = "${landlord?.firstName} ${landlord?.lastName}"
        }

        // Fetch Tenant Name
        FirebaseManager.usersRef.child(contract.tenantId ?: "").get().addOnSuccessListener { userSnap ->
            val tenant = userSnap.getValue(User::class.java)
            binding.tvTenantName.text = "${tenant?.firstName} ${tenant?.lastName}"
        }

        // Fetch Property Info with Fallbacks
        if (contract.propertyId != null) {
            fetchAndShowProperty(contract.propertyId, contract.roomId)
        } else if (contract.landlordId != null) {
            FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(contract.landlordId).get()
                .addOnSuccessListener { snapshot ->
                    val property = snapshot.children.firstOrNull()?.getValue(Property::class.java)
                    if (property != null) {
                        fetchAndShowProperty(property.propertyId, contract.roomId)
                    } else {
                        fetchPropertyIdFromUser(contract)
                    }
                }
        } else {
            fetchPropertyIdFromUser(contract)
        }
    }

    private fun fetchPropertyIdFromUser(contract: Contract) {
        FirebaseManager.usersRef.child(contract.tenantId ?: "").child("propertyId").get().addOnSuccessListener { snapshot ->
            val fallbackPropId = snapshot.getValue(String::class.java)
            fetchAndShowProperty(fallbackPropId, contract.roomId)
        }
    }

    private fun fetchAndShowProperty(propertyId: String?, roomId: String?) {
        if (propertyId == null) {
            binding.tvPropertyName.text = "Unknown Property"
            binding.tvPropertyAddress.text = "N/A"
            return
        }
        
        FirebaseManager.propertiesRef.child(propertyId).get().addOnSuccessListener { propSnap ->
            val property = propSnap.getValue(Property::class.java)
            if (property != null) {
                val bldg = property.propertyName ?: "Apartment"
                val unit = if (!roomId.isNullOrEmpty()) "Unit $roomId" else "Room N/A"
                binding.tvPropertyName.text = "$bldg, $unit"
                binding.tvPropertyAddress.text = property.address ?: "Address N/A"
            } else {
                binding.tvPropertyName.text = "Unknown Property"
                binding.tvPropertyAddress.text = "N/A"
            }
        }
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
            val menuItems = arrayOf("Announcements", "Settings", "Log out")
            val popup = ListPopupWindow(this)
            popup.anchorView = view
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuItems) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(Color.WHITE)
                    v.setPadding(40, 30, 40, 30)
                    v.textSize = 14f
                    return v
                }
            }
            popup.setAdapter(adapter)
            popup.width = 600
            popup.setBackgroundDrawable(ColorDrawable("#22223B".toColorInt()))
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> {
                        FirebaseManager.auth.signOut()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finishAffinity()
                    }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener {
            val intent = Intent(this, DashboardTenantActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, InboxTenantActivity::class.java))
        }
        binding.bottomNav.navPayments.setOnClickListener {
            startActivity(Intent(this, PaymentTenantActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
