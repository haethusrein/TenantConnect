package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivitySettingsLandlordBinding
import com.google.firebase.auth.EmailAuthProvider

class SettingsLandlordActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsLandlordBinding
    private var currentUserData: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsLandlordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadUserData()
        setupBottomNavigation()
    }

    private fun loadUserData() {
        val uid = FirebaseManager.auth.currentUser?.uid ?: return
        FirebaseManager.usersRef.child(uid).get().addOnSuccessListener { snapshot ->
            currentUserData = snapshot.getValue(User::class.java)
            if (currentUserData != null) {
                binding.tvSettingsName.text = "${currentUserData!!.firstName} ${currentUserData!!.lastName}"
                binding.tvSettingsEmail.text = currentUserData!!.email
                ImageUtils.loadImage(binding.ivSettingsProfile, currentUserData!!.profilePhotoUrl)
            }
        }
    }

    private fun setupUI() {
        binding.ivBack.setOnClickListener { finish() }

        binding.btnChangeName.setOnClickListener { showChangeNameDialog() }
        binding.btnChangeEmail.setOnClickListener { showChangeEmailDialog() }
        binding.btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        binding.btnDeleteAccount.setOnClickListener { showDeleteAccountDialog() }

        binding.btnLogout.setOnClickListener {
            FirebaseManager.auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // =========================================================================
    // DIALOG LOGIC & FIREBASE OPERATIONS
    // =========================================================================

    private fun showChangeNameDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 10) }
        val etFirst = EditText(this).apply { hint = "First Name"; setText(currentUserData?.firstName) }
        val etLast = EditText(this).apply { hint = "Last Name"; setText(currentUserData?.lastName) }
        layout.addView(etFirst)
        layout.addView(etLast)

        AlertDialog.Builder(this).setTitle("Change Profile Name").setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val fName = etFirst.text.toString().trim()
                val lName = etLast.text.toString().trim()
                if (fName.isEmpty() || lName.isEmpty()) return@setPositiveButton

                val uid = FirebaseManager.auth.currentUser?.uid ?: return@setPositiveButton
                FirebaseManager.usersRef.child(uid).updateChildren(mapOf("firstName" to fName, "lastName" to lName))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Name updated!", Toast.LENGTH_SHORT).show()
                        loadUserData() // Refresh UI
                    }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showChangeEmailDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 10) }
        val etNewEmail = EditText(this).apply { hint = "New Email Address"; inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val etPassword = EditText(this).apply { hint = "Current Password (Required)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(etNewEmail)
        layout.addView(etPassword)

        AlertDialog.Builder(this).setTitle("Change Email").setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val newEmail = etNewEmail.text.toString().trim()
                val pass = etPassword.text.toString().trim()
                if (newEmail.isEmpty() || pass.isEmpty()) return@setPositiveButton

                val user = FirebaseManager.auth.currentUser ?: return@setPositiveButton
                val credential = EmailAuthProvider.getCredential(user.email!!, pass)

                // Must re-authenticate before changing sensitive credentials
                user.reauthenticate(credential).addOnSuccessListener {
                    user.updateEmail(newEmail).addOnSuccessListener {
                        FirebaseManager.usersRef.child(user.uid).child("email").setValue(newEmail)
                        Toast.makeText(this, "Email successfully changed!", Toast.LENGTH_SHORT).show()
                        loadUserData()
                    }.addOnFailureListener { Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
                }.addOnFailureListener { Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showChangePasswordDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 10) }
        val etCurrentPass = EditText(this).apply { hint = "Current Password"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val etNewPass = EditText(this).apply { hint = "New Password"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(etCurrentPass)
        layout.addView(etNewPass)

        AlertDialog.Builder(this).setTitle("Change Password").setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val current = etCurrentPass.text.toString().trim()
                val newPass = etNewPass.text.toString().trim()
                if (current.isEmpty() || newPass.isEmpty()) return@setPositiveButton

                val user = FirebaseManager.auth.currentUser ?: return@setPositiveButton
                val credential = EmailAuthProvider.getCredential(user.email!!, current)

                user.reauthenticate(credential).addOnSuccessListener {
                    user.updatePassword(newPass).addOnSuccessListener {
                        Toast.makeText(this, "Password updated!", Toast.LENGTH_SHORT).show()
                    }.addOnFailureListener { Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show() }
                }.addOnFailureListener { Toast.makeText(this, "Incorrect current password", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showDeleteAccountDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 40, 50, 10) }
        val etPassword = EditText(this).apply { hint = "Enter Password to Confirm"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        layout.addView(etPassword)

        AlertDialog.Builder(this)
            .setTitle("Delete Landlord Account")
            .setMessage("WARNING: This will permanently delete your account, your properties, and terminate all tenant contracts. Your tenants will be orphaned and reset to Inactive status.")
            .setView(layout)
            .setPositiveButton("DELETE EVERYTHING") { _, _ ->
                val pass = etPassword.text.toString().trim()
                if (pass.isEmpty()) return@setPositiveButton

                val user = FirebaseManager.auth.currentUser ?: return@setPositiveButton
                val uid = user.uid
                val credential = EmailAuthProvider.getCredential(user.email!!, pass)


                user.reauthenticate(credential).addOnSuccessListener {

                    FirebaseManager.usersRef.orderByChild("landlordId").equalTo(uid).get().addOnSuccessListener { tSnap ->
                        for (tenant in tSnap.children) {
                            tenant.ref.child("landlordId").removeValue() // Detach landlord
                            tenant.ref.child("status").setValue("Inactive") // Reset status
                        }

                        // 2. Delete Properties
                        FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(uid).get().addOnSuccessListener { pSnap ->
                            for (prop in pSnap.children) { prop.ref.removeValue() }

                            // 3. Delete Contracts
                            FirebaseManager.contractsRef.orderByChild("landlordId").equalTo(uid).get().addOnSuccessListener { cSnap ->
                                for (contract in cSnap.children) { contract.ref.removeValue() }

                                // 4. Delete pending invitations
                                FirebaseManager.invitationsRef.orderByChild("landlordId").equalTo(uid).get().addOnSuccessListener { iSnap ->
                                    for (inv in iSnap.children) { inv.ref.removeValue() }

                                    // 5. Delete Landlord Database Node
                                    FirebaseManager.usersRef.child(uid).removeValue().addOnSuccessListener {

                                        // 6. Finally, Delete the Landlord Auth Account
                                        user.delete().addOnSuccessListener {
                                            Toast.makeText(this, "Account and all property data successfully deleted.", Toast.LENGTH_LONG).show()
                                            startActivity(Intent(this, LoginActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            })
                                            finish()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.addOnFailureListener { Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.navHome.setOnClickListener {
            startActivity(Intent(this, DashboardLandlordActivity::class.java))
            finish()
        }
        binding.bottomNav.navNotifications.setOnClickListener {
            startActivity(Intent(this, InboxLandlordActivity::class.java))
            finish()
        }
        binding.bottomNav.navPayments.setOnClickListener {
            startActivity(Intent(this, PaymentLandlordActivity::class.java))
            finish()
        }
        binding.bottomNav.navProfile.setOnClickListener {
            startActivity(Intent(this, LandlordDetailsActivity::class.java))
            finish()
        }
    }
}