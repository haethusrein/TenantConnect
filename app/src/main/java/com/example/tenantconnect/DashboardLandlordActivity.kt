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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class DashboardLandlordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardLandlordBinding

    private var propertyListener: ValueEventListener? = null
    private var contractListener: ValueEventListener? = null
    private var earningsListener: ValueEventListener? = null
    private var announcementsListener: ValueEventListener? = null // NEW LISTENER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardLandlordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = intent.getStringExtra("USER_ID") ?: FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            checkPropertySetup(userId)
            listenForPropertyData(userId)
            listenForOccupancy(userId)
            listenForEarnings(userId)
            loadLandlordInfo(userId)
        }

        if (intent.getBooleanExtra("SHOW_ADD_TENANT", false)) {
            showAddTenantDialog()
        }

        binding.btnViewAllTenants.setOnClickListener {
            startActivity(Intent(this, ManageTenantsActivity::class.java))
        }

        binding.btnDashboardViewPayments.setOnClickListener {
            startActivity(Intent(this, PaymentLandlordActivity::class.java))
        }

        binding.btnManageAnnouncements.setOnClickListener {
            startActivity(Intent(this, AnnouncementsLandlordActivity::class.java))
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

                        // NEW: Once we have the property ID, load the latest announcement!
                        property.propertyId?.let { loadRecentAnnouncement(it) }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadRecentAnnouncement(propertyId: String) {
        announcementsListener?.let { FirebaseManager.announcementsRef.removeEventListener(it) }

        announcementsListener = FirebaseManager.announcementsRef.orderByChild("propertyId").equalTo(propertyId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    val currentTime = System.currentTimeMillis()

                    // Filter out expired announcements, then sort by newest first
                    val validAnnouncements = snapshot.children.mapNotNull { it.getValue(Announcement::class.java) }
                        .filter { it.expiryDate == null || it.expiryDate > currentTime }
                        .sortedByDescending { it.datePosted ?: 0L }

                    if (validAnnouncements.isNotEmpty()) {
                        val latest = validAnnouncements.first()

                        // UPDATED: Now it only displays the title!
                        binding.tvAnnouncementsEmpty.text = "Latest: ${latest.title ?: "Broadcast"}"
                        binding.tvAnnouncementsEmpty.alpha = 1.0f // Make it fully visible

                    } else {
                        binding.tvAnnouncementsEmpty.text = "No active announcements."
                        binding.tvAnnouncementsEmpty.alpha = 0.7f // Dim it slightly
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
                    
                    // Count unique room IDs that have an "Active" status
                    val uniqueOccupiedRooms = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                        .filter { it.status == "Active" }
                        .mapNotNull { it.roomId }
                        .distinct()
                        .size
                        
                    binding.tvOccupiedRooms.text = uniqueOccupiedRooms.toString()
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun listenForEarnings(userId: String) {
        earningsListener = FirebaseManager.billingsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return

                FirebaseManager.contractsRef.orderByChild("landlordId").equalTo(userId).get()
                    .addOnSuccessListener { contractSnapshot ->
                        val myContractIds = contractSnapshot.children.mapNotNull { it.key }

                        var totalEarnings = 0.0
                        for (billSnap in snapshot.children) {
                            val bill = billSnap.getValue(Billing::class.java)
                            if (bill != null && bill.status == "Paid" && myContractIds.contains(bill.contractId)) {
                                totalEarnings += (bill.totalAmount ?: 0.0)
                            }
                        }

                        binding.tvCurrentEarnings.text = String.format(Locale.US, "₱%.2f", totalEarnings)
                    }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadLandlordInfo(userId: String) {
        FirebaseManager.usersRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    binding.tvGreeting.text = "Welcome, ${user.firstName}!"
                    ImageUtils.loadImage(binding.ivProfileSmall, user.profilePhotoUrl)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun checkPropertySetup(userId: String) {
        FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val intent = Intent(this, LandlordDetailsActivity::class.java)
                    intent.putExtra("LANDLORD_ID", userId)
                    startActivity(intent)
                    finish()
                }
            }
    }

    private fun logout() {
        propertyListener?.let { FirebaseManager.propertiesRef.removeEventListener(it) }
        contractListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
        earningsListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        announcementsListener?.let { FirebaseManager.announcementsRef.removeEventListener(it) } // Safely remove listener

        propertyListener = null
        contractListener = null
        earningsListener = null
        announcementsListener = null

        FirebaseManager.auth.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }

    override fun onDestroy() {
        super.onDestroy()
        propertyListener?.let { FirebaseManager.propertiesRef.removeEventListener(it) }
        contractListener?.let { FirebaseManager.contractsRef.removeEventListener(it) }
        earningsListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        announcementsListener?.let { FirebaseManager.announcementsRef.removeEventListener(it) }
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
                    startActivity(Intent(this, AnnouncementsLandlordActivity::class.java))
                }
                "Settings" -> {
                    startActivity(Intent(this, SettingsLandlordActivity::class.java))
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

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener { }

        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, InboxLandlordActivity::class.java))
        }

        binding.bottomNav.navPayments.setOnClickListener {
            startActivity(Intent(this, PaymentLandlordActivity::class.java))
        }

        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, LandlordDetailsActivity::class.java))
        }
    }

    private fun showAddTenantDialog() {
        val dialog = AddTenantDialog()
        dialog.show(supportFragmentManager, "AddTenantDialog")
    }
}