package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivityDashboardLandlordBinding

class DashboardLandlordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardLandlordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardLandlordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = intent.getStringExtra("USER_ID") ?: FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            checkPropertySetup(userId)
            loadLandlordData(userId)
        }

        // Handle direct redirection from setup with a popup
        if (intent.getBooleanExtra("SHOW_ADD_TENANT", false)) {
            showAddTenantDialog()
        }

        binding.btnViewAllTenants.setOnClickListener {
            startActivity(Intent(this, ManageTenantsActivity::class.java))
        }

        binding.btnManageAnnouncements.setOnClickListener {
            // Placeholder for managing announcements
            Toast.makeText(this, "Announcements Management coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.ivMenu.setOnClickListener { view ->
            showPopupMenu(view)
        }

        setupBottomNavigation()
    }
    
    private fun checkPropertySetup(userId: String) {
        FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    // No property found, redirect to setup
                    val intent = Intent(this, LandlordDetailsActivity::class.java)
                    intent.putExtra("LANDLORD_ID", userId)
                    startActivity(intent)
                    finish() // Close dashboard so user must complete setup
                }
            }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("Profile")
        popup.menu.add("Tenants")
        popup.menu.add("Payments")
        popup.menu.add("Notifications")
        popup.menu.add("Inbox")
        popup.menu.add("Announcements")
        popup.menu.add("Settings")
        popup.menu.add("Log Out")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Profile" -> {
                    startActivity(Intent(this, ProfileTenantActivity::class.java))
                    true
                }
                "Tenants" -> {
                    startActivity(Intent(this, ManageTenantsActivity::class.java))
                    true
                }
                "Payments" -> {
                    Toast.makeText(this, "Payments coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                "Notifications" -> {
                    Toast.makeText(this, "Notifications coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                "Inbox" -> {
                    startActivity(Intent(this, InboxTenantActivity::class.java))
                    true
                }
                "Announcements" -> {
                    Toast.makeText(this, "Announcements coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                "Settings" -> {
                    Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                "Log Out" -> {
                    FirebaseManager.auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun loadLandlordData(userId: String) {
        FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                binding.tvGreeting.text = "Welcome, ${user.firstName}!"
            }
        }
        
        // Load actual stats
        FirebaseManager.roomsRef.orderByChild("landlordId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                val total = snapshot.childrenCount
                var occupied = 0
                snapshot.children.forEach { roomSnapshot ->
                    val status = roomSnapshot.child("status").getValue(String::class.java)
                    if (status == "Occupied") occupied++
                }
                binding.tvTotalRooms.text = total.toString()
                binding.tvOccupiedRooms.text = occupied.toString()
            }
    }

    private fun showAddTenantDialog() {
        val dialog = AddTenantDialog()
        dialog.show(supportFragmentManager, "AddTenantDialog")
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener {
            // Already here
        }

        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, InboxTenantActivity::class.java))
        }

        binding.bottomNav.navPayments.setOnClickListener {
            // Target Landlord Payments Activity when created
            Toast.makeText(this, "Payments management coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
