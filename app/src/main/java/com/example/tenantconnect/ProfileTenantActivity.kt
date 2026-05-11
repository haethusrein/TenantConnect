package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import coil.load
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.ActivityProfileTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class ProfileTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileTenantBinding
    private var currentUser: User? = null
    private var currentProperty: Property? = null
    private var activeContracts = mutableListOf<Contract>()
    private var contractsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            loadUserProfile(userId)
        }

        binding.btnEditProfile.setOnClickListener {
            currentUser?.let { user ->
                val dialog = EditProfileDialog(user, currentProperty) {
                    loadUserProfile(userId!!)
                }
                dialog.show(supportFragmentManager, "EditProfileDialog")
            }
        }

        setupBottomNavigation()
    }

    private fun loadUserProfile(userId: String) {
        FirebaseManager.usersRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return
                
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    currentUser = user
                    binding.tvProfileName.text = "Name: ${user.firstName} ${user.middleName ?: ""} ${user.lastName}"
                    binding.tvProfileRole.text = "Role: ${user.role}"
                    binding.tvProfileSex.text = "Sex: ${user.gender}"
                    binding.tvBirthDate.text = "Birth Date: ${user.birthDate ?: "N/A"}"
                    binding.tvOriginalAddress.text = "Original Address: ${user.originalAddress ?: "N/A"}"
                    binding.tvCivilStatus.text = "Civil Status: ${user.civilStatus ?: "N/A"}"
                    binding.tvOccupation.text = "Occupation: ${user.occupation ?: "N/A"}"
                    
                    ImageUtils.loadImage(binding.ivPhoto, user.profilePhotoUrl)
                    
                    if (user.role == "Landlord") {
                        setupLandlordProfile(user)
                    } else {
                        setupTenantProfile(userId)
                    }
                    setupMenu() // Refresh menu role logic
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
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
                    currentProperty = property
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
        listenForContracts(userId)
    }

    private fun listenForContracts(userId: String) {
        contractsListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
        
        contractsListener = FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    activeContracts = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                        .filter { it.status == "Active" }.toMutableList()

                    if (activeContracts.isNotEmpty()) {
                        setupContractSpinner()
                        // Initial update with the first contract or previously selected one
                        val selectedPos = if (binding.spinnerContracts.isVisible) {
                            binding.spinnerContracts.selectedItemPosition.coerceIn(0, activeContracts.size - 1)
                        } else 0
                        updateLeaseUI(activeContracts[selectedPos])
                    } else {
                        binding.spinnerContracts.isVisible = false
                        showNoLeaseState()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ProfileTenantActivity, "Error loading contracts", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun setupContractSpinner() {
        if (activeContracts.size > 1) {
            binding.spinnerContracts.isVisible = true
            val contractNames = activeContracts.map { "Unit ${it.roomId}" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contractNames)
            binding.spinnerContracts.adapter = adapter
            
            binding.spinnerContracts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateLeaseUI(activeContracts[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } else {
            binding.spinnerContracts.isVisible = false
        }
    }

    private fun updateLeaseUI(contract: Contract) {
        binding.tvContractType.text = "Contract: ${contract.renewalTerm ?: "N/A"}"
        binding.tvBaseRate.text = "Base rate: ₱${String.format(Locale.US, "%.2f", contract.baseRentAmount)}"
        
        // Robust Property Lookup with Fallbacks
        if (contract.propertyId != null) {
            fetchPropertyDetails(contract.propertyId, contract.roomId)
        } else if (contract.landlordId != null) {
            // Fallback 1: Look up property by landlordId
            FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(contract.landlordId).get()
                .addOnSuccessListener { snapshot ->
                    val property = snapshot.children.firstOrNull()?.getValue(Property::class.java)
                    if (property != null) {
                        fetchPropertyDetails(property.propertyId, contract.roomId)
                    } else {
                        // Fallback 2: Try user profile as a last resort
                        fetchPropertyIdFromUserProfile(contract)
                    }
                }
        } else {
            fetchPropertyIdFromUserProfile(contract)
        }
    }

    private fun fetchPropertyIdFromUserProfile(contract: Contract) {
        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            FirebaseManager.usersRef.child(userId).child("propertyId").get().addOnSuccessListener { snapshot ->
                val fallbackPropId = snapshot.getValue(String::class.java)
                fetchPropertyDetails(fallbackPropId, contract.roomId)
            }
        }
    }

    private fun fetchPropertyDetails(propertyId: String?, roomId: String?) {
        if (propertyId == null) {
            binding.tvResidence.text = "Residence: Unknown Property"
            binding.tvLeaseAddress.text = "Address: N/A"
            return
        }
        
        FirebaseManager.propertiesRef.child(propertyId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            val property = snapshot.getValue(Property::class.java)
            if (property != null) {
                val residenceText = if (!roomId.isNullOrEmpty()) "${property.propertyName}, Unit $roomId" else property.propertyName
                binding.tvResidence.text = "Residence: $residenceText"
                binding.tvLeaseAddress.text = "Address: ${property.address}"
            }
        }
    }

    private fun showNoLeaseState() {
        binding.tvResidence.text = "Residence: None"
        binding.tvLeaseAddress.text = "Address: None"
        binding.tvContractType.text = "Contract: None"
        binding.tvBaseRate.text = "Base rate: None"
    }

    override fun onDestroy() {
        super.onDestroy()
        contractsListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
            val isLandlord = currentUser?.role == "Landlord"
            val menuItems = arrayOf("Announcements", "Settings", "Log out")
            
            val popup = ListPopupWindow(this)
            popup.anchorView = view
            
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuItems) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(Color.WHITE)
                    view.setPadding(40, 30, 40, 30)
                    view.textSize = 14f
                    return view
                }
            }
            
            popup.setAdapter(adapter)
            popup.width = 600 
            popup.setBackgroundDrawable(ColorDrawable("#22223B".toColorInt()))
            
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> {
                        if (isLandlord) {
                            Toast.makeText(this, "Announcements coming soon", Toast.LENGTH_SHORT).show()
                        } else {
                            startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                        }
                    }
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
            val isLandlord = currentUser?.role == "Landlord"
            val intent = if (isLandlord) {
                Intent(this, DashboardLandlordActivity::class.java)
            } else {
                Intent(this, DashboardTenantActivity::class.java)
            }
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        binding.bottomNav.navNotifications.setOnClickListener {
            val isLandlord = currentUser?.role == "Landlord"
            val intent = if (isLandlord) {
                Intent(this, InboxLandlordActivity::class.java)
            } else {
                Intent(this, InboxTenantActivity::class.java)
            }
            startActivity(intent)
        }

        binding.bottomNav.navPayments.setOnClickListener {
            val isLandlord = currentUser?.role == "Landlord"
            if (isLandlord) {
                startActivity(Intent(this, PaymentLandlordActivity::class.java))
            } else {
                startActivity(Intent(this, PaymentHistoryTenantActivity::class.java))
            }
        }

        binding.bottomNav.navProfile.setOnClickListener {
            // Already here
        }
    }
}
