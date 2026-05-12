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
                        loadUserData()
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
            .setTitle("Delete Account")
            .setMessage("This action is permanent. All your property, room, and contract records will be erased.")
            .setView(layout)
            .setPositiveButton("DELETE") { _, _ ->
                val pass = etPassword.text.toString().trim()
                if (pass.isEmpty()) return@setPositiveButton

                val user = FirebaseManager.auth.currentUser ?: return@setPositiveButton
                val uid = user.uid
                val credential = EmailAuthProvider.getCredential(user.email!!, pass)

                user.reauthenticate(credential).addOnSuccessListener {
                    // 1. Find the property
                    FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(uid).get().addOnSuccessListener { snapshot ->
                        val property = snapshot.children.firstOrNull()?.getValue(Property::class.java)
                        val propId = property?.propertyId

                        // 2. Delete everything related to this landlord
                        val updates = hashMapOf<String, Any?>()
                        if (propId != null) {
                            updates["properties/$propId"] = null
                            // Also need to find and delete all rooms for this property
                            FirebaseManager.roomsRef.orderByChild("propertyId").equalTo(propId).get().addOnSuccessListener { roomSnap ->
                                for (room in roomSnap.children) { updates["rooms/${room.key}"] = null }
                                
                                // And all announcements
                                FirebaseManager.announcementsRef.orderByChild("propertyId").equalTo(propId).get().addOnSuccessListener { annSnap ->
                                    for (ann in annSnap.children) { updates["announcements/${ann.key}"] = null }

                                    // And all contracts
                                    FirebaseManager.contractsRef.orderByChild("landlordId").equalTo(uid).get().addOnSuccessListener { contractSnap ->
                                        for (contract in contractSnap.children) { updates["contracts/${contract.key}"] = null }

                                        FirebaseManager.database.reference.updateChildren(updates).addOnSuccessListener {
                                            FirebaseManager.usersRef.child(uid).removeValue().addOnSuccessListener {
                                                user.delete().addOnSuccessListener {
                                                    Toast.makeText(this, "Account successfully deleted.", Toast.LENGTH_LONG).show()
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
                        } else {
                            // No property found, just delete user and auth
                            FirebaseManager.usersRef.child(uid).removeValue().addOnSuccessListener {
                                user.delete().addOnSuccessListener {
                                    startActivity(Intent(this, LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    })
                                    finish()
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
            startActivity(Intent(this, ProfileTenantActivity::class.java))
            finish()
        }
    }
}