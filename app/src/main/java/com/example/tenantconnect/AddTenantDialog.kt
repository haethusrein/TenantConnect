package com.example.tenantconnect

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogAddTenantBinding

import androidx.core.view.isVisible

class AddTenantDialog : BottomSheetDialogFragment() {
    private var _binding: DialogAddTenantBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddTenantBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddTenant.setOnClickListener {
            val query = binding.etTenantSearch.text.toString().trim().lowercase()
            if (query.isNotEmpty()) {
                searchAndAddTenant(query)
            } else {
                binding.etTenantSearch.error = "Please enter an email address"
                binding.etTenantSearch.requestFocus()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
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
                        val sanitizedName = "${tenant.firstName} ${tenant.lastName?.take(1)}."
                        binding.tvSearchResult.text = "Found: $sanitizedName"
                        binding.tvSearchResult.setTextColor(binding.root.context.getColor(R.color.navy))
                        binding.tvSearchResult.isVisible = true

                        if (tenant.landlordId == null) {
                            binding.btnAddTenant.setOnClickListener {
                                sendInvitation(tenantSnapshot.key!!, tenant)
                            }
                            binding.btnAddTenant.text = "Send Invitation"
                        } else {
                            showError("Tenant Registered", "This tenant is already registered to another landlord.")
                            binding.btnAddTenant.isEnabled = false
                        }
                    } else {
                        showError("Invalid User", "The account found is not a registered tenant.")
                    }
                } else {
                    showError("Not Found", "No tenant account found with the email: $query")
                }
            }
            .addOnFailureListener {
                showLoading(false)
                showError("Search Error", "Firebase index not defined or connection issue. Please check your database rules.")
            }
    }

    private fun showError(title: String, message: String) {
        CustomAlertDialog.newInstance(title, message)
            .show(parentFragmentManager, "CustomAlert")
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
                    propertyId = property.propertyId,
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
