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
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenantconnect.databinding.ActivityPaymentLandlordBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class PaymentLandlordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentLandlordBinding
    private lateinit var adapter: TenantBillingAdapter
    private val billingItems = mutableListOf<TenantBillingAdapter.TenantBillingItem>()
    private var billingListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentLandlordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        setupRecyclerView()
        loadBillingData()
        setupMenu()
        setupBottomNavigation()
    }

    private fun setupRecyclerView() {
        adapter = TenantBillingAdapter { tenant, contract, billing ->
            if (billing == null) {
                val dialog = InvoiceDialog(tenant, contract) { loadBillingData() }
                dialog.show(supportFragmentManager, "InvoiceDialog")
            } else if (billing.status != "Paid") {
                // If it's unpaid or overdue, landlord can mark as paid
                // BUT first show proof if available
                checkProofAndConfirm(billing)
            }
        }
        binding.rvTenantBillings.layoutManager = LinearLayoutManager(this)
        binding.rvTenantBillings.adapter = adapter
    }

    private fun checkProofAndConfirm(billing: Billing) {
        val bid = billing.billingId ?: return
        
        Toast.makeText(this, "Checking for tenant proof...", Toast.LENGTH_SHORT).show()

        // Query transactions for this billing
        FirebaseManager.transactionsRef.orderByChild("billingId").equalTo(bid).get()
            .addOnSuccessListener { snapshot ->
                val transaction = snapshot.children.mapNotNull { it.getValue(PaymentTransaction::class.java) }.firstOrNull()
                
                if (transaction != null) {
                    // Show Proof Dialog
                    val dialog = ProofOfPaymentDialog(transaction.proofOfPaymentUrl, transaction.referenceNumber)
                    dialog.show(supportFragmentManager, "ProofOfPaymentDialog")
                    
                    // Simple confirm after showing
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Confirm Payment")
                        .setMessage("Tenant has submitted a receipt. Did you receive ₱${String.format(Locale.US, "%.2f", billing.totalAmount)}?")
                        .setPositiveButton("Yes, Mark Paid") { _, _ -> markAsPaid(billing) }
                        .setNegativeButton("Not Yet", null)
                        .show()
                } else {
                    // No transaction found, just ask to mark as paid (Cash use case)
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Mark as Paid")
                        .setMessage("No online proof found. Confirm receiving payment of ₱${String.format(Locale.US, "%.2f", billing.totalAmount)} for this tenant?")
                        .setPositiveButton("Confirm") { _, _ -> markAsPaid(billing) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadBillingData() {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        
        FirebaseManager.usersRef.orderByChild("landlordId").equalTo(currentLandlordId).get()
            .addOnSuccessListener { tenantSnapshot ->
                val tenants = tenantSnapshot.children.mapNotNull { it.getValue(User::class.java) }
                
                FirebaseManager.contractsRef.orderByChild("landlordId").equalTo(currentLandlordId).get()
                    .addOnSuccessListener { contractSnapshot ->
                        val contracts = contractSnapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                            .filter { it.status == "Active" }

                        FirebaseManager.billingsRef.get().addOnSuccessListener { billingSnapshot ->
                            billingItems.clear()
                            var totalCollected = 0.0
                            var totalExpected = 0.0
                            var paidCount = 0
                            var overdueCount = 0

                            for (contract in contracts) {
                                val tenant = tenants.find { it.userId == contract.tenantId } ?: continue
                                var latestBilling = billingSnapshot.children
                                    .mapNotNull { it.getValue(Billing::class.java) }
                                    .filter { it.contractId == contract.contractId }
                                    .maxByOrNull { it.dueDate ?: "" }

                                if (latestBilling?.status == "Paid") {
                                    val calendar = java.util.Calendar.getInstance()
                                    val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    val dueDay = contract.paymentDueDay ?: 1
                                    val daysUntilDue = if (dueDay >= currentDay) dueDay - currentDay else (30 - currentDay + dueDay)
                                    if (daysUntilDue <= 7) latestBilling = null 
                                }

                                billingItems.add(TenantBillingAdapter.TenantBillingItem(tenant, contract, latestBilling))
                                
                                if (latestBilling != null) {
                                    totalExpected += latestBilling.totalAmount ?: 0.0
                                    if (latestBilling.status == "Paid") {
                                        totalCollected += latestBilling.totalAmount ?: 0.0
                                        paidCount++
                                    } else if (latestBilling.status == "Overdue") {
                                        overdueCount++
                                    }
                                }
                            }

                            binding.tvTotalCollected.text = String.format(Locale.US, "₱%.2f", totalCollected)
                            binding.tvTotalExpected.text = String.format(Locale.US, "₱%.2f", totalExpected)
                            binding.tvPaidCount.text = paidCount.toString()
                            binding.tvOverdueCount.text = overdueCount.toString()
                            adapter.submitList(billingItems)
                        }
                    }
            }
    }

    private fun markAsPaid(billing: Billing) {
        val id = billing.billingId ?: return
        val updates = hashMapOf<String, Any?>(
            "status" to "Paid",
            "datePaid" to System.currentTimeMillis()
        )
        
        // 1. Update the Billing record
        FirebaseManager.billingsRef.child(id).updateChildren(updates).addOnSuccessListener {
            
            // 2. ALSO update the associated Transaction record if it exists
            FirebaseManager.transactionsRef.orderByChild("billingId").equalTo(id).get()
                .addOnSuccessListener { snapshot ->
                    val transId = snapshot.children.firstOrNull()?.key
                    if (transId != null) {
                        FirebaseManager.transactionsRef.child(transId).child("verificationStatus").setValue("Verified")
                    }
                    
                    Toast.makeText(this, "Payment and Transaction verified!", Toast.LENGTH_SHORT).show()
                    loadBillingData()
                }
        }
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
            val menuItems = arrayOf("Announcements", "Settings", "Log out")
            val popup = ListPopupWindow(this)
            popup.anchorView = view
            val menuAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuItems) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(Color.WHITE); v.setPadding(40, 30, 40, 30); v.textSize = 14f
                    return v
                }
            }
            popup.setAdapter(menuAdapter)
            popup.width = 600
            popup.setBackgroundDrawable(ColorDrawable("#22223B".toColorInt()))
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> startActivity(Intent(this, AnnouncementsLandlordActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsLandlordActivity::class.java))
                    "Log out" -> { FirebaseManager.auth.signOut(); startActivity(Intent(this, LoginActivity::class.java)); finishAffinity() }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener { startActivity(Intent(this, DashboardLandlordActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }) }
        binding.bottomNav.navNotifications.setOnClickListener { startActivity(Intent(this, InboxLandlordActivity::class.java)) }
        binding.bottomNav.navPayments.setOnClickListener { /* here */ }
        binding.bottomNav.navProfile.setOnClickListener { startActivity(Intent(this, ProfileTenantActivity::class.java)) }
    }
}