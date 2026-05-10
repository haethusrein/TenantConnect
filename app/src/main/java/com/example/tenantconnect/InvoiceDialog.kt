package com.example.tenantconnect

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogCreateInvoiceBinding
import java.util.Locale

class InvoiceDialog(
    private val tenant: User,
    private val contract: Contract,
    private val onInvoiceSent: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogCreateInvoiceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateInvoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTenantName.text = "For: ${tenant.firstName} ${tenant.lastName}"
        
        val baseRent = contract.baseRentAmount ?: 0.0
        updateTotal(baseRent)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                calculateTotal(baseRent)
            }
        }

        binding.etWater.addTextChangedListener(watcher)
        binding.etElectric.addTextChangedListener(watcher)
        binding.etWifi.addTextChangedListener(watcher)
        binding.etOthers.addTextChangedListener(watcher)

        binding.etDueDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnSendInvoice.setOnClickListener {
            val dueDate = binding.etDueDate.text.toString().trim()
            if (dueDate.isEmpty()) {
                binding.etDueDate.error = "Select a due date"
                return@setOnClickListener
            }
            sendInvoice(baseRent, dueDate)
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance()
        val datePickerDialog = android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                binding.etDueDate.setText("${month + 1}/$day/$year")
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }

    private fun calculateTotal(baseRent: Double) {
        val water = binding.etWater.text.toString().toDoubleOrNull() ?: 0.0
        val electric = binding.etElectric.text.toString().toDoubleOrNull() ?: 0.0
        val wifi = binding.etWifi.text.toString().toDoubleOrNull() ?: 0.0
        val others = binding.etOthers.text.toString().toDoubleOrNull() ?: 0.0
        
        updateTotal(baseRent + water + electric + wifi + others)
    }

    private fun updateTotal(total: Double) {
        binding.tvTotalPreview.text = "₱${String.format(Locale.US, "%.2f", total)}"
    }

    private fun sendInvoice(baseRent: Double, dueDate: String) {
        val water = binding.etWater.text.toString().toDoubleOrNull() ?: 0.0
        val electric = binding.etElectric.text.toString().toDoubleOrNull() ?: 0.0
        val wifi = binding.etWifi.text.toString().toDoubleOrNull() ?: 0.0
        val others = binding.etOthers.text.toString().toDoubleOrNull() ?: 0.0
        val total = baseRent + water + electric + wifi + others

        val billingId = FirebaseManager.billingsRef.push().key ?: return

        val billing = Billing(
            billingId = billingId,
            contractId = contract.contractId,
            dueDate = dueDate,
            rentCharge = baseRent,
            waterCharge = water,
            electricityCharge = electric,
            wifiArrearsCharge = wifi,
            totalAmount = total,
            status = "Unpaid"
        )

        binding.btnSendInvoice.isEnabled = false

        FirebaseManager.billingsRef.child(billingId).setValue(billing)
            .addOnSuccessListener {
                Toast.makeText(context, "Invoice sent to ${tenant.firstName}", Toast.LENGTH_SHORT).show()
                onInvoiceSent()
                dismiss()
            }
            .addOnFailureListener {
                binding.btnSendInvoice.isEnabled = true
                Toast.makeText(context, "Failed to send: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
