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
import com.example.tenantconnect.databinding.ActivityPaymentDetailsTenantBinding

class PaymentDetailsTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentDetailsTenantBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentDetailsTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        
        binding.btnPay.setOnClickListener { 
            handlePayment()
        }

        setupMenu()
        setupBottomNavigation()
    }

    private fun handlePayment() {
        val amountPaidStr = binding.etAmountToPay.text.toString().trim()
        val totalBalanceStr = binding.tvTotalBalance.text.toString().replace("₱", "").trim()
        
        val amountPaid = amountPaidStr.toDoubleOrNull()
        val totalBalance = totalBalanceStr.toDoubleOrNull() ?: 0.0

        // 1. Validation
        if (amountPaid == null || amountPaid <= 0.0) {
            binding.etAmountToPay.error = "Please enter a valid amount greater than 0"
            binding.etAmountToPay.requestFocus()
            return
        }

        if (amountPaid > totalBalance) {
            binding.etAmountToPay.error = "Amount cannot exceed total balance (₱$totalBalance)"
            binding.etAmountToPay.requestFocus()
            return
        }

        // 2. Prevent Double Clicks
        binding.btnPay.isEnabled = false
        // showLoading(true) // If you have a loading overlay

        // 3. Logic Placeholder (Transaction implementation)
        // For now, simulate success
        Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
        finish()

        /* 
        // Example logic for real Firebase implementation:
        FirebaseManager.transactionsRef.child(newId).setValue(transaction)
            .addOnSuccessListener {
                Toast.makeText(this, "Payment Successful!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                binding.btnPay.isEnabled = true
                Toast.makeText(this, "Payment Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        */
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
                    "Contact Landlord" -> startActivity(Intent(this, InboxTenantActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> {
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
            startActivity(Intent(this, PaymentTenantActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
