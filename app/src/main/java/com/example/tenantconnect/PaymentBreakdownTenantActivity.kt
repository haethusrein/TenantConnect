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
import com.example.tenantconnect.databinding.ActivityPaymentBreakdownTenantBinding

class PaymentBreakdownTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentBreakdownTenantBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBreakdownTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        
        binding.btnPayFinal.setOnClickListener {
            startActivity(Intent(this, PaymentDetailsTenantActivity::class.java))
        }

        setupMenu()
        setupBottomNavigation()
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
