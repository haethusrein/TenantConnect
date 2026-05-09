package com.example.tenantconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogRoomSetupBinding

class EditTenantDialog(private val tenant: User, private val contract: Contract) : BottomSheetDialogFragment() {
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

        // Initialize with current values
        binding.etRoomNumber.setText(contract.roomId)
        binding.etMonthlyRent.setText(contract.baseRentAmount?.toString() ?: "0.00")

        binding.btnConfirm.setOnClickListener {
            val newRoom = binding.etRoomNumber.text.toString().trim()
            val rentStr = binding.etMonthlyRent.text.toString().trim()

            if (newRoom.isEmpty()) {
                binding.etRoomNumber.error = "Room unit required"
                binding.etRoomNumber.requestFocus()
                return@setOnClickListener
            }

            val newRent = rentStr.toDoubleOrNull()
            if (newRent == null || newRent <= 0.0) {
                binding.etMonthlyRent.error = "Enter a valid amount > 0"
                binding.etMonthlyRent.requestFocus()
                return@setOnClickListener
            }

            updateContract(newRoom, newRent)
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun updateContract(newRoom: String, newRent: Double) {
        val contractId = contract.contractId ?: return
        
        val updates = hashMapOf<String, Any?>(
            "roomId" to newRoom,
            "baseRentAmount" to newRent
        )

        FirebaseManager.contractsRef.child(contractId).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Tenant terms updated for ${tenant.firstName}", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
