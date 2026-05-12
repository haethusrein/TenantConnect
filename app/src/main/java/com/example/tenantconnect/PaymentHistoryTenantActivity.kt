package com.example.tenantconnect

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.example.tenantconnect.databinding.ActivityPaymentHistoryTenantBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import java.util.Locale

class PaymentHistoryTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaymentHistoryTenantBinding
    private var activeContracts = mutableListOf<Contract>()
    private var historyListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentHistoryTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val userId = FirebaseManager.auth.currentUser?.uid
        if (userId != null) {
            loadContracts(userId)
        }

        setupMenu()
        setupBottomNavigation()
    }

    private fun loadContracts(userId: String) {
        FirebaseManager.contractsRef.orderByChild("tenantId").equalTo(userId).get()
            .addOnSuccessListener { snapshot ->
                activeContracts = snapshot.children.mapNotNull { it.getValue(Contract::class.java) }
                    .filter { it.status == "Active" }.toMutableList()

                if (activeContracts.isNotEmpty()) {
                    setupContractSpinner()
                    listenForHistory(activeContracts[0].contractId ?: "")
                } else {
                    binding.spinnerContracts.isVisible = false
                    showNoHistory("No active contract found.")
                }
            }
    }

    private fun setupContractSpinner() {
        if (activeContracts.size > 1) {
            binding.spinnerContracts.isVisible = true
            val names = activeContracts.map { "Unit ${it.roomId}" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            binding.spinnerContracts.adapter = adapter
            binding.spinnerContracts.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    listenForHistory(activeContracts[pos].contractId ?: "")
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        } else {
            binding.spinnerContracts.isVisible = false
        }
    }

    private fun listenForHistory(contractId: String) {
        historyListener?.let { FirebaseManager.billingsRef.removeEventListener(it) }
        
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

        card.setOnClickListener {
            val dialog = PaymentBreakdownDialog(bill)
            dialog.show(supportFragmentManager, "PaymentBreakdownDialog")
        }
        binding.llHistoryContainer.addView(card)
    }

    private fun showNoHistory(message: String) {
        binding.llHistoryContainer.removeAllViews()
        val tv = TextView(this).apply {
            text = message; setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 40, 0, 0); textAlignment = View.TEXT_ALIGNMENT_CENTER
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
                    val v = super.getView(position, convertView, parent) as TextView
                    v.setTextColor(Color.WHITE); v.setPadding(40, 30, 40, 30); v.textSize = 14f
                    return v
                }
            }
            popup.setAdapter(adapter)
            popup.width = 600
            popup.setBackgroundDrawable(ColorDrawable("#22223B".toColorInt()))
            popup.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position]) {
                    "Announcements" -> startActivity(Intent(this, AnnouncementsTenantActivity::class.java))
                    "Settings" -> startActivity(Intent(this, SettingsTenantActivity::class.java))
                    "Log out" -> logout()
                }
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun logout() {
        FirebaseManager.auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
        finishAffinity()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener { startActivity(Intent(this, DashboardTenantActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }) }
        binding.bottomNav.navNotifications.setOnClickListener { startActivity(Intent(this, InboxTenantActivity::class.java)) }
        binding.bottomNav.navPayments.setOnClickListener { startActivity(Intent(this, PaymentTenantActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }) }
        binding.bottomNav.navProfile.setOnClickListener { startActivity(Intent(this, ProfileTenantActivity::class.java)) }
    }
}