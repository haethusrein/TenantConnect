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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenantconnect.databinding.ActivityInboxTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class InboxTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInboxTenantBinding
    private lateinit var adapter: MessageAdapter
    private var targetUserId: String? = null
    private var isLandlordMode: Boolean = false
    private var chatId: String? = null
    private var activeContractId: String? = null
    private var messagesListener: ValueEventListener? = null

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
        
        // Targeted Routing: Expect a specific user ID to chat with passed via Intent
        targetUserId = intent.getStringExtra("TARGET_USER_ID")

        FirebaseManager.usersRef.child(currentUserId).get().addOnSuccessListener { snapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            
            val role = snapshot.child("role").getValue(String::class.java)
            isLandlordMode = (role == "Landlord")
            
            if (isLandlordMode) {
                if (targetUserId == null) {
                    Toast.makeText(this, "No chat partner selected", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
            } else {
                // For tenant, if TARGET_USER_ID is missing, fall back to their assigned landlordId
                if (targetUserId == null) {
                    targetUserId = snapshot.child("landlordId").getValue(String::class.java)
                }
                
                if (targetUserId == null) {
                    Toast.makeText(this, "No landlord connected yet.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
            }

            verifyContractAndSetupChat(currentUserId, targetUserId!!)
        }
    }

    private fun verifyContractAndSetupChat(currentUserId: String, partnerId: String) {
        // Query contracts where current user is either tenant or landlord and partner is the other party
        FirebaseManager.contractsRef.orderByChild("status").equalTo("Active").get()
            .addOnSuccessListener { snapshot ->
                val contract = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                    .firstOrNull { 
                        (it.tenantId == currentUserId && it.landlordId == partnerId) ||
                        (it.tenantId == partnerId && it.landlordId == currentUserId)
                    }

                if (contract != null) {
                    activeContractId = contract.contractId
                    
                    // Create unique chatId including contractId to prevent merging histories of different leases
                    val ids = listOf(currentUserId, partnerId).sorted()
                    chatId = "${ids[0]}_${ids[1]}_$activeContractId"
                    
                    setupRecyclerView(currentUserId)
                    setupMessaging()
                } else {
                    Toast.makeText(this, "Unauthorized: No active lease found", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Verification failed: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupRecyclerView(currentUserId: String) {
        adapter = MessageAdapter(currentUserId, isLandlordMode)
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Messages start from the bottom
        }
        binding.rvMessages.adapter = adapter
    }

    private fun setupMessaging() {
        val currentUserId = FirebaseManager.auth.currentUser?.uid ?: return
        val currentChatId = chatId ?: return
        
        // Targeted Query: Attach listener strictly to the specific chatId node
        messagesListener = FirebaseManager.messagesRef.child(currentChatId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    val messagesList = mutableListOf<Message>()
                    for (child in snapshot.children) {
                        child.getValue(Message::class.java)?.let { messagesList.add(it) }
                    }
                    
                    // Display messages and scroll to the bottom
                    messagesList.sortBy { it.timestamp }
                    adapter.submitList(messagesList)
                    
                    if (messagesList.isNotEmpty()) {
                        binding.rvMessages.smoothScrollToPosition(messagesList.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@InboxTenantActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(currentUserId, text)
            }
        }
    }

    private fun sendMessage(senderId: String, text: String) {
        val currentChatId = chatId ?: return
        val partnerId = targetUserId ?: return
        // Messages are now stored under their specific chatId node
        val messageId = FirebaseManager.messagesRef.child(currentChatId).push().key ?: return
        
        val message = Message(
            messageId = messageId,
            chatId = currentChatId,
            senderId = senderId,
            receiverId = partnerId,
            messageText = text,
            timestamp = System.currentTimeMillis()
        )

        FirebaseManager.messagesRef.child(currentChatId).child(messageId).setValue(message)
            .addOnSuccessListener {
                binding.etMessage.setText("")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Memory Leak Fix: Remove listener when activity is destroyed
        chatId?.let { id ->
            messagesListener?.let { 
                FirebaseManager.messagesRef.child(id).removeEventListener(it) 
            }
        }
    }

    private fun logout() {
        // 1. Remove all active listeners to prevent "Permission Denied" errors
        chatId?.let { id ->
            messagesListener?.let { 
                FirebaseManager.messagesRef.child(id).removeEventListener(it) 
            }
        }
        messagesListener = null

        // 2. Perform sign out
        FirebaseManager.auth.signOut()

        // 3. Redirect to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
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
            popup.setBackgroundDrawable(ColorDrawable(Color.parseColor("#22223B")))
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> {
                        popup.dismiss()
                        logout()
                    }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener {
            val intent = if (isLandlordMode) {
                Intent(this, DashboardLandlordActivity::class.java)
            } else {
                Intent(this, DashboardTenantActivity::class.java)
            }
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        
        binding.bottomNav.navNotifications.setOnClickListener {
            if (isLandlordMode) {
                startActivity(Intent(this, InboxLandlordActivity::class.java))
            } else {
                // Already here (Inbox)
            }
        }
        
        binding.bottomNav.navPayments.setOnClickListener {
            if (isLandlordMode) {
                Toast.makeText(this, "Payments management coming soon", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, PaymentTenantActivity::class.java))
            }
        }

        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
