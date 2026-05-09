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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivityDashboardTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class DashboardTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardTenantBinding
    private var invitationListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = intent.getStringExtra("USER_ID") ?: FirebaseManager.auth.currentUser?.uid
        
        if (userId != null) {
            loadUserData(userId)
            listenForInvitations(userId)
        } else {
            // Handle case where user is not logged in
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setupBottomNavigation()
        setupMenu()
        setupDashboardButtons()
    }

    private fun listenForInvitations(userId: String) {
        invitationListener = FirebaseManager.invitationsRef.orderByChild("tenantId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (invitationSnapshot in snapshot.children) {
                        val invitation = invitationSnapshot.getValue(Invitation::class.java)
                        if (invitation != null && invitation.status == "Pending") {
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
        AlertDialog.Builder(this)
            .setTitle("Tenant Invitation")
            .setMessage("Landlord ${invitation.landlordName} from ${invitation.propertyName} wants to add you as a tenant. Do you accept?")
            .setPositiveButton("Accept") { _, _ ->
                acceptInvitation(invitation)
            }
            .setNegativeButton("Decline") { _, _ ->
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
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                binding.tvGreeting.text = "Hello, ${user.firstName}!"
                checkAccommodationStatus(userId)
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading data: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAccommodationStatus(userId: String) {
        // Query contracts to see if this tenant has an active one
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                showNoAccommodationState()
            }
        }
    }

    private fun showNoAccommodationState() {
        val msg = "no accommodation. Please tell your landlord to register you as their tenant."
        
        // Announcements Box
        binding.tvAnnouncementsContent.text = msg
        
        // Payment Box
        binding.tvPaymentLabel.visibility = View.GONE
        binding.tvPaymentDueDate.visibility = View.GONE
        binding.tvPaymentAmount.text = msg
        binding.tvPaymentAmount.textSize = 14f
        binding.tvPaymentAmount.setPadding(0, 20, 0, 0)
        
        // Accommodations Box
        binding.tvAccommodationLabel.visibility = View.GONE
        binding.tvAccommodationAddress.visibility = View.GONE
        binding.tvAccommodation.text = msg
        binding.tvAccommodation.textSize = 14f
        binding.tvAccommodation.setPadding(0, 20, 0, 0)
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
            val menuItems = arrayOf(
                "Profile", "Announcements", "Payments", 
                "Accommodation", "Contact Landlord", "Settings", "Log out"
            )
            
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
            popup.setBackgroundDrawable(ColorDrawable(Color.parseColor("#22223B"))) 
            
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Profile" -> startActivity(Intent(this, ProfileTenantActivity::class.java))
                    "Announcements" -> startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                    "Payments" -> startActivity(Intent(this, PaymentTenantActivity::class.java))
                    "Accommodation" -> startActivity(Intent(this, ViewContractActivity::class.java))
                    "Contact Landlord" -> {
                        val intent = Intent(this, InboxTenantActivity::class.java)
                        startActivity(intent)
                    }
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> {
                        FirebaseManager.auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
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
