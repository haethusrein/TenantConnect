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
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivityPaymentTenantBinding

class PaymentTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentTenantBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        
        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            checkPaymentStatus(userId)
        }

        setupMenu()
        setupButtons()
        setupBottomNavigation()
    }

    private fun checkPaymentStatus(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                showNoAccommodationState()
            }
        }
    }

    private fun showNoAccommodationState() {
        val msg = "no accommodation. Please tell your landlord to register you as their tenant."
        
        // Dues Box
        binding.tvPaymentLabel.visibility = View.GONE
        binding.tvPaymentDueDate.visibility = View.GONE
        binding.tvPaymentAmount.text = msg
        binding.tvPaymentAmount.textSize = 14f
        binding.tvPaymentAmount.setPadding(0, 20, 0, 0)
        
        // History Box
        binding.llHistoryPreview.visibility = View.GONE
        val noHistoryTv = TextView(this).apply {
            text = "No history available."
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        (binding.llHistoryPreview.parent as ViewGroup).addView(noHistoryTv, 1)
    }
    
    private fun setupButtons() {
        binding.btnViewBreakdown.setOnClickListener {
            startActivity(Intent(this, PaymentBreakdownTenantActivity::class.java))
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
                    "Payments" -> { /* Already here */ }
                    "Accommodation" -> startActivity(Intent(this, ViewContractActivity::class.java))
                    "Contact Landlord" -> startActivity(Intent(this, InboxTenantActivity::class.java))
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
            // Already here
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
