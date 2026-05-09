package com.example.tenantconnect

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.tenantconnect.databinding.DialogAddTenantBinding

class AddTenantDialog : DialogFragment() {
    private var _binding: DialogAddTenantBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddTenantBinding.inflate(LayoutInflater.from(context))

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)

        binding.btnAddTenant.setOnClickListener {
            val query = binding.etTenantSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchAndAddTenant(query)
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        return builder.create()
    }

    private fun searchAndAddTenant(query: String) {
        // Search by email
        FirebaseManager.usersRef.orderByChild("email").equalTo(query).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val tenantSnapshot = snapshot.children.first()
                    val tenant = tenantSnapshot.getValue(User::class.java)
                    
                    if (tenant != null && tenant.role == "Tenant") {
                        if (tenant.landlordId == null) {
                            sendInvitation(tenantSnapshot.key!!, tenant)
                        } else {
                            Toast.makeText(context, "Tenant is already registered to another landlord.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "User found is not a tenant.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Tenant not found.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun sendInvitation(tenantUid: String, tenant: User) {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        
        // Fetch landlord and property info to include in the invitation
        FirebaseManager.usersRef.child(currentLandlordId).get().addOnSuccessListener { userSnapshot ->
            val landlord = userSnapshot.getValue(User::class.java)
            
            FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(currentLandlordId).get().addOnSuccessListener { propSnapshot ->
                val property = propSnapshot.children.firstOrNull()?.getValue(Property::class.java)
                
                val invitationId = FirebaseManager.invitationsRef.push().key ?: return@addOnSuccessListener
                val invitation = Invitation(
                    invitationId = invitationId,
                    tenantId = tenantUid,
                    landlordId = currentLandlordId,
                    landlordName = "${landlord?.firstName} ${landlord?.lastName}",
                    propertyName = property?.propertyName ?: "Unknown Property"
                )

                FirebaseManager.invitationsRef.child(invitationId).setValue(invitation)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Invitation sent to ${tenant.firstName}!", Toast.LENGTH_LONG).show()
                        dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Failed to send invitation: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun assignTenantToLandlord(tenantUid: String, tenant: User) {
        // This method is now replaced by the invitation flow
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
