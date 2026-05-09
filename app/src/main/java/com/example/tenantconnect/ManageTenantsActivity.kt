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
        
        FirebaseManager.usersRef.orderByChild("landlordId").equalTo(currentLandlordId)
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

    private fun removeTenant(tenant: User) {
        val tenantId = tenant.userId ?: return
        
        val updates = hashMapOf<String, Any?>(
            "landlordId" to null,
            "status" to "Inactive"
        )

        FirebaseManager.usersRef.child(tenantId).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Tenant removed", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to remove tenant", Toast.LENGTH_SHORT).show()
            }
    }
}
