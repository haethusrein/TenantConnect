package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.example.tenantconnect.databinding.ActivityPaymentHistoryTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class PaymentHistoryTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentHistoryTenantBinding
    private var historyListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentHistoryTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            loadPaymentHistory(userId)
        }

        setupMenu()
        setupBottomNavigation()
    }

    private fun loadPaymentHistory(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                val activeContractId = snapshot.children.firstOrNull { 
                    it.child("status").getValue(String::class.java) == "Active" 
                }?.key

                if (activeContractId != null) {
                    listenForHistory(activeContractId)
                } else {
                    showNoHistory("No active contract found.")
                }
            }
    }

    private fun listenForHistory(contractId: String) {
        historyListener = FirebaseManager.billingsRef.orderByChild("contractId").equalTo(contractId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isFinishing || isDestroyed) return
                    
                    binding.llHistoryContainer.removeAllViews()
                    val history = snapshot.children.mapNotNull { it.getValue(Billing::class.java) }
                        .filter { it.status == "Paid" }
                        .sortedByDescending { it.datePaid }

                    if (history.isEmpty()) {
                        showNoHistory("No payment history yet.")
                    } else {
                        var index = 1
                        for (bill in history) {
                            addHistoryCard(index++, bill)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun addHistoryCard(index: Int, bill: Billing) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_history, binding.llHistoryContainer, false)
        
        card.findViewById<TextView>(R.id.tv_num).text = index.toString()
        card.findViewById<TextView>(R.id.tv_date).text = bill.dueDate
        
        val amount = String.format(Locale.US, "₱%.2f", bill.totalAmount ?: 0.0)
        card.findViewById<TextView>(R.id.tv_amount).text = amount
        
        binding.llHistoryContainer.addView(card)
    }

    private fun showNoHistory(message: String) {
        binding.llHistoryContainer.removeAllViews()
        val tv = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 40, 0, 0)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        binding.llHistoryContainer.addView(tv)
    }

    override fun onDestroy() {
        super.onDestroy()
        historyListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
    }

    private fun setupMenu() {
        binding.ivMenu.setOnClickListener { view ->
            val menuItems = arrayOf("Announcements", "Settings", "Log out")
            val popup = ListPopupWindow(this)
            popup.anchorView = view
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, menuItems) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(Color.WHITE)
                    view.setPadding(40, 30, 40, 30)
                    view.textSize = 14f
                    return view
                }
            }
            popup.setAdapter(adapter)
            popup.width = 600
            popup.setBackgroundDrawable(ColorDrawable("#22223B".toColorInt()))
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> {
                        logout()
                    }
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun logout() {
        historyListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        FirebaseManager.auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finishAffinity()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener {
            val intent = Intent(this, DashboardTenantActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, InboxTenantActivity::class.java))
        }
        binding.bottomNav.navPayments.setOnClickListener {
            startActivity(Intent(this, PaymentTenantActivity::class.java))
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileTenantActivity::class.java))
        }
    }
}
