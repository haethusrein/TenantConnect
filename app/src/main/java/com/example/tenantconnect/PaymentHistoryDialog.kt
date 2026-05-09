package com.example.tenantconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogTenantPaymentHistoryBinding
import java.util.Locale

class PaymentHistoryDialog(
    private val tenant: User,
    private val contractId: String
) : BottomSheetDialogFragment() {

    private var _binding: DialogTenantPaymentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTenantPaymentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTenantSubtitle.text = "For: ${tenant.firstName} ${tenant.lastName}"
        
        setupRecyclerView()
        loadHistory()

        binding.btnClose.setOnClickListener { dismiss() }
    }

    private fun setupRecyclerView() {
        binding.rvHistoryList.layoutManager = LinearLayoutManager(context)
    }

    private fun loadHistory() {
        FirebaseManager.billingsRef.orderByChild("contractId").equalTo(contractId).get()
            .addOnSuccessListener { snapshot ->
                val history = snapshot.children.mapNotNull { it.getValue(Billing::class.java) }
                    .filter { it.status == "Paid" }
                    .sortedByDescending { it.dueDate }
                
                binding.rvHistoryList.adapter = HistoryAdapter(history)
            }
    }

    class HistoryAdapter(private val history: List<Billing>) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bill = history[position]
            val text1 = holder.view.findViewById<TextView>(android.R.id.text1)
            val text2 = holder.view.findViewById<TextView>(android.R.id.text2)
            
            text1.text = "Due: ${bill.dueDate} • Paid"
            text1.setTextColor(holder.view.context.getColor(R.color.navy))
            
            val amount = String.format(Locale.US, "₱%.2f", bill.totalAmount ?: 0.0)
            text2.text = "Total: $amount"
        }

        override fun getItemCount(): Int = history.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
