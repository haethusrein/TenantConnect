package com.example.tenantconnect

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    }

    private fun setupRecyclerView() {
        adapter = TenantAdapter(tenantList) { tenant ->
            removeTenant(tenant)
        }
        binding.rvTenants.layoutManager = LinearLayoutManager(this)
        binding.rvTenants.adapter = adapter
    }

    private fun loadTenants() {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        
        tenantsListener = FirebaseManager.usersRef.orderByChild("landlordId").equalTo(currentLandlordId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    tenantList.clear()
                    for (child in snapshot.children) {
                        val tenant = child.getValue(User::class.java)
                        if (tenant != null) {
                            tenantList.add(tenant)
                        }
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
        
        // Multi-path update to remove tenant and clean up room/contract
        val updates = hashMapOf<String, Any?>()
        
        // 1. Update User node
        updates["users/$tenantId/landlordId"] = null
        updates["users/$tenantId/status"] = "Inactive"

        // 2. Find and update Room node
        FirebaseManager.roomsRef.orderByChild("tenantId").equalTo(tenantId).get().addOnSuccessListener { roomSnapshot ->
            for (child in roomSnapshot.children) {
                val roomId = child.key
                if (roomId != null) {
                    updates["rooms/$roomId/tenantId"] = null
                    updates["rooms/$roomId/status"] = "Vacant"
                }
            }
            
            // 3. Find and update/terminate Contract node
            FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(tenantId).get().addOnSuccessListener { contractSnapshot ->
                for (child in contractSnapshot.children) {
                    val contractId = child.key
                    if (contractId != null) {
                        updates["contracts/$contractId/status"] = "Terminated"
                    }
                }
                
                // Perform the batch update
                FirebaseManager.database.reference.updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Tenant removed and data cleaned up", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to remove tenant: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch associated room data", Toast.LENGTH_SHORT).show()
        }
    }
}
