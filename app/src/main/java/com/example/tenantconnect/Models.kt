package com.example.tenantconnect

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val userId: String? = null,
    val role: String? = null,
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val birthDate: String? = null,
    val gender: String? = null,
    val civilStatus: String? = null,
    val occupation: String? = null,
    val originalAddress: String? = null,
    val profilePhotoUrl: String? = null,
    val status: String? = "Active",
    val landlordId: String? = null, // New: Link to landlord
    val propertyId: String? = null,  // New: Link to property
    val createdAt: Long? = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class Property(
    val propertyId: String? = null,
    val landlordId: String? = null,
    val propertyName: String? = null,
    val address: String? = null,
    val totalRooms: Int? = 0,
    val amenities: List<String>? = null,
    val coverPhotoUrl: String? = null
)

@IgnoreExtraProperties
data class Room(
    val roomId: String? = null,
    val propertyId: String? = null,
    val roomNumber: String? = null,
    val baseRate: Double? = 0.0,
    val status: String? = "Vacant",
    val roomType: String? = null,
    val assets: List<String>? = null
)

@IgnoreExtraProperties
data class Contract(
    val contractId: String? = null,
    val tenantId: String? = null,
    val landlordId: String? = null, // Added
    val propertyId: String? = null, // Added
    val roomId: String? = null,
    val startDate: String? = null,
    val renewalTerm: String? = null,
    val paymentDueDay: Int? = 1,
    val baseRentAmount: Double? = 0.0,
    val wifiFeeAmount: Double? = 0.0,
    val waterBillingType: String? = null,
    val electricityBillingType: String? = null,
    val status: String? = "Active"
)

@IgnoreExtraProperties
data class Billing(
    val billingId: String? = null,
    val contractId: String? = null,
    val billingPeriodStart: String? = null,
    val billingPeriodEnd: String? = null,
    val dueDate: String? = null,
    val rentCharge: Double? = 0.0,
    val waterUsage: Double? = 0.0,
    val waterCharge: Double? = 0.0,
    val electricityUsage: Double? = 0.0,
    val electricityCharge: Double? = 0.0,
    val wifiArrearsCharge: Double? = 0.0,
    val totalAmount: Double? = 0.0,
    val status: String? = "Unpaid",
    val paymentMethod: String? = null,
    val datePaid: Long? = null
)

@IgnoreExtraProperties
data class Announcement(
    val announcementId: String? = null,
    val propertyId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val status: String? = "Active",
    val datePosted: Long? = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class Message(
    val messageId: String? = null,
    val chatId: String? = null, // Added for targeted queries
    val senderId: String? = null,
    val receiverId: String? = null,
    val messageText: String? = null,
    val timestamp: Long? = System.currentTimeMillis(),
    val isRead: Boolean? = false
)

@IgnoreExtraProperties
data class Invitation(
    val invitationId: String? = null,
    val tenantId: String? = null,
    val landlordId: String? = null,
    val landlordName: String? = null,
    val propertyName: String? = null,
    val status: String? = "Pending", // Pending, Accepted, Declined
    val timestamp: Long? = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class MaintenanceRequest(
    val ticketId: String? = null,
    val propertyId: String? = null,
    val roomId: String? = null,
    val tenantId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val urgency: String? = "Medium", // Low, Medium, High
    val status: String? = "Open", // Open, InProgress, Resolved
    val photoUrl: String? = null,
    val timestamp: Long? = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class PaymentTransaction(
    val transactionId: String? = null,
    val billingId: String? = null,
    val tenantId: String? = null,
    val amountPaid: Double? = 0.0,
    val paymentMethod: String? = null,
    val referenceNumber: String? = null,
    val proofOfPaymentUrl: String? = null,
    val paymentDate: Long? = System.currentTimeMillis(),
    val verificationStatus: String? = "Pending" // Pending, Verified, Rejected
)

@IgnoreExtraProperties
data class Document(
    val documentId: String? = null,
    val userId: String? = null,
    val contractId: String? = null,
    val documentType: String? = null, // e.g., Lease Agreement, ID
    val fileUrl: String? = null,
    val uploadDate: Long? = System.currentTimeMillis()
)
