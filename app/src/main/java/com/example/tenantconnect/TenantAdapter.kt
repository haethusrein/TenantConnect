package com.example.tenantconnect

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.tenantconnect.databinding.ItemTenantBinding

class TenantAdapter(
    private var tenants: List<User>,
    private val onEditClick: (User) -> Unit,
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
        
        // Load photo or show initial fallback
        tenant.profilePhotoUrl?.let { uriString ->
            holder.binding.ivTenantPhoto.load(uriString) {
                crossfade(true)
                placeholder(null) // Or a default placeholder
                error(null) // Or a default error image
            }
            holder.binding.ivTenantPhoto.isVisible = true
            holder.binding.tvTenantAvatar.isVisible = false
        } ?: run {
            holder.binding.ivTenantPhoto.isVisible = false
            holder.binding.tvTenantAvatar.isVisible = true
        }

        holder.binding.btnEditTenant.setOnClickListener {
            onEditClick(tenant)
        }

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
