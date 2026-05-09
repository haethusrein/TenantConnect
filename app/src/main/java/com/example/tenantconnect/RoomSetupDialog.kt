package com.example.tenantconnect

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.tenantconnect.databinding.DialogRoomSetupBinding

class RoomSetupDialog(private val invitation: Invitation) : DialogFragment() {
    private var _binding: DialogRoomSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRoomSetupBinding.inflate(LayoutInflater.from(context))

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)

        binding.btnConfirm.setOnClickListener {
            val roomNumber = binding.etRoomNumber.text.toString().trim()
            val rentAmount = binding.etMonthlyRent.text.toString().trim().toDoubleOrNull() ?: 0.0

            if (roomNumber.isEmpty()) {
                Toast.makeText(context, "Please enter a room unit.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            finalizeSetup(roomNumber, rentAmount)
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        return builder.create()
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
