package com.example.tenantconnect

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tenantconnect.databinding.ItemTenantBinding

class TenantAdapter(
    private var tenants: List<User>,
    private val onDeleteClick: (User) -> Unit
) : RecyclerView.Adapter<TenantAdapter.TenantViewHolder>() {

    class TenantViewHolder(val binding: ItemTenantBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TenantViewHolder {
        val binding = ItemTenantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TenantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TenantViewHolder, position: Int) {
        val tenant = tenants[position]
        holder.binding.tvTenantName.text = "${tenant.firstName} ${tenant.lastName}"
        holder.binding.tvTenantEmail.text = tenant.email
        holder.binding.tvTenantAvatar.text = tenant.firstName?.take(1) ?: "T"
        
        holder.binding.btnDeleteTenant.setOnClickListener {
            onDeleteClick(tenant)
        }
    }

    override fun getItemCount(): Int = tenants.size

    fun updateTenants(newTenants: List<User>) {
        tenants = newTenants
        notifyDataSetChanged()
    }
}
