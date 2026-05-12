package com.example.tenantconnect

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.DialogAddTenantBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddTenantDialog : BottomSheetDialogFragment() {
    private var _binding: DialogAddTenantBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun setupInitialState() {
        binding.btnAddTenant.text = "Search Account" // Set initial text
        binding.llRoomSetup.isVisible = false
        binding.tvSearchResult.isVisible = false

        // This click listener handles the Search phase
        binding.btnAddTenant.setOnClickListener {
            val query = binding.etTenantSearch.text.toString().trim().lowercase()
            if (query.isNotEmpty()) {
                searchAndAddTenant(query)
            } else {
                binding.etTenantSearch.error = "Please enter a name or email"
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.btnAddTenant.isEnabled = !isLoading
    }

    private fun searchAndAddTenant(query: String) {
        showLoading(true)
        binding.tvSearchResult.isVisible = false
        binding.llRoomSetup.isVisible = false

        // Fetch tenants and perform a smart partial-match search locally
        FirebaseManager.usersRef.orderByChild("role").equalTo("Tenant").get()
            .addOnSuccessListener { snapshot ->
                showLoading(false)

                // Find the first tenant whose name or email contains the query
                val matchedTenantSnap = snapshot.children.firstOrNull { child ->
                    val t = child.getValue(User::class.java)
                    t != null && (
                            t.firstName?.lowercase()?.contains(query) == true ||
                                    t.lastName?.lowercase()?.contains(query) == true ||
                                    t.email?.lowercase()?.contains(query) == true
                            )
                }

                if (matchedTenantSnap != null) {
                    val tenant = matchedTenantSnap.getValue(User::class.java)!!
                    val tenantUid = matchedTenantSnap.key!!

                    val sanitizedName = "${tenant.firstName} ${tenant.lastName}"
                    binding.tvSearchResult.text = "Found: $sanitizedName\nEmail: ${tenant.email}"
                    binding.tvSearchResult.setTextColor(binding.root.context.getColor(R.color.navy))
                    binding.tvSearchResult.isVisible = true

                    if (tenant.landlordId == null) {
                        // They are available! Show the room setup fields
                        binding.llRoomSetup.isVisible = true
                        binding.btnAddTenant.text = "Send Invitation"

                        binding.btnAddTenant.setOnClickListener {
                            val roomNum = binding.etRoomNumber.text.toString().trim()
                            val rentStr = binding.etBaseRent.text.toString().trim()

                            if (roomNum.isEmpty() || rentStr.isEmpty()) {
                                Toast.makeText(context, "Please assign a unit and base rent.", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }

                            sendInvitation(tenantUid, tenant, roomNum, rentStr.toDouble())
                        }
                    } else {
                        showError("Already Linked", "This tenant is already linked to an active landlord.")
                        binding.btnAddTenant.isEnabled = false
                    }
                } else {
                    showError("Not Found", "No tenant account found matching: '$query'")
                }
            }
            .addOnFailureListener {
                showLoading(false)
                showError("Search Error", "Network issue. Please try again.")
            }
    }

    private fun showError(title: String, message: String) {
        CustomAlertDialog.newInstance(title, message).show(parentFragmentManager, "CustomAlert")
    }

    private fun sendInvitation(tenantUid: String, tenant: User, roomNum: String, rentAmount: Double) {
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

                // Check for duplicate pending invites
                FirebaseManager.invitationsRef.orderByChild("tenantId").equalTo(tenantUid).get()
                    .addOnSuccessListener { invSnapshot ->
                        val hasPending = invSnapshot.children.any {
                            it.child("landlordId").getValue(String::class.java) == currentLandlordId &&
                                    it.child("status").getValue(String::class.java) == "Pending"
                        }

                        if (hasPending) {
                            showLoading(false)
                            binding.btnAddTenant.isEnabled = true
                            showError("Duplicate Invitation", "You already have a pending invitation sent to this tenant.")
                        } else {
                            // CREATE THE INVITATION WITH ROOM AND RENT INCLUDED
                            val invitationId = FirebaseManager.invitationsRef.push().key ?: return@addOnSuccessListener
                            val invitation = Invitation(
                                invitationId = invitationId,
                                tenantId = tenantUid,
                                landlordId = currentLandlordId,
                                propertyId = property.propertyId,
                                landlordName = "${landlord?.firstName} ${landlord?.lastName}",
                                propertyName = property.propertyName ?: "Your Property",
                                roomId = roomNum,            // Sent by Landlord
                                baseRentAmount = rentAmount  // Sent by Landlord
                            )

                            FirebaseManager.invitationsRef.child(invitationId).setValue(invitation)
                                .addOnSuccessListener {
                                    showLoading(false)
                                    binding.tvSearchResult.text = "Invitation sent successfully!"
                                    binding.tvSearchResult.setTextColor(binding.root.context.getColor(android.R.color.holo_green_dark))
                                    Toast.makeText(context, "Invitation sent to ${tenant.firstName}!", Toast.LENGTH_LONG).show()
                                    binding.root.postDelayed({ dismiss() }, 1500)
                                }
                        }
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}