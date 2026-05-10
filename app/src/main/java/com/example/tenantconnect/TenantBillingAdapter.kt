package com.example.tenantconnect

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import coil.load
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.tenantconnect.databinding.ItemTenantBillingBinding
import java.util.Locale

class TenantBillingAdapter(
    private val onActionClick: (User, Contract, Billing?) -> Unit
) : RecyclerView.Adapter<TenantBillingAdapter.BillingViewHolder>() {

    private val items = mutableListOf<TenantBillingItem>()

    data class TenantBillingItem(
        val tenant: User,
        val contract: Contract,
        val latestBilling: Billing?
    )

    class BillingViewHolder(val binding: ItemTenantBillingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BillingViewHolder {
        val binding = ItemTenantBillingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BillingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BillingViewHolder, position: Int) {
        val item = items[position]
        val tenant = item.tenant
        val contract = item.contract
        val billing = item.latestBilling

        // 1. Tenant Info
        holder.binding.tvNameRoom.text = "${tenant.firstName} ${tenant.lastName} • ${contract.roomId}"
        
        // Avatar
        ImageUtils.loadImage(holder.binding.ivTenantPhoto, tenant.profilePhotoUrl)
        holder.binding.ivTenantPhoto.isVisible = !tenant.profilePhotoUrl.isNullOrEmpty()
        holder.binding.tvAvatar.isVisible = tenant.profilePhotoUrl.isNullOrEmpty()
        if (holder.binding.tvAvatar.isVisible) {
            holder.binding.tvAvatar.text = tenant.firstName?.take(1) ?: "T"
        }

        // 2. Billing Status Logic
        if (billing == null) {
            // No billing issued yet
            holder.binding.tvDueAmount.text = "No invoice issued"
            holder.binding.btnStatus.text = "Issue"
            holder.binding.btnStatus.setBackgroundResource(R.drawable.bg_button)
            holder.binding.btnStatus.backgroundTintList = holder.itemView.context.getColorStateList(R.color.navy)
            
            holder.binding.btnAction.isVisible = true
            holder.binding.btnAction.text = "Send Invoice"
            holder.binding.btnAction.setBackgroundResource(R.drawable.bg_button)
            holder.binding.btnAction.backgroundTintList = holder.itemView.context.getColorStateList(R.color.navy)
        } else {
            val amountStr = String.format(Locale.US, "₱%.2f", billing.totalAmount ?: 0.0)
            holder.binding.tvDueAmount.text = "Due ${billing.dueDate} • $amountStr"
            holder.binding.btnStatus.text = billing.status
            
            when (billing.status) {
                "Paid" -> {
                    holder.binding.btnStatus.setBackgroundResource(R.drawable.bg_status_badge)
                    holder.binding.btnStatus.backgroundTintList = holder.itemView.context.getColorStateList(android.R.color.holo_green_dark)
                    holder.binding.btnAction.isVisible = false
                }
                "Overdue" -> {
                    holder.binding.btnStatus.setBackgroundResource(R.drawable.bg_status_badge)
                    holder.binding.btnStatus.backgroundTintList = holder.itemView.context.getColorStateList(android.R.color.holo_orange_dark)
                    holder.binding.btnAction.isVisible = true
                    holder.binding.btnAction.text = "Mark as received"
                }
                else -> { // Unpaid
                    holder.binding.btnStatus.setBackgroundResource(R.drawable.bg_status_badge)
                    holder.binding.btnStatus.backgroundTintList = holder.itemView.context.getColorStateList(android.R.color.holo_red_dark)
                    holder.binding.btnAction.isVisible = true
                    holder.binding.btnAction.text = "Mark as received"
                }
            }
        }

        val clickListener = View.OnClickListener {
            onActionClick(tenant, contract, billing)
        }
        
        holder.binding.btnAction.setOnClickListener(clickListener)
        holder.binding.btnStatus.setOnClickListener(clickListener)

        holder.itemView.setOnClickListener {
            // View full history on item tap
            val dialog = PaymentHistoryDialog(tenant, contract.contractId ?: "")
            (holder.itemView.context as? androidx.fragment.app.FragmentActivity)?.supportFragmentManager?.let {
                dialog.show(it, "PaymentHistoryDialog")
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<TenantBillingItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
