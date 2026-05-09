package com.example.tenantconnect

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val currentUserId: String, private val isLandlordMode: Boolean) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<Message>()

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            SentMessageViewHolder(inflater.inflate(R.layout.item_message_right, parent, false))
        } else {
            ReceivedMessageViewHolder(inflater.inflate(R.layout.item_message_left, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message, isLandlordMode)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun submitList(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    class SentMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tv_message)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)

        fun bind(message: Message) {
            tvMessage.text = message.messageText
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            tvTime.text = sdf.format(Date(message.timestamp ?: 0L))
        }
    }

    class ReceivedMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tv_message)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)
        private val tvSender: TextView = view.findViewById(R.id.tv_sender)

        fun bind(message: Message, isLandlordMode: Boolean) {
            tvMessage.text = message.messageText
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            tvTime.text = sdf.format(Date(message.timestamp ?: 0L))
            tvSender.text = if (isLandlordMode) "Tenant" else "Landlord"
        }
    }
}
