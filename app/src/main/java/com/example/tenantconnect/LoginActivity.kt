package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnTopSignup: Button
    private lateinit var tvErrorMsg: TextView
    private lateinit var loadingOverlay: View
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnTopSignup = findViewById(R.id.btn_top_signup)
        tvErrorMsg = findViewById(R.id.tv_error_msg)
        loadingOverlay = findViewById(R.id.loading_overlay)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoading(true)
            FirebaseManager.auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    showLoading(false)
                    if (task.isSuccessful) {
                        checkUserRole()
                    } else {
                        val exception = task.exception
                        val message = when (exception) {
                            is FirebaseAuthInvalidUserException -> "No account found with this email. Please sign up."
                            is FirebaseAuthInvalidCredentialsException -> "Incorrect password. Please try again."
                            else -> "Login Failed: ${exception?.message}"
                        }
                        tvErrorMsg.text = message
                        tvErrorMsg.visibility = View.VISIBLE
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnTopSignup.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun showLoading(isLoading: Boolean) {
        loadingOverlay.isVisible = isLoading
        btnLogin.isEnabled = !isLoading
        
        if (isLoading) {
            timeoutRunnable = Runnable {
                if (loadingOverlay.isVisible) {
                    showLoading(false)
                    Toast.makeText(this, "Connection timeout. Please check your internet or Firebase config.", Toast.LENGTH_LONG).show()
                }
            }
            handler.postDelayed(timeoutRunnable!!, 10000) // 10 second timeout
        } else {
            timeoutRunnable?.let { handler.removeCallbacks(it) }
            timeoutRunnable = null
        }
    }

    private fun checkUserRole() {
        val userId = FirebaseManager.auth.currentUser?.uid ?: return
        showLoading(true)
        FirebaseManager.usersRef.child(userId).get().addOnSuccessListener { snapshot ->
            showLoading(false)
            val role = snapshot.child("role").getValue(String::class.java)
            
            when (role) {
                "Landlord" -> {
                    // For Landlords, check if they have a property set up
                    FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(userId).get()
                        .addOnSuccessListener { propSnapshot ->
                            val intent = if (!propSnapshot.exists()) {
                                // If no property, go to setup
                                Intent(this, LandlordDetailsActivity::class.java).apply {
                                    putExtra("LANDLORD_ID", userId)
                                }
                            } else {
                                // If property exists, go to dashboard
                                Intent(this, DashboardLandlordActivity::class.java)
                            }
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                        .addOnFailureListener {
                            // Fallback to dashboard if check fails
                            val intent = Intent(this, DashboardLandlordActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        }
                }
                "Tenant" -> {
                    // Tenants go to their dashboard
                    val intent = Intent(this, DashboardTenantActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                else -> {
                    // Role is null or invalid
                    FirebaseManager.auth.signOut()
                    Toast.makeText(this, "Unauthorized access or missing user role.", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener {
            showLoading(false)
            Toast.makeText(this, "Failed to retrieve user data", Toast.LENGTH_SHORT).show()
        }
    }
}
