package com.example.tenantconnect

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogRoomSetupBinding

class RoomSetupDialog(private val invitation: Invitation) : BottomSheetDialogFragment() {
    private var _binding: DialogRoomSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRoomSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnConfirm.setOnClickListener {
            val roomNumber = binding.etRoomNumber.text.toString().trim()
            val rentAmountStr = binding.etMonthlyRent.text.toString().trim()

            if (roomNumber.isEmpty()) {
                binding.etRoomNumber.error = "Please enter a room unit"
                binding.etRoomNumber.requestFocus()
                return@setOnClickListener
            }
            
            val rentAmount = rentAmountStr.toDoubleOrNull()
            if (rentAmount == null || rentAmount <= 0.0) {
                binding.etMonthlyRent.error = "Please enter a valid amount greater than 0"
                binding.etMonthlyRent.requestFocus()
                return@setOnClickListener
            }

            finalizeSetup(roomNumber, rentAmount)
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun finalizeSetup(roomNumber: String, rentAmount: Double) {
        val tenantId = invitation.tenantId ?: return
        val landlordId = invitation.landlordId ?: return
        val invitationId = invitation.invitationId ?: return

        // 1. Update user record
        val userUpdates = hashMapOf<String, Any?>(
            "landlordId" to landlordId,
            "status" to "Active"
        )

        FirebaseManager.usersRef.child(tenantId).updateChildren(userUpdates)
            .addOnSuccessListener {
                // 2. Create Contract
                val contractId = FirebaseManager.contractsRef.push().key ?: return@addOnSuccessListener
                val contract = Contract(
                    contractId = contractId,
                    tenantId = tenantId,
                    landlordId = landlordId,
                    propertyId = invitation.propertyId,
                    roomId = roomNumber,
                    baseRentAmount = rentAmount,
                    status = "Active"
                )

                FirebaseManager.contractsRef.child(contractId).setValue(contract)
                    .addOnSuccessListener {
                        // 3. Mark invitation as accepted
                        FirebaseManager.invitationsRef.child(invitationId).child("status").setValue("Accepted")
                        
                        Toast.makeText(context, "Welcome to ${invitation.propertyName}!", Toast.LENGTH_LONG).show()
                        dismiss()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
