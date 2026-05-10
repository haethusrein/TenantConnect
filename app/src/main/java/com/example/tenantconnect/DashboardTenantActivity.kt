package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Spinner
import android.widget.Toast
import coil.load
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.ActivityDashboardTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class DashboardTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardTenantBinding
    private var invitationListener: ValueEventListener? = null
    private var contractListener: ValueEventListener? = null
    private var billingListener: ValueEventListener? = null
    private var activeContracts = mutableListOf<Contract>()
    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = intent.getStringExtra("USER_ID") ?: FirebaseManager.auth.currentUser?.uid
        
        if (userId != null) {
            loadUserData(userId)
            listenForInvitations(userId)
            listenForContractChanges(userId)
        } else {
            // Handle case where user is not logged in
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setupBottomNavigation()
        setupMenu()
        setupDashboardButtons()
    }

    private fun listenForContractChanges(userId: String) {
        contractListener = FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    activeContracts = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                        .filter { it.status == "Active" }.toMutableList()

                    if (activeContracts.isNotEmpty()) {
                        binding.mainContentLayout.isVisible = true
                        binding.layoutEmpty.layoutEmptyAccommodation.isVisible = false
                        
                        setupContractSpinner()
                        // Initial update with the first contract
                        updateDashboardWithContract(activeContracts[0])
                    } else {
                        binding.spinnerContracts.isVisible = false
                        showNoAccommodationState()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupContractSpinner() {
        if (activeContracts.size > 1) {
            binding.spinnerContracts.isVisible = true
            
            // Map contracts to readable names for the spinner
            val contractNames = activeContracts.map { "Unit ${it.roomId}" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contractNames)
            binding.spinnerContracts.adapter = adapter
            
            binding.spinnerContracts.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateDashboardWithContract(activeContracts[position])
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        } else {
            binding.spinnerContracts.isVisible = false
        }
    }

    private fun updateDashboardWithContract(contract: Contract) {
        // Fallback: If contract.propertyId is null, try to get it from the current user profile
        if (contract.propertyId != null) {
            displayAccommodation(contract.propertyId, contract.roomId)
            displayRecentAnnouncement(contract.propertyId)
        } else {
            // Fetch propertyId from user record if missing in contract
            val userId = FirebaseManager.auth.currentUser?.uid
            if (userId != null) {
                FirebaseManager.usersRef.child(userId).child("propertyId").get().addOnSuccessListener { snapshot ->
                    val fallbackPropId = snapshot.getValue(String::class.java)
                    displayAccommodation(fallbackPropId, contract.roomId)
                    displayRecentAnnouncement(fallbackPropId)
                }
            }
        }
        
        listenForDashboardBilling(contract.contractId)
        
        // Update listeners for "View Details" and "View Payments" to pass current contract info
        binding.btnViewPayments.setOnClickListener {
            val intent = Intent(this, PaymentTenantActivity::class.java)
            intent.putExtra("CONTRACT_ID", contract.contractId)
            startActivity(intent)
        }
        binding.btnViewDetails.setOnClickListener {
            val intent = Intent(this, ViewContractActivity::class.java)
            intent.putExtra("CONTRACT_ID", contract.contractId)
            startActivity(intent)
        }
    }

    private fun listenForDashboardBilling(contractId: String?) {
        if (contractId == null) return
        
        billingListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        
        billingListener = FirebaseManager.billingsRef.orderByChild("contractId").equalTo(contractId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    val latestUnpaidBilling = snapshot.children.mapNotNull { it.getValue(Billing::class.java) }
                        .filter { it.status != "Paid" }
                        .maxByOrNull { it.dueDate ?: "" }

                    if (latestUnpaidBilling != null) {
                        binding.tvPaymentLabel.isVisible = true
                        binding.tvPaymentDueDate.isVisible = true
                        
                        val safeAmount = latestUnpaidBilling.totalAmount ?: 0.0
                        binding.tvPaymentAmount.text = String.format(Locale.US, "₱%.2f", safeAmount)
                        
                        binding.tvPaymentDueDate.text = "Due: ${latestUnpaidBilling.dueDate}"
                        binding.tvPaymentAmount.textSize = 24f
                        binding.tvPaymentAmount.setPadding(0, 0, 0, 0)
                    } else {
                        binding.tvPaymentLabel.isVisible = true
                        binding.tvPaymentDueDate.isVisible = true
                        binding.tvPaymentAmount.text = "₱0.00"
                        binding.tvPaymentDueDate.text = "No pending payments"
                        binding.tvPaymentAmount.textSize = 24f
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForInvitations(userId: String) {
        invitationListener = FirebaseManager.invitationsRef.orderByChild("tenantId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isDialogShowing) return
                    
                    for (invitationSnapshot in snapshot.children) {
                        val invitation = invitationSnapshot.getValue(Invitation::class.java)
                        if (invitation != null && (invitation.status == "Pending")) {
                            showInvitationDialog(invitation)
                            break
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun showInvitationDialog(invitation: Invitation) {
        isDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle("Tenant Invitation")
            .setMessage("Landlord ${invitation.landlordName} from ${invitation.propertyName} wants to add you as a tenant. Do you accept?")
            .setPositiveButton("Accept") { _, _ ->
                isDialogShowing = false
                acceptInvitation(invitation)
            }
            .setNegativeButton("Decline") { _, _ ->
                isDialogShowing = false
                declineInvitation(invitation)
            }
            .setCancelable(false)
            .show()
    }

    private fun acceptInvitation(invitation: Invitation) {
        val roomDialog = RoomSetupDialog(invitation)
        roomDialog.show(supportFragmentManager, "RoomSetupDialog")
    }

    private fun declineInvitation(invitation: Invitation) {
        invitation.invitationId?.let { id ->
            FirebaseManager.invitationsRef.child(id).child("status").setValue("Declined")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        invitationListener?.let { FirebaseManager.invitationsRef.removeEventListener(it) }
        contractListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
        billingListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
    }

    private fun logout() {
        invitationListener?.let { FirebaseManager.invitationsRef.removeEventListener(it) }
        contractListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
        billingListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        
        invitationListener = null
        contractListener = null
        billingListener = null

        FirebaseManager.auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }

    private fun setupDashboardButtons() {
        binding.btnViewAnnouncements.setOnClickListener {
            startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
        }
        binding.btnViewPayments.setOnClickListener {
            startActivity(Intent(this, PaymentTenantActivity::class.java))
        }
        binding.btnViewDetails.setOnClickListener {
            startActivity(Intent(this, ViewContractActivity::class.java))
        }
    }

    private fun loadUserData(userId: String) {
        FirebaseManager.usersRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return
                
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    binding.tvGreeting.text = "Hello, ${user.firstName}!"
                    ImageUtils.loadImage(binding.ivProfileSmall, user.profilePhotoUrl)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun displayAccommodation(propertyId: String?, roomId: String?) {
        if (propertyId == null) return
        FirebaseManager.propertiesRef.child(propertyId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            val property = snapshot.getValue(Property::class.java)
            if (property != null) {
                binding.tvAccommodationLabel.isVisible = true
                binding.tvAccommodationAddress.isVisible = true
                
                // Building name and Unit number
                val bldg = property.propertyName ?: "Apartment"
                val unit = if (!roomId.isNullOrEmpty()) "Unit $roomId" else "Room N/A"
                binding.tvAccommodation.text = "$bldg, $unit"
                
                // Full Address
                binding.tvAccommodationAddress.text = property.address ?: "Address N/A"
                
                // Adjust text size if it's too long
                if (binding.tvAccommodation.text.length > 20) {
                    binding.tvAccommodation.textSize = 24f
                } else {
                    binding.tvAccommodation.textSize = 36f
                }
            }
        }
    }

    private fun displayRecentAnnouncement(propertyId: String?) {
        if (propertyId == null) return
        FirebaseManager.announcementsRef.orderByChild("propertyId").equalTo(propertyId).limitToLast(1).get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val announcement = snapshot.children.firstOrNull()?.getValue(Announcement::class.java)
                if (announcement != null) {
                    binding.tvAnnouncementsContent.text = announcement.title
                } else {
                    binding.tvAnnouncementsContent.text = "No announcements yet"
                }
            }
    }

    private fun showNoAccommodationState() {
        binding.mainContentLayout.isVisible = false
        binding.layoutEmpty.layoutEmptyAccommodation.isVisible = true
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
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
                    "Announcements" -> startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> {
                        popup.dismiss()
                        logout()
                    }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener { /* Already here */ }
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
