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
import android.widget.Toast
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
        // Targeted Query: Listen for the tenant's active contract in real-time
        contractListener = FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    val activeContract = snapshot.children.firstOrNull { 
                        it.child("status").getValue(String::class.java) == "Active" 
                    }?.getValue(Contract::class.java)

                    if (activeContract != null) {
                        // Switch UI to active state immediately
                        binding.mainContentLayout.isVisible = true
                        binding.layoutEmpty.root.isVisible = false
                        
                        displayAccommodation(activeContract.propertyId, activeContract.roomId)
                        displayLatestPayment(activeContract.contractId)
                        displayRecentAnnouncement(activeContract.propertyId)
                    } else {
                        // Show empty state if no active contract found
                        showNoAccommodationState()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Log error if needed
                }
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
                            break // Show one at a time
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Log or handle error
                }
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
        // Show dialog to fill room details
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
        invitationListener?.let {
            FirebaseManager.invitationsRef.removeEventListener(it)
        }
        contractListener?.let {
            FirebaseManager.contractsRef.removeEventListener(it)
        }
    }

    private fun setupDashboardButtons() {
        binding.btnViewAnnouncements.setOnClickListener {
            startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
        }
        binding.btnViewPayments.setOnClickListener {
            startActivity(Intent(this, PaymentHistoryTenantActivity::class.java))
        }
        binding.btnViewDetails.setOnClickListener {
            startActivity(Intent(this, ViewContractActivity::class.java))
        }
    }

    private fun loadUserData(userId: String) {
        FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                binding.tvGreeting.text = "Hello, ${user.firstName}!"
                
                user.profilePhotoUrl?.let { uriString ->
                    binding.ivProfileSmall.setImageURI(Uri.parse(uriString))
                } ?: run {
                    binding.ivProfileSmall.setImageResource(R.drawable.ic_person)
                }

                // Contract and property data now handled by listenForContractChanges
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading data: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayAccommodation(propertyId: String?, roomId: String?) {
        if (propertyId == null) return
        FirebaseManager.propertiesRef.child(propertyId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            val property = snapshot.getValue(Property::class.java)
            if (property != null) {
                binding.tvAccommodationLabel.isVisible = true
                binding.tvAccommodationAddress.isVisible = true
                
                val displayText = if (roomId != null) "${property.propertyName}, $roomId" else property.propertyName
                binding.tvAccommodation.text = displayText
                binding.tvAccommodationAddress.text = property.address
            }
        }
    }

    private fun displayLatestPayment(contractId: String?) {
        if (contractId == null) return
        FirebaseManager.billingsRef.orderByChild("contractId").equalTo(contractId).limitToLast(1).get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val billing = snapshot.children.firstOrNull()?.getValue(Billing::class.java)
                if (billing != null) {
                    binding.tvPaymentLabel.isVisible = true
                    binding.tvPaymentDueDate.isVisible = true
                    
                    val safeAmount = billing.totalAmount ?: 0.0
                    binding.tvPaymentAmount.text = "₱${String.format(Locale.US, "%.2f", safeAmount)}"
                    
                    binding.tvPaymentDueDate.text = "Due: ${billing.dueDate}"
                    binding.tvPaymentAmount.textSize = 24f
                    binding.tvPaymentAmount.setPadding(0, 0, 0, 0)
                } else {
                    binding.tvPaymentAmount.text = "No pending bills"
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
        binding.layoutEmpty.root.isVisible = true
    }

    private fun logout() {
        // 1. Remove all active listeners to prevent "Permission Denied" errors
        invitationListener?.let { FirebaseManager.invitationsRef.removeEventListener(it) }
        contractListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
        invitationListener = null
        contractListener = null

        // 2. Perform sign out
        FirebaseManager.auth.signOut()

        // 3. Redirect to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
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
        binding.bottomNav.navHome.setOnClickListener {
            // Already here
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
            val intent = Intent(this, ProfileTenantActivity::class.java)
            startActivity(intent)
        }
    }
}
