package com.example.tenantconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivitySettingsTenantBinding

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt

class SettingsTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsTenantBinding
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        
        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            FirebaseManager.usersRef.child(userId).get().addOnSuccessListener {
                currentUser = it.getValue(User::class.java)
                setupMenu()
                setupBottomNavigation()
            }
        }
    }

    private fun logout() {
        // 1. Perform sign out
        FirebaseManager.auth.signOut()

        // 2. Redirect to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
            val isLandlord = currentUser?.role == "Landlord"
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
            popup.setBackgroundDrawable(ColorDrawable("#22223B".toColorInt()))
            
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> {
                        if (isLandlord) {
                            Toast.makeText(this, "Announcements coming soon", Toast.LENGTH_SHORT).show()
                        } else {
                            startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                        }
                    }
                    "Settings" -> { /* Already here */ }
                    "Log out" -> {
                        popup.dismiss()
                        logout()
                    }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener {
            val isLandlord = currentUser?.role == "Landlord"
            val intent = if (isLandlord) {
                Intent(this, DashboardLandlordActivity::class.java)
            } else {
                Intent(this, DashboardTenantActivity::class.java)
            }
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        binding.bottomNav.navNotifications.setOnClickListener {
            val isLandlord = currentUser?.role == "Landlord"
            val intent = if (isLandlord) {
                Intent(this, InboxLandlordActivity::class.java)
            } else {
                Intent(this, InboxTenantActivity::class.java)
            }
            startActivity(intent)
        }

        binding.bottomNav.navPayments.setOnClickListener {
            val isLandlord = currentUser?.role == "Landlord"
            if (isLandlord) {
                Toast.makeText(this, "Payments coming soon", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, PaymentHistoryTenantActivity::class.java))
            }
        }

        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
