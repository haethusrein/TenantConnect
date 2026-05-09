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
import com.example.tenantconnect.databinding.ActivityAnnouncementsTenantBinding
import com.google.firebase.database.DataSnapshot

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnnouncementsTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnnouncementsTenantBinding

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
        // First get the tenant's property ID
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                val activeContract = snapshot.children.firstOrNull { 
                    it.child("status").getValue(String::class.java) == "Active" 
                }?.getValue(Contract::class.java)

                if (activeContract != null) {
                    val propertyId = activeContract.propertyId
                    if (propertyId != null) {
                        fetchAnnouncements(propertyId)
                    } else {
                        showNoAnnouncements("No property linked to your contract.")
                    }
                } else {
                    showNoAnnouncements("no accommodation. Please tell your landlord to register you as their tenant.")
                }
            }
    }

    private fun fetchAnnouncements(propertyId: String) {
        FirebaseManager.announcementsRef.orderByChild("propertyId").equalTo(propertyId).get()
            .addOnSuccessListener { snapshot ->
                binding.cvAnnouncementItem1.visibility = View.GONE
                binding.llAnnouncementsContainer.removeAllViews()

                if (!snapshot.exists()) {
                    showNoAnnouncements("No announcements yet for this property.")
                    return@addOnSuccessListener
                }

                var index = 1
                for (child in snapshot.children.reversed()) {
                    val announcement = child.getValue(Announcement::class.java)
                    if (announcement != null) {
                        addAnnouncementCard(index++, announcement)
                    }
                }
            }
    }

    private fun addAnnouncementCard(index: Int, announcement: Announcement) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_announcement, binding.llAnnouncementsContainer, false)
        
        val tvNum = card.findViewById<TextView>(R.id.tv_num)
        val tvDesc = card.findViewById<TextView>(R.id.tv_description)
        val tvCategory = card.findViewById<TextView>(R.id.tv_category)
        val tvDate = card.findViewById<TextView>(R.id.tv_date)

        tvNum.text = index.toString()
        tvDesc.text = announcement.description
        tvCategory.text = announcement.category ?: "General"
        
        val sdf = SimpleDateFormat("MM/dd/yy", Locale.US)
        val dateStr = sdf.format(Date(announcement.datePosted ?: 0L))
        tvDate.text = "$dateStr - ${announcement.status}"

        binding.llAnnouncementsContainer.addView(card)
    }

    private fun showNoAnnouncements(message: String) {
        binding.cvAnnouncementItem1.visibility = View.GONE
        binding.llAnnouncementsContainer.removeAllViews()
        val noAnnMsg = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 20, 0, 0)
        }
        binding.llAnnouncementsContainer.addView(noAnnMsg)
    }

    private fun checkAnnouncementsStatus(userId: String) {
        // Handled by loadAnnouncements
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
                    "Announcements" -> { /* Already here */ }
                    "Payments" -> startActivity(Intent(this, PaymentTenantActivity::class.java))
                    "Accommodation" -> startActivity(Intent(this, ViewContractActivity::class.java))
                    "Contact Landlord" -> startActivity(Intent(this, InboxTenantActivity::class.java))
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
