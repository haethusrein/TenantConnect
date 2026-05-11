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
import com.example.tenantconnect.databinding.ActivityInboxLandlordBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class InboxLandlordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInboxLandlordBinding
    private lateinit var adapter: TenantChatAdapter
    private val tenantList = mutableListOf<User>()
    private var tenantsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInboxLandlordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        setupRecyclerView()
        loadTenants()
        setupMenu()
        setupBottomNavigation()
    }

    private fun setupRecyclerView() {
        adapter = TenantChatAdapter(tenantList) { tenant ->
            val intent = Intent(this, InboxTenantActivity::class.java)
            intent.putExtra("TARGET_USER_ID", tenant.userId)
            startActivity(intent)
        }
        binding.rvChats.layoutManager = LinearLayoutManager(this)
        binding.rvChats.adapter = adapter
    }

    private fun loadTenants() {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        
        tenantsListener = FirebaseManager.usersRef.orderByChild("landlordId").equalTo(currentLandlordId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    tenantList.clear()
                    snapshot.children.forEach { child ->
                        child.getValue(User::class.java)?.let { tenantList.add(it) }
                    }
                    adapter.updateTenants(tenantList)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@InboxLandlordActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        tenantsListener?.let {
            FirebaseManager.usersRef.removeEventListener(it)
        }
    }

    private fun logout() {
        // 1. Remove all active listeners to prevent "Permission Denied" errors
        tenantsListener?.let {
            FirebaseManager.usersRef.removeEventListener(it)
        }
        tenantsListener = null

        // 2. Perform sign out
        FirebaseManager.auth.signOut()

        // 3. Redirect to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
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
                    "Announcements" -> {
                        Toast.makeText(this, "Announcements coming soon", Toast.LENGTH_SHORT).show()
                    }
                    "Settings" -> {
                        Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
                    }
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
            val intent = Intent(this, DashboardLandlordActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        
        binding.bottomNav.navNotifications.setOnClickListener {
            // Already here
        }
        
        binding.bottomNav.navPayments.setOnClickListener {
            startActivity(Intent(this, PaymentLandlordActivity::class.java))
        }
        
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
