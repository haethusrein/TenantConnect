package com.example.tenantconnect

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.tenantconnect.databinding.DialogAddTenantBinding

import androidx.core.view.isVisible

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
            } else {
                Toast.makeText(context, "Please enter an email address", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        return builder.create()
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.btnAddTenant.isEnabled = !isLoading
    }

    private fun searchAndAddTenant(query: String) {
        showLoading(true)
        binding.tvSearchResult.isVisible = false
        // Search by email
        FirebaseManager.usersRef.orderByChild("email").equalTo(query).get()
            .addOnSuccessListener { snapshot ->
                showLoading(false)
                if (snapshot.exists()) {
                    val tenantSnapshot = snapshot.children.first()
                    val tenant = tenantSnapshot.getValue(User::class.java)
                    
                    if (tenant != null && tenant.role == "Tenant") {
                        binding.tvSearchResult.text = "Found: ${tenant.firstName} ${tenant.lastName}"
                        binding.tvSearchResult.setTextColor(binding.root.context.getColor(R.color.navy))
                        binding.tvSearchResult.isVisible = true

                        if (tenant.landlordId == null) {
                            binding.btnAddTenant.setOnClickListener {
                                sendInvitation(tenantSnapshot.key!!, tenant)
                            }
                            binding.btnAddTenant.text = "Send Invitation"
                        } else {
                            binding.tvSearchResult.text = "Tenant already registered elsewhere."
                            binding.tvSearchResult.setTextColor(binding.root.context.getColor(android.R.color.holo_red_dark))
                            binding.btnAddTenant.isEnabled = false
                        }
                    } else {
                        binding.tvSearchResult.text = "User is not a tenant."
                        binding.tvSearchResult.isVisible = true
                    }
                } else {
                    binding.tvSearchResult.text = "No user found with that email."
                    binding.tvSearchResult.isVisible = true
                }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(context, "Search failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendInvitation(tenantUid: String, tenant: User) {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        showLoading(true)
        binding.btnAddTenant.isEnabled = false
        
        // Fetch landlord and property info to include in the invitation
        FirebaseManager.usersRef.child(currentLandlordId).get().addOnSuccessListener { userSnapshot ->
            val landlord = userSnapshot.getValue(User::class.java)
            
            FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(currentLandlordId).get().addOnSuccessListener { propSnapshot ->
                val property = propSnapshot.children.firstOrNull()?.getValue(Property::class.java)
                
                if (property == null) {
                    showLoading(false)
                    Toast.makeText(context, "Please complete your property setup first.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val invitationId = FirebaseManager.invitationsRef.push().key ?: return@addOnSuccessListener
                val invitation = Invitation(
                    invitationId = invitationId,
                    tenantId = tenantUid,
                    landlordId = currentLandlordId,
                    landlordName = "${landlord?.firstName} ${landlord?.lastName}",
                    propertyName = property.propertyName ?: "Your Property"
                )

                FirebaseManager.invitationsRef.child(invitationId).setValue(invitation)
                    .addOnSuccessListener {
                        showLoading(false)
                        binding.tvSearchResult.text = "Invitation sent successfully!"
                        binding.tvSearchResult.setTextColor(binding.root.context.getColor(android.R.color.holo_green_dark))
                        Toast.makeText(context, "Invitation sent to ${tenant.firstName}!", Toast.LENGTH_LONG).show()
                        
                        // Briefly show success before dismissing
                        binding.root.postDelayed({ dismiss() }, 1500)
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        binding.btnAddTenant.isEnabled = true
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
