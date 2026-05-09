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
import com.example.tenantconnect.databinding.ActivityInboxTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class InboxTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInboxTenantBinding
    private var landlordId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.ivBack.setOnClickListener { finish() }
        
        fetchLandlordInfo()
        setupMessaging()
        setupMenu()
        setupBottomNavigation()
    }

    private fun fetchLandlordInfo() {
        val currentUserId = FirebaseManager.auth.currentUser?.uid ?: return
        FirebaseManager.usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            landlordId = snapshot.child("landlordId").getValue(String::class.java)
        }
    }

    private fun setupMessaging() {
        val currentUserId = FirebaseManager.auth.currentUser?.uid ?: return
        
        // Listen for messages in real-time
        FirebaseManager.messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val message = child.getValue(Message::class.java)
                    if (message != null) {
                        // Filter for conversation between this tenant and their landlord
                        if ((message.senderId == currentUserId && message.receiverId == landlordId) ||
                            (message.senderId == landlordId && message.receiverId == currentUserId)) {
                            messages.add(message)
                        }
                    }
                }
                // Update UI here
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@InboxTenantActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                if (landlordId != null) {
                    sendMessage(currentUserId, text)
                } else {
                    Toast.makeText(this, "No landlord connected yet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendMessage(senderId: String, text: String) {
        val targetLandlordId = landlordId ?: return
        val messageId = FirebaseManager.messagesRef.push().key ?: return
        val message = Message(
            messageId = messageId,
            senderId = senderId,
            receiverId = targetLandlordId,
            messageText = text,
            timestamp = System.currentTimeMillis()
        )

        FirebaseManager.messagesRef.child(messageId).setValue(message)
            .addOnSuccessListener {
                binding.etMessage.setText("")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
            }
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
                    "Contact Landlord" -> { /* Already here */ }
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
            // Already here
        }
        binding.bottomNav.navPayments.setOnClickListener {
            startActivity(Intent(this, PaymentTenantActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
