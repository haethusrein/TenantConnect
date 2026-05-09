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
                // Show Create Invoice Dialog
                val dialog = InvoiceDialog(tenant, contract) {
                    loadBillingData() // Refresh
                }
                dialog.show(supportFragmentManager, "InvoiceDialog")
            } else if (billing.status != "Paid") {
                // Mark as Paid
                markAsPaid(billing)
            }
        }
        binding.rvTenantBillings.layoutManager = LinearLayoutManager(this)
        binding.rvTenantBillings.adapter = adapter
    }

    private fun loadBillingData() {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        
        // This is a complex query. We need tenants -> their active contracts -> their latest billing.
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
                                
                                // Find latest billing for this contract
                                var latestBilling = billingSnapshot.children
                                    .mapNotNull { it.getValue(Billing::class.java) }
                                    .filter { it.contractId == contract.contractId }
                                    .maxByOrNull { it.dueDate ?: "" }

                                // Requirement: Revert to "Invoice" button a week before next cycle
                                if (latestBilling?.status == "Paid") {
                                    val calendar = java.util.Calendar.getInstance()
                                    val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    val dueDay = contract.paymentDueDay ?: 1
                                    
                                    // If we are within 7 days of the NEXT due date, hide the old "Paid" status
                                    // to allow issuing the next month's invoice.
                                    val daysUntilDue = if (dueDay >= currentDay) dueDay - currentDay else (30 - currentDay + dueDay)
                                    if (daysUntilDue <= 7) {
                                        latestBilling = null 
                                    }
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
        val updates = mapOf(
            "status" to "Paid",
            "datePaid" to System.currentTimeMillis()
        )
        
        FirebaseManager.billingsRef.child(id).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Payment confirmed!", Toast.LENGTH_SHORT).show()
                loadBillingData()
            }
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
            val menuItems = arrayOf("Announcements", "Settings", "Log out")
            val popup = ListPopupWindow(this)
            popup.anchorView = view
            val menuAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuItems) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(Color.WHITE)
                    view.setPadding(40, 30, 40, 30)
                    view.textSize = 14f
                    return view
                }
            }
            popup.setAdapter(menuAdapter)
            popup.width = 600
            popup.setBackgroundDrawable(ColorDrawable("#22223B".toColorInt()))
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> Toast.makeText(this, "Announcements coming soon", Toast.LENGTH_SHORT).show()
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> {
                        FirebaseManager.auth.signOut()
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
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
        binding.bottomNav.navPayments.setOnClickListener { /* Already here */ }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
