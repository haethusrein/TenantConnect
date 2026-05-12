package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.tenantconnect.databinding.ActivityAnnouncementsTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnnouncementsTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnnouncementsTenantBinding
    private var announcementsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementsTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            loadAnnouncements(userId)
        }

        setupMenu()
        setupBottomNavigation()
    }

    private fun loadAnnouncements(userId: String) {
        // First get the tenant's active contract to find the propertyId
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                val activeContract = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                    .firstOrNull { it.status == "Active" }

                if (activeContract != null) {
                    val propertyId = activeContract.propertyId
                    if (propertyId != null) {
                        listenForAnnouncements(propertyId)
                    } else {
                        // Fallback: Check user profile for propertyId
                        FirebaseManager.usersRef.child(userId).child("propertyId").get().addOnSuccessListener { userSnap ->
                            val fallbackId = userSnap.getValue(String::class.java)
                            if (fallbackId != null) listenForAnnouncements(fallbackId)
                            else showNoAnnouncements("No property linked to your stay.")
                        }
                    }
                } else {
                    showNoAnnouncements("You don't have an active accommodation yet.")
                }
            }
    }

    private fun listenForAnnouncements(propertyId: String) {
        announcementsListener = FirebaseManager.announcementsRef.orderByChild("propertyId").equalTo(propertyId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    binding.llAnnouncementsContainer.removeAllViews()

                    val currentTime = System.currentTimeMillis()

                    // Filter out announcements that have an expiryDate in the past
                    val announcements = snapshot.children.mapNotNull { it.getValue(Announcement::class.java) }
                        .filter { it.expiryDate == null || it.expiryDate > currentTime }
                        .sortedByDescending { it.datePosted ?: 0L }

                    if (announcements.isEmpty()) {
                        showNoAnnouncements("No announcements yet for this property.")
                    } else {
                        var index = 1
                        for (ann in announcements) {
                            addAnnouncementCard(index++, ann)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@AnnouncementsTenantActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun addAnnouncementCard(index: Int, announcement: Announcement) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_announcement, binding.llAnnouncementsContainer, false)
        
        card.findViewById<TextView>(R.id.tv_num).text = index.toString()
        card.findViewById<TextView>(R.id.tv_description).text = announcement.description
        card.findViewById<TextView>(R.id.tv_category).text = announcement.title ?: "Broadcast"
        
        val sdf = SimpleDateFormat("MM/dd/yy", Locale.US)
        val dateStr = sdf.format(Date(announcement.datePosted ?: 0L))
        card.findViewById<TextView>(R.id.tv_date).text = "$dateStr - ${announcement.status}"

        binding.llAnnouncementsContainer.addView(card)
    }

    private fun showNoAnnouncements(message: String) {
        binding.llAnnouncementsContainer.removeAllViews()
        val noAnnMsg = TextView(this).apply {
            text = message
            setTextColor(Color.GRAY)
            textSize = 14f
            setPadding(0, 40, 0, 0)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        binding.llAnnouncementsContainer.addView(noAnnMsg)
    }

    override fun onDestroy() {
        super.onDestroy()
        announcementsListener?.let { FirebaseManager.announcementsRef.removeEventListener(it) }
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
                    "Announcements" -> { /* Already here */ }
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
