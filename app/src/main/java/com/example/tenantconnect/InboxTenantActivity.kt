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

import android.view.LayoutInflater
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InboxTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInboxTenantBinding
    private var targetUserId: String? = null
    private var isLandlordMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.ivBack.setOnClickListener { finish() }
        
        determineModeAndPartner()
        setupMenu()
        setupBottomNavigation()
    }

    private fun determineModeAndPartner() {
        val currentUserId = FirebaseManager.auth.currentUser?.uid ?: return
        
        FirebaseManager.usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            val role = snapshot.child("role").getValue(String::class.java)
            isLandlordMode = (role == "Landlord")
            
            if (isLandlordMode) {
                // For landlord, we need a list of tenants to talk to. 
                // For now, let's assume we pick the first tenant linked to this landlord.
                FirebaseManager.usersRef.orderByChild("landlordId").equalTo(currentUserId).limitToFirst(1).get()
                    .addOnSuccessListener { tenantSnapshot ->
                        targetUserId = tenantSnapshot.children.firstOrNull()?.key
                        if (targetUserId != null) {
                            setupMessaging()
                        } else {
                            Toast.makeText(this, "No tenants found to message.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                // For tenant, target is their landlord
                targetUserId = snapshot.child("landlordId").getValue(String::class.java)
                if (targetUserId != null) {
                    setupMessaging()
                } else {
                    Toast.makeText(this, "No landlord connected yet.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupMessaging() {
        val currentUserId = FirebaseManager.auth.currentUser?.uid ?: return
        val partnerId = targetUserId ?: return
        
        // Listen for messages in real-time
        FirebaseManager.messagesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isFinishing || isDestroyed) return
                
                binding.llMessagesContainer.removeAllViews()
                val messages = mutableListOf<Message>()
                for (child in snapshot.children) {
                    val message = child.getValue(Message::class.java)
                    if (message != null) {
                        // Filter for conversation between this user and their partner
                        if ((message.senderId == currentUserId && message.receiverId == partnerId) ||
                            (message.senderId == partnerId && message.receiverId == currentUserId)) {
                            messages.add(message)
                        }
                    }
                }
                
                // Sort by timestamp
                messages.sortBy { it.timestamp }
                
                for (msg in messages) {
                    addMessageBubble(msg, currentUserId)
                }
                
                // Scroll to bottom
                binding.scrollView.post {
                    binding.scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@InboxTenantActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                if (targetUserId != null) {
                    sendMessage(currentUserId, text)
                }
            }
        }
    }

    private fun addMessageBubble(message: Message, currentUserId: String) {
        val isMe = message.senderId == currentUserId
        val layoutRes = if (isMe) R.layout.item_message_right else R.layout.item_message_left
        val view = LayoutInflater.from(this).inflate(layoutRes, binding.llMessagesContainer, false)
        
        val tvMessage = view.findViewById<TextView>(R.id.tv_message)
        val tvTime = view.findViewById<TextView>(R.id.tv_time)
        val tvSender = if (!isMe) view.findViewById<TextView>(R.id.tv_sender) else null

        tvMessage.text = message.messageText
        
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        tvTime.text = sdf.format(Date(message.timestamp ?: 0L))

        if (!isMe && tvSender != null) {
            // Optional: fetch sender name for better UI
            tvSender.text = if (isLandlordMode) "Tenant" else "Landlord"
        }

        binding.llMessagesContainer.addView(view)
    }

    private fun sendMessage(senderId: String, text: String) {
        val partnerId = targetUserId ?: return
        val messageId = FirebaseManager.messagesRef.push().key ?: return
        val message = Message(
            messageId = messageId,
            senderId = senderId,
            receiverId = partnerId,
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
