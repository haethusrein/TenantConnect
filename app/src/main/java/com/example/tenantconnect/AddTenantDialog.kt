package com.example.tenantconnect

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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

        setupInitialState()

        binding.etTenantSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                setupInitialState() // Reset if they start typing again
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun setupInitialState() {
        binding.btnAddTenant.text = "Search Account"
        binding.btnAddTenant.setOnClickListener {
            val query = binding.etTenantSearch.text.toString().trim().lowercase()
            if (query.isNotEmpty()) {
                searchAndAddTenant(query)
            } else {
                binding.etTenantSearch.error = "Please enter an email address"
                binding.etTenantSearch.requestFocus()
            }
        }
        binding.tvSearchResult.isVisible = false
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

                        val currentLandlordId = FirebaseManager.auth.currentUser?.uid

                        if (tenant.landlordId == currentLandlordId && tenant.status == "Active") {
                            showError("Already Linked", "This user is already your active tenant.")
                            binding.btnAddTenant.isEnabled = false
                        } else if (tenant.landlordId != null) {
                            showError("Already Linked", "This tenant is already linked to another landlord.")
                            binding.btnAddTenant.isEnabled = false
                        } else {
                            binding.btnAddTenant.text = "Send Invitation"
                            binding.btnAddTenant.setOnClickListener {
                                checkPendingAndSend(tenantSnapshot.key!!, tenant)
                            }
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

    private fun checkPendingAndSend(tenantUid: String, tenant: User) {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        showLoading(true)

        // Logic Requirement: Query invitationsRef for the target tenantId
        FirebaseManager.invitationsRef.orderByChild("tenantId").equalTo(tenantUid).get()
            .addOnSuccessListener { snapshot ->
                // Check if an invitation exists and its status is "Pending" from THIS landlord
                val existingPending = snapshot.children.any { 
                    it.child("landlordId").getValue(String::class.java) == currentLandlordId &&
                    it.child("status").getValue(String::class.java) == "Pending"
                }

                if (existingPending) {
                    showLoading(false)
                    Toast.makeText(context, "An invitation is already pending for this tenant", Toast.LENGTH_LONG).show()
                } else {
                    sendInvitation(tenantUid, tenant)
                }
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(context, "Error checking invitations: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showError(title: String, message: String) {
        CustomAlertDialog.newInstance(title, message)
            .show(parentFragmentManager, "CustomAlert")
    }

    private fun sendInvitation(tenantUid: String, tenant: User) {
        val currentLandlordId = FirebaseManager.auth.currentUser?.uid ?: return
        showLoading(true)
        
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
