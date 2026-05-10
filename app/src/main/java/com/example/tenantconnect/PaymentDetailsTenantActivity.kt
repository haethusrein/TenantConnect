package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.ActivityPaymentDetailsTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class PaymentDetailsTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentDetailsTenantBinding
    private var activeBilling: Billing? = null
    private var activeContract: Contract? = null
    private var currentProperty: Property? = null
    private var selectedMethod: String = ""
    private var billingListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentDetailsTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        
        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            loadInitialData(userId)
        }

        setupPaymentMethodSelector()
        setupButtons()
        setupMenu()
        setupBottomNavigation()
    }

    private fun loadInitialData(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                activeContract = snapshot.children.firstOrNull { 
                    it.child("status").getValue(String::class.java) == "Active" 
                }?.getValue(Contract::class.java)

                activeContract?.let { contract ->
                    fetchPropertyInfo(contract.propertyId)
                    fetchTenantInfo(userId)
                    listenForLatestBilling(contract.contractId)
                }
            }
    }

    private fun listenForLatestBilling(contractId: String?) {
        if (contractId == null) return
        billingListener = FirebaseManager.billingsRef.orderByChild("contractId").equalTo(contractId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    activeBilling = snapshot.children.mapNotNull { it.getValue(Billing::class.java) }
                        .filter { it.status != "Paid" }
                        .maxByOrNull { it.dueDate ?: "" }
                    
                    activeBilling?.let { displayBillingBreakdown(it) } ?: run {
                        resetBillingDisplay()
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun resetBillingDisplay() {
        binding.tvBillingAmount.text = "₱0.00"
        binding.tvDueDate.text = "No pending bills"
        binding.tvTotalBalance.text = "To Pay: ₱0.00"
        binding.tvRentalBreakdown.text = "Rental: ₱0.00"
        binding.tvWaterBreakdown.text = "Water Bill: ₱0.00"
        binding.tvElectricBreakdown.text = "Electric Bill: ₱0.00"
        binding.tvWifiBreakdown.text = "Wi-fi Arrears: ₱0.00"
        binding.tvTotalBreakdown.text = "Total: ₱0.00"
    }

    private fun fetchPropertyInfo(propertyId: String?) {
        if (propertyId == null) return
        FirebaseManager.propertiesRef.child(propertyId).get().addOnSuccessListener { snapshot ->
            currentProperty = snapshot.getValue(Property::class.java)
            currentProperty?.let { prop ->
                binding.tvUnit.text = "Unit: ${activeContract?.roomId ?: "-"}"
                
                FirebaseManager.usersRef.child(prop.landlordId ?: "").get().addOnSuccessListener { userSnap ->
                    val landlord = userSnap.getValue(User::class.java)
                    binding.tvOwner.text = "Owner: ${landlord?.firstName} ${landlord?.lastName}"
                }
            }
        }
    }

    private fun fetchTenantInfo(userId: String) {
        FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            val user = snapshot.getValue(User::class.java)
            binding.tvRecipient.text = "Recipient: ${user?.firstName} ${user?.lastName}"
        }
    }

    private fun displayBillingBreakdown(billing: Billing) {
        val locale = Locale.US
        val total = billing.totalAmount ?: 0.0
        
        binding.tvBillingAmount.text = String.format(locale, "₱%.2f", total)
        binding.tvDueDate.text = "Due in: ${billing.dueDate}"
        binding.tvTotalBalance.text = String.format(locale, "To Pay: ₱%.2f", total)
        
        binding.tvRentalBreakdown.text = String.format(locale, "Rental: ₱%.2f", billing.rentCharge ?: 0.0)
        binding.tvWaterBreakdown.text = String.format(locale, "Water Bill: ₱%.2f", billing.waterCharge ?: 0.0)
        binding.tvElectricBreakdown.text = String.format(locale, "Electric Bill: ₱%.2f", billing.electricityCharge ?: 0.0)
        binding.tvWifiBreakdown.text = String.format(locale, "Wi-fi Arrears: ₱%.2f", billing.wifiArrearsCharge ?: 0.0)
        binding.tvTotalBreakdown.text = String.format(locale, "Total: ₱%.2f", total)
    }

    private fun setupPaymentMethodSelector() {
        val methods = arrayOf("Cash", "Online Pay")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, methods)
        binding.etPaymentMethod.setAdapter(adapter)
        
        binding.etPaymentMethod.setOnClickListener {
            binding.etPaymentMethod.showDropDown()
        }

        binding.etPaymentMethod.setOnItemClickListener { _, _, position, _ ->
            selectedMethod = methods[position]
            handleMethodSelection(selectedMethod)
        }
    }

    private fun handleMethodSelection(method: String) {
        if (method == "Cash") {
            binding.btnPay.text = "Confirm Payment"
            binding.btnPay.isVisible = true
            sendCashNotification()
        } else if (method == "Online Pay") {
            showQrDialog()
        }
    }

    private fun showQrDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_online_payment_qr, null)
        val ivQr = dialogView.findViewById<android.widget.ImageView>(R.id.iv_landlord_qr)
        val btnPaid = dialogView.findViewById<android.widget.Button>(R.id.btn_already_paid)
        
        currentProperty?.coverPhotoUrl?.let {
            ivQr.setImageURI(it.toUri())
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnPaid.setOnClickListener {
            dialog.dismiss()
            showOnlineTransactionDialog()
        }
        
        dialog.show()
    }

    private fun showOnlineTransactionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_online_transaction_form, null)
        val etTransId = dialogView.findViewById<android.widget.EditText>(R.id.et_transaction_id)
        val etGcash = dialogView.findViewById<android.widget.EditText>(R.id.et_gcash_info)
        val etAmount = dialogView.findViewById<android.widget.EditText>(R.id.et_amount_to_pay)
        val btnSubmit = dialogView.findViewById<android.widget.Button>(R.id.btn_submit_transaction)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnSubmit.setOnClickListener {
            val transId = etTransId.text.toString().trim()
            val gcash = etGcash.text.toString().trim()
            val amountStr = etAmount.text.toString().trim()

            if (transId.isEmpty() || gcash.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(this, "Please fill all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitOnlineTransaction(transId, gcash, amountStr.toDoubleOrNull() ?: 0.0)
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun sendCashNotification() {
        val currentUserId = FirebaseManager.auth.currentUser?.uid ?: return
        val landlordId = currentProperty?.landlordId ?: return
        
        val ids = listOf(currentUserId, landlordId).sorted()
        val chatId = "${ids[0]}_${ids[1]}"
        
        val messageId = FirebaseManager.messagesRef.child(chatId).push().key ?: return
        val msg = Message(
            messageId = messageId,
            chatId = chatId,
            senderId = currentUserId,
            receiverId = landlordId,
            messageText = "I will pay with cash for my current bill.",
            timestamp = System.currentTimeMillis()
        )
        
        FirebaseManager.messagesRef.child(chatId).child(messageId).setValue(msg)
            .addOnSuccessListener {
                Toast.makeText(this, "Landlord notified: Paying with cash", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupButtons() {
        binding.btnPay.setOnClickListener {
            if (selectedMethod == "Cash") {
                Toast.makeText(this, "Please coordinate physical payment with your landlord.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun submitOnlineTransaction(transId: String, gcash: String, amount: Double) {
        val billingTotal = activeBilling?.totalAmount ?: 0.0

        if (amount <= 0.0 || amount > billingTotal) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnPay.isEnabled = false
        
        val transactionId = FirebaseManager.transactionsRef.push().key ?: return
        val transaction = PaymentTransaction(
            transactionId = transactionId,
            billingId = activeBilling?.billingId,
            tenantId = FirebaseManager.auth.currentUser?.uid,
            amountPaid = amount,
            paymentMethod = "Online (GCash)",
            referenceNumber = transId,
            verificationStatus = "Pending"
        )

        FirebaseManager.transactionsRef.child(transactionId).setValue(transaction)
            .addOnSuccessListener {
                Toast.makeText(this, "Transaction submitted for verification!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                binding.btnPay.isEnabled = true
                Toast.makeText(this, "Submission failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
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
            val intent = Intent(this, PaymentTenantActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
