package com.example.tenantconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogPaymentBreakdownBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentBreakdownDialog(private val bill: Billing) : BottomSheetDialogFragment() {

    private var _binding: DialogPaymentBreakdownBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPaymentBreakdownBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val locale = Locale.US
        val df = SimpleDateFormat("MM/dd/yyyy", locale)

        // 1. Title and Period
        binding.tvBillingPeriod.text = "Period: ${bill.billingPeriodStart ?: "N/A"} - ${bill.billingPeriodEnd ?: "N/A"}"

        // 2. Amounts
        binding.tvRentalAmount.text = String.format(locale, "₱%.2f", bill.rentCharge ?: 0.0)
        
        binding.tvWaterUsage.text = "${bill.waterUsage ?: 0.0} m³"
        binding.tvWaterAmount.text = String.format(locale, "₱%.2f", bill.waterCharge ?: 0.0)

        binding.tvElectricUsage.text = "${bill.electricityUsage ?: 0.0} kWh"
        binding.tvElectricAmount.text = String.format(locale, "₱%.2f", bill.electricityCharge ?: 0.0)

        binding.tvWifiAmount.text = String.format(locale, "₱%.2f", bill.wifiArrearsCharge ?: 0.0)

        // 3. Totals
        binding.tvTotalAmount.text = String.format(locale, "₱%.2f", bill.totalAmount ?: 0.0)
        
        val datePaid = bill.datePaid?.let { df.format(Date(it)) } ?: "N/A"
        binding.tvDatePaid.text = datePaid

        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
