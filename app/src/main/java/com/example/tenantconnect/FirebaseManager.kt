package com.example.tenantconnect

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object FirebaseManager {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    // Explicitly providing the correct database URL from your Firebase Console
    val database: FirebaseDatabase get() = FirebaseDatabase.getInstance("https://tenantconnect-5838a3fe-default-rtdb.asia-southeast1.firebasedatabase.app")
    
    val usersRef get() = database.getReference("users")
    val propertiesRef get() = database.getReference("properties")
    val roomsRef get() = database.getReference("rooms")
    val contractsRef get() = database.getReference("contracts")
    val billingsRef get() = database.getReference("billings")
    val announcementsRef get() = database.getReference("announcements")
    val messagesRef get() = database.getReference("messages")
    val invitationsRef get() = database.getReference("invitations")
    val maintenanceRef get() = database.getReference("maintenance_requests")
    val transactionsRef get() = database.getReference("payment_transactions")
    val documentsRef get() = database.getReference("documents")
}
