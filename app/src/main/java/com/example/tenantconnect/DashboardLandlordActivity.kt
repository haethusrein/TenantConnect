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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import coil.load
import com.example.tenantconnect.databinding.ActivityDashboardLandlordBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class DashboardLandlordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardLandlordBinding

    private var propertyListener: ValueEventListener? = null
    private var contractListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardLandlordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = intent.getStringExtra("USER_ID") ?: FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            checkPropertySetup(userId)
            listenForPropertyData(userId)
            listenForOccupancy(userId)
            loadLandlordInfo(userId)
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

    private fun listenForPropertyData(userId: String) {
        propertyListener = FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    val property = snapshot.children.firstOrNull()?.getValue(Property::class.java)
                    if (property != null) {
                        binding.tvTotalRooms.text = (property.totalRooms ?: 0).toString()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForOccupancy(userId: String) {
        contractListener = FirebaseManager.contractsRef.orderByChild("landlordId").equalTo(userId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    val occupiedCount = snapshot.children.count {
                        it.child("status").getValue(String::class.java) == "Active"
                    }
                    binding.tvOccupiedRooms.text = occupiedCount.toString()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        propertyListener?.let { FirebaseManager.propertiesRef.removeEventListener(it) }
        contractListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
    }

    private fun loadLandlordInfo(userId: String) {
        FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            val user = snapshot.getValue(User::class.java)
            if (user != null) {
                binding.tvGreeting.text = "Welcome, ${user.firstName}!"
                
                user.profilePhotoUrl?.let { uriString ->
                    binding.ivProfileSmall.load(uriString) {
                        crossfade(true)
                        placeholder(R.drawable.ic_person)
                        error(R.drawable.ic_person)
                    }
                } ?: run {
                    binding.ivProfileSmall.setImageResource(R.drawable.ic_person)
                }
            }
        }
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

    private fun logout() {
        // 1. Remove all active listeners to prevent "Permission Denied" errors
        propertyListener?.let { FirebaseManager.propertiesRef.removeEventListener(it) }
        contractListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
        propertyListener = null
        contractListener = null

        // 2. Perform sign out
        FirebaseManager.auth.signOut()

        // 3. Redirect to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
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
                    popup.dismiss()
                    logout()
                }
            }
            popup.dismiss()
        }
        popup.show()
    }

    private fun loadLandlordData(userId: String) {
        // Handled by real-time listeners
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
            startActivity(Intent(this, PaymentLandlordActivity::class.java))
        }

        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
