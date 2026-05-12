package com.example.tenantconnect

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import coil.load
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.ActivityPaymentDetailsTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.util.Locale

class PaymentDetailsTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentDetailsTenantBinding
    private var activeBilling: Billing? = null
    private var activeContract: Contract? = null
    private var currentProperty: Property? = null
    private var selectedMethod: String = ""
    private var activeContracts = mutableListOf<Contract>()
    private var billingListener: ValueEventListener? = null
    
    private var selectedProofUri: Uri? = null

    private val selectProofLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedProofUri = it
            Toast.makeText(this, "Proof photo selected!", Toast.LENGTH_SHORT).show()
        }
    }

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
                activeContracts = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                    .filter { it.status == "Active" }.toMutableList()

                if (activeContracts.isNotEmpty()) {
                    setupContractSpinner()
                    updatePaymentInfo(activeContracts[0])
                } else {
                    binding.spinnerContracts.isVisible = false
                    resetBillingDisplay()
                }
            }
    }

    private fun setupContractSpinner() {
        if (activeContracts.size > 1) {
            binding.spinnerContracts.isVisible = true
            val contractNames = activeContracts.map { "Unit ${it.roomId}" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, contractNames)
            binding.spinnerContracts.adapter = adapter
            
            binding.spinnerContracts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updatePaymentInfo(activeContracts[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        } else {
            binding.spinnerContracts.isVisible = false
        }
    }

    private fun updatePaymentInfo(contract: Contract) {
        activeContract = contract
        val userId = FirebaseManager.auth.currentUser?.uid ?: return
        
        fetchPropertyInfo(contract.propertyId)
        fetchTenantInfo(userId)
        listenForLatestBilling(contract.contractId)
    }

    private fun listenForLatestBilling(contractId: String?) {
        if (contractId == null) return
        billingListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        
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
        binding.etPaymentMethod.setOnClickListener { binding.etPaymentMethod.showDropDown() }
        binding.etPaymentMethod.setOnItemClickListener { _, _, position, _ ->
            selectedMethod = methods[position]
            handleMethodSelection(selectedMethod)
        }
    }

    private fun handleMethodSelection(method: String) {
        if (method == "Cash") {
            binding.containerOnlinePay.isVisible = false
            binding.containerTransactionForm.isVisible = false
            binding.btnPay.text = "Confirm Cash Notify"
            binding.btnPay.isVisible = true
        } else if (method == "Online Pay") {
            binding.containerOnlinePay.isVisible = true
            binding.containerTransactionForm.isVisible = false
            binding.btnPay.isVisible = false
            currentProperty?.coverPhotoUrl?.let { ImageUtils.loadImage(binding.ivLandlordQr, it, android.R.drawable.ic_menu_gallery) }
        }
    }

    private fun setupButtons() {
        binding.btnAlreadyPaid.setOnClickListener {
            binding.containerOnlinePay.isVisible = false
            binding.containerTransactionForm.isVisible = true
            binding.btnPay.isVisible = true
            binding.btnPay.text = "Submit Transaction"
        }
        
        binding.btnPay.setOnClickListener {
            if (selectedMethod == "Cash") {
                sendCashNotification()
            } else {
                val transId = binding.etTransactionId.text.toString().trim()
                val gcash = binding.etGcashInfo.text.toString().trim()
                val amountStr = binding.etAmountToPay.text.toString().trim()

                if (transId.isEmpty() || gcash.isEmpty() || amountStr.isEmpty()) {
                    Toast.makeText(this, "Please fill all details", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (selectedProofUri == null) {
                    AlertDialog.Builder(this)
                        .setTitle("Upload Proof")
                        .setMessage("Please attach a screenshot of your GCash receipt.")
                        .setPositiveButton("Select Photo") { _, _ -> selectProofLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    submitOnlineTransaction(transId, gcash, amountStr.toDoubleOrNull() ?: 0.0)
                }
            }
        }
    }

    private fun sendCashNotification() {
        val currentUserId = FirebaseManager.auth.currentUser?.uid ?: return
        val landlordId = currentProperty?.landlordId ?: return
        val ids = listOf(currentUserId, landlordId).sorted()
        val chatId = "${ids[0]}_${ids[1]}_${activeContract?.contractId}"
        val messageId = FirebaseManager.messagesRef.child(chatId).push().key ?: return
        val msg = Message(messageId, chatId, currentUserId, landlordId, "I will pay with cash for Unit ${activeContract?.roomId}.", System.currentTimeMillis())
        FirebaseManager.messagesRef.child(chatId).child(messageId).setValue(msg).addOnSuccessListener {
            Toast.makeText(this, "Landlord notified!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun submitOnlineTransaction(transId: String, gcash: String, amount: Double) {
        val uid = FirebaseManager.auth.currentUser?.uid ?: return
        binding.btnPay.isEnabled = false
        
        processImageToBase64(selectedProofUri!!, 600) { base64 ->
            val transactionId = FirebaseManager.transactionsRef.push().key ?: return@processImageToBase64
            val transaction = PaymentTransaction(
                transactionId = transactionId,
                billingId = activeBilling?.billingId,
                tenantId = uid,
                amountPaid = amount,
                paymentMethod = "Online (GCash)",
                referenceNumber = transId,
                proofOfPaymentUrl = base64,
                verificationStatus = "Pending"
            )

            FirebaseManager.transactionsRef.child(transactionId).setValue(transaction)
                .addOnSuccessListener {
                    Toast.makeText(this, "Transaction submitted!", Toast.LENGTH_LONG).show()
                    finish()
                }
                .addOnFailureListener {
                    binding.btnPay.isEnabled = true
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun processImageToBase64(uri: Uri, maxSide: Int, callback: (String?) -> Unit) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (original == null) return callback(null)
            var w = original.width; var h = original.height
            if (w > maxSide || h > maxSide) {
                val r = w.toFloat() / h.toFloat()
                if (w > h) { w = maxSide; h = (maxSide / r).toInt() } else { h = maxSide; w = (maxSide * r).toInt() }
            }
            val scaled = Bitmap.createScaledBitmap(original, w, h, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            callback("data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
        } catch (e: Exception) { callback(null) }
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
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(Color.WHITE); v.setPadding(40, 30, 40, 30); v.textSize = 14f
                    return v
                }
            }
            popup.setAdapter(adapter)
            popup.width = 600
            popup.setBackgroundDrawable(ColorDrawable(Color.parseColor("#22223B")))
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> { FirebaseManager.auth.signOut(); startActivity(Intent(this, LoginActivity::class.java)); finishAffinity() }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener { startActivity(Intent(this, DashboardTenantActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }) }
        binding.bottomNav.navNotifications.setOnClickListener { startActivity(Intent(this, InboxTenantActivity::class.java)) }
        binding.bottomNav.navPayments.setOnClickListener { /* here */ }
        binding.bottomNav.navProfile.setOnClickListener { startActivity(Intent(this, ProfileTenantActivity::class.java)) }
    }
}