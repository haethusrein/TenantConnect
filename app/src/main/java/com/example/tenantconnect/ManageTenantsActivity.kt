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
import com.example.tenantconnect.databinding.ActivityManageTenantsBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class ManageTenantsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityManageTenantsBinding
    private lateinit var adapter: TenantAdapter
    private val tenantList = mutableListOf<User>()
    private var tenantsListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageTenantsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        setupRecyclerView()
        loadTenants()

        binding.btnManageTenantsAction.setOnClickListener {
            val dialog = AddTenantDialog()
            dialog.show(supportFragmentManager, "AddTenantDialog")
        }
        
        setupMenu()
        setupBottomNavigation()
    }

    private fun setupRecyclerView() {
        adapter = TenantAdapter(tenantList, 
            onEditClick = { tenant ->
                showEditDialog(tenant)
            },
            onDeleteClick = { tenant ->
                removeTenant(tenant)
            }
        )
        binding.rvTenants.layoutManager = LinearLayoutManager(this)
        binding.rvTenants.adapter = adapter
    }

    private fun showEditDialog(tenant: User) {
        val tenantId = tenant.userId ?: return
        
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(tenantId).get()
            .addOnSuccessListener { snapshot ->
                val activeContract = snapshot.children.firstOrNull { 
                    it.child("status").getValue(String::class.java) == "Active" 
                }?.getValue(Contract::class.java)

                if (activeContract != null) {
                    val dialog = EditTenantDialog(tenant, activeContract)
                    dialog.show(supportFragmentManager, "EditTenantDialog")
                } else {
                    Toast.makeText(this, "No active contract found for this tenant.", Toast.LENGTH_SHORT).show()
                }
            }
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
                    Toast.makeText(this@ManageTenantsActivity, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        tenantsListener?.let {
            FirebaseManager.usersRef.removeEventListener(it)
        }
    }

    private fun removeTenant(tenant: User) {
        val tenantId = tenant.userId ?: return
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Remove Tenant")
            .setMessage("Are you sure you want to remove ${tenant.firstName} ${tenant.lastName}? This will vacate their room and terminate the contract.")
            .setPositiveButton("Remove") { _, _ ->
                performRemoval(tenantId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRemoval(tenantId: String) {
        val updates = hashMapOf<String, Any?>()
        
        updates["users/$tenantId/landlordId"] = null
        updates["users/$tenantId/status"] = "Inactive"

        FirebaseManager.roomsRef.orderByChild("tenantId").equalTo(tenantId).get().addOnSuccessListener { roomSnapshot ->
            for (child in roomSnapshot.children) {
                val roomId = child.key
                if (roomId != null) {
                    updates["rooms/$roomId/tenantId"] = null
                    updates["rooms/$roomId/status"] = "Vacant"
                }
            }
            
            FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(tenantId).get().addOnSuccessListener { contractSnapshot ->
                for (child in contractSnapshot.children) {
                    val contractId = child.key
                    if (contractId != null) {
                        updates["contracts/$contractId/status"] = "Terminated"
                    }
                }
                
                FirebaseManager.database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Tenant removed successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to remove tenant: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch associated room data", Toast.LENGTH_SHORT).show()
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
                    "Announcements" -> Toast.makeText(this, "Announcements coming soon", Toast.LENGTH_SHORT).show()
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
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
            startActivity(Intent(this, InboxLandlordActivity::class.java))
        }
        
        binding.bottomNav.navPayments.setOnClickListener {
            Toast.makeText(this, "Payments management coming soon", Toast.LENGTH_SHORT).show()
        }
        
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
