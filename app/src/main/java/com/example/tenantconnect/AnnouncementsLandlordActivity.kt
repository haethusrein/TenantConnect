package com.example.tenantconnect

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import java.util.Calendar
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
            showAnnouncementDialog(null) // Pass null to indicate "Create New"
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

                    val currentTime = System.currentTimeMillis()
                    val validAnnouncements = mutableListOf<Announcement>()

                    // AUTO-DELETE LOGIC: Clean up the database while sorting
                    for (annSnapshot in snapshot.children) {
                        val ann = annSnapshot.getValue(Announcement::class.java) ?: continue
                        if (ann.expiryDate != null && ann.expiryDate < currentTime) {
                            // Target is expired. Delete it from Firebase permanently.
                            annSnapshot.ref.removeValue()
                        } else {
                            validAnnouncements.add(ann)
                        }
                    }

                    val sortedAnnouncements = validAnnouncements.sortedByDescending { it.datePosted ?: 0L }

                    if (sortedAnnouncements.isEmpty()) {
                        showNoAnnouncements()
                    } else {
                        var index = 1
                        for (ann in sortedAnnouncements) {
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

        val sdf = SimpleDateFormat("MM/dd/yy h:mm a", Locale.US)
        val dateStr = sdf.format(Date(ann.datePosted ?: 0L))
        var badgeText = "$dateStr - ${ann.status}"

        if (ann.expiryDate != null) {
            badgeText += " (Expires: ${sdf.format(Date(ann.expiryDate))})"
        }

        card.findViewById<TextView>(R.id.tv_date).text = badgeText

        // UPDATED: Edit / Delete Menu
        card.setOnLongClickListener {
            val options = arrayOf("Edit", "Delete")
            AlertDialog.Builder(this)
                .setTitle("Manage Announcement")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showAnnouncementDialog(ann) // Edit
                        1 -> ann.announcementId?.let { FirebaseManager.announcementsRef.child(it).removeValue() } // Delete
                    }
                }
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

    // Handles BOTH Create and Edit modes
    private fun showAnnouncementDialog(existingAnn: Announcement?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_post_announcement, null)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_announcement_title)
        val etDesc = dialogView.findViewById<EditText>(R.id.et_announcement_desc)
        val tvExpiry = dialogView.findViewById<TextView>(R.id.tv_expiry_picker)

        var selectedExpiryTime: Long? = existingAnn?.expiryDate

        // Pre-fill if editing
        if (existingAnn != null) {
            tvDialogTitle.text = "Edit Announcement"
            etTitle.setText(existingAnn.title)
            etDesc.setText(existingAnn.description)
            if (selectedExpiryTime != null) {
                val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.US)
                tvExpiry.text = "Expires: ${sdf.format(Date(selectedExpiryTime!!))}"
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Trigger Date/Time Picker
        tvExpiry.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                TimePickerDialog(this, { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)

                    selectedExpiryTime = calendar.timeInMillis
                    val sdf = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.US)
                    tvExpiry.text = "Expires: ${sdf.format(calendar.time)}"

                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_post).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()

            if (title.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save to Firebase (Push new or overwrite existing)
            val id = existingAnn?.announcementId ?: FirebaseManager.announcementsRef.push().key ?: return@setOnClickListener

            val ann = Announcement(
                announcementId = id,
                propertyId = propertyId,
                title = title,
                description = desc,
                datePosted = existingAnn?.datePosted ?: System.currentTimeMillis(), // Keep original date if editing
                expiryDate = selectedExpiryTime
            )

            FirebaseManager.announcementsRef.child(id).setValue(ann)
                .addOnSuccessListener {
                    val msg = if (existingAnn == null) "Announcement Posted!" else "Announcement Updated!"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            dialog.dismiss()
        }
        dialog.show()
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
                    "Settings" -> startActivity(Intent(this, SettingsLandlordActivity::class.java))
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
            startActivity(Intent(this, LandlordDetailsActivity::class.java))
        }
    }
}