package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.tenantconnect.databinding.ActivityAnnouncementsLandlordBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnnouncementsLandlordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnnouncementsLandlordBinding
    private var announcementsListener: ValueEventListener? = null
    private var propertyId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnnouncementsLandlordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        
        // Find the landlord's property first
        FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(currentLandlordId).get()
            .addOnSuccessListener { snapshot ->
                val property = snapshot.children.firstOrNull()?.getValue(Property::class.java)
                if (property != null) {
                    propertyId = property.propertyId
                    listenForAnnouncements(propertyId!!)
                } else {
                    Toast.makeText(this, "Please set up your property first.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

        binding.btnPostAnnouncement.setOnClickListener {
            showPostAnnouncementDialog()
        }

        setupMenu()
        setupBottomNavigation()
    }

    private fun listenForAnnouncements(propId: String) {
        announcementsListener = FirebaseManager.announcementsRef.orderByChild("propertyId").equalTo(propId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    binding.llAnnouncementsContainer.removeAllViews()
                    
                    val announcements = snapshot.children.mapNotNull { it.getValue(Announcement::class.java) }
                        .sortedByDescending { it.datePosted }

                    if (announcements.isEmpty()) {
                        showNoAnnouncements()
                    } else {
                        var index = 1
                        for (ann in announcements) {
                            addAnnouncementCard(index++, ann)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun addAnnouncementCard(index: Int, ann: Announcement) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_announcement, binding.llAnnouncementsContainer, false)
        
        card.findViewById<TextView>(R.id.tv_num).text = index.toString()
        card.findViewById<TextView>(R.id.tv_description).text = ann.description
        card.findViewById<TextView>(R.id.tv_category).text = ann.title ?: "Broadcast"
        
        val sdf = SimpleDateFormat("MM/dd/yy", Locale.US)
        val dateStr = sdf.format(Date(ann.datePosted ?: 0L))
        card.findViewById<TextView>(R.id.tv_date).text = "$dateStr - ${ann.status}"

        // Landlord-only CRUD: Long click to delete
        card.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Announcement")
                .setMessage("Are you sure you want to delete this announcement?")
                .setPositiveButton("Delete") { _, _ ->
                    ann.announcementId?.let { FirebaseManager.announcementsRef.child(it).removeValue() }
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        binding.llAnnouncementsContainer.addView(card)
    }

    private fun showNoAnnouncements() {
        val tv = TextView(this).apply {
            text = "No announcements posted yet."
            setTextColor(Color.GRAY)
            setPadding(0, 50, 0, 0)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        binding.llAnnouncementsContainer.addView(tv)
    }

    private fun showPostAnnouncementDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_post_announcement, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_announcement_title)
        val etDesc = dialogView.findViewById<EditText>(R.id.et_announcement_desc)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_post).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()

            if (title.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            postAnnouncement(title, desc)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun postAnnouncement(title: String, desc: String) {
        val id = FirebaseManager.announcementsRef.push().key ?: return
        val ann = Announcement(
            announcementId = id,
            propertyId = propertyId,
            title = title,
            description = desc,
            datePosted = System.currentTimeMillis()
        )

        FirebaseManager.announcementsRef.child(id).setValue(ann)
            .addOnSuccessListener {
                Toast.makeText(this, "Announcement Posted!", Toast.LENGTH_SHORT).show()
            }
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
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(Color.WHITE)
                    v.setPadding(40, 30, 40, 30)
                    v.textSize = 14f
                    return v
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
            val intent = Intent(this, DashboardLandlordActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
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
