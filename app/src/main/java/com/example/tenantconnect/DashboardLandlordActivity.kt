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
            setupMenu(view)
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

    private fun setupMenu(view: View) {
        val menuItems = arrayOf("Announcements", "Settings", "Log Out")
        
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
                    Toast.makeText(this, "Announcements coming soon", Toast.LENGTH_SHORT).show()
                }
                "Settings" -> {
                    startActivity(Intent(this, SettingsTenantActivity::class.java))
                }
                "Log Out" -> {
                    FirebaseManager.auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
            }
            popup.dismiss()
        }
        popup.show()
    }

    private fun loadLandlordData(userId: String) {
        FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                binding.tvGreeting.text = "Welcome, ${user.firstName}!"
            }
        }
        
        // Load actual stats
        FirebaseManager.roomsRef.orderByChild("landlordId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener

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
            startActivity(Intent(this, InboxLandlordActivity::class.java))
        }

        binding.bottomNav.navPayments.setOnClickListener {
            Toast.makeText(this, "Payments management coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
