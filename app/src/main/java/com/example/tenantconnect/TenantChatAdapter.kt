package com.example.tenantconnect

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.tenantconnect.databinding.ItemTenantBinding

class TenantChatAdapter(
    private var tenants: List<User>,
    private val onChatClick: (User) -> Unit
) : RecyclerView.Adapter<TenantChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(val binding: ItemTenantBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemTenantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val tenant = tenants[position]
        holder.binding.tvTenantName.text = "${tenant.firstName} ${tenant.lastName}"
        holder.binding.tvTenantEmail.text = tenant.email
        holder.binding.tvTenantAvatar.text = tenant.firstName?.take(1) ?: "T"
        
        // Load photo or show initial fallback
        ImageUtils.loadImage(holder.binding.ivTenantPhoto, tenant.profilePhotoUrl)
        holder.binding.ivTenantPhoto.isVisible = !tenant.profilePhotoUrl.isNullOrEmpty()
        holder.binding.tvTenantAvatar.isVisible = tenant.profilePhotoUrl.isNullOrEmpty()

        // Hide the delete button for the chat list
        holder.binding.btnDeleteTenant.visibility = View.GONE
        holder.binding.btnEditTenant.visibility = View.GONE
        
        holder.itemView.setOnClickListener {
            onChatClick(tenant)
        }
    }

    override fun getItemCount(): Int = tenants.size

    fun updateTenants(newTenants: List<User>) {
        tenants = newTenants
        notifyDataSetChanged()
    }
}
