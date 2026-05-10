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
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.ActivityPaymentTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class PaymentTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentTenantBinding
    private var billingListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        
        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            fetchPaymentData(userId)
        }

        setupMenu()
        setupButtons()
        setupBottomNavigation()
    }

    private fun fetchPaymentData(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                val activeContract = snapshot.children.firstOrNull { 
                    it.child("status").getValue(String::class.java) == "Active" 
                }?.getValue(Contract::class.java)

                if (activeContract != null) {
                    listenForLatestBilling(activeContract.contractId)
                    fetchPaymentHistory(activeContract.contractId)
                } else {
                    showNoAccommodationState()
                }
            }
    }

    private fun listenForLatestBilling(contractId: String?) {
        if (contractId == null) return
        billingListener = FirebaseManager.billingsRef.orderByChild("contractId").equalTo(contractId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    val latestBilling = snapshot.children.mapNotNull { it.getValue(Billing::class.java) }
                        .filter { it.status != "Paid" }
                        .maxByOrNull { it.dueDate ?: "" }

                    if (latestBilling != null) {
                        binding.tvPaymentLabel.text = "Current Billing"
                        binding.tvPaymentAmount.text = String.format(Locale.US, "₱%.2f", latestBilling.totalAmount ?: 0.0)
                        binding.tvPaymentDueDate.text = "Due: ${latestBilling.dueDate}"
                        
                        binding.btnPayNow.isEnabled = true
                        binding.btnViewBreakdown.isEnabled = true
                    } else {
                        binding.tvPaymentLabel.text = "No Dues"
                        binding.tvPaymentAmount.text = "₱0.00"
                        binding.tvPaymentDueDate.text = "You are all caught up!"
                        
                        binding.btnPayNow.isEnabled = false
                        binding.btnViewBreakdown.isEnabled = false
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchPaymentHistory(contractId: String?) {
        if (contractId == null) return
        FirebaseManager.billingsRef.orderByChild("contractId").equalTo(contractId).get()
            .addOnSuccessListener { snapshot ->
                binding.llHistoryPreview.removeAllViews()
                val history = snapshot.children.mapNotNull { it.getValue(Billing::class.java) }
                    .filter { it.status == "Paid" }
                    .sortedByDescending { it.datePaid }
                    .take(5)

                if (history.isEmpty()) {
                    val noHistoryTv = TextView(this).apply {
                        text = "No history available."
                        setTextColor(Color.WHITE)
                        textSize = 12f
                    }
                    binding.llHistoryPreview.addView(noHistoryTv)
                } else {
                    for (bill in history) {
                        val historyTv = TextView(this).apply {
                            val amount = String.format(Locale.US, "₱%.2f", bill.totalAmount ?: 0.0)
                            text = "${bill.dueDate} • $amount • Paid"
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            setPadding(0, 0, 0, 10)
                        }
                        binding.llHistoryPreview.addView(historyTv)
                    }
                }
            }
    }

    private fun showNoAccommodationState() {
        binding.mainContentLayout.visibility = View.GONE
        binding.layoutEmpty.root.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        billingListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
    }
    
    private fun setupButtons() {
        binding.btnViewBreakdown.setOnClickListener {
            startActivity(Intent(this, PaymentDetailsTenantActivity::class.java))
        }
        binding.btnPayNow.setOnClickListener {
            startActivity(Intent(this, PaymentDetailsTenantActivity::class.java))
        }
        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(this, PaymentHistoryTenantActivity::class.java))
        }
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
                        logout()
                    }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun logout() {
        billingListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        FirebaseManager.auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
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
            // Already here
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
