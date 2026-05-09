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

            if (email.isEmpty()) {
                etEmail.error = "Email is required"
                etEmail.requestFocus()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                etPassword.error = "Password is required"
                etPassword.requestFocus()
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
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            
            val role = snapshot.child("role").getValue(String::class.java)
            
            when (role) {
                "Landlord" -> {
                    // Check if property is set up via user's propertyId field first
                    val propertyId = snapshot.child("propertyId").getValue(String::class.java)
                    
                    if (propertyId.isNullOrEmpty()) {
                        // Double check the properties node just in case
                        FirebaseManager.propertiesRef.orderByChild("landlordId").equalTo(userId).get()
                            .addOnSuccessListener { propSnapshot ->
                                if (isFinishing || isDestroyed) return@addOnSuccessListener
                                showLoading(false)
                                
                                val intent = if (!propSnapshot.exists()) {
                                    Intent(this, LandlordDetailsActivity::class.java).apply {
                                        putExtra("LANDLORD_ID", userId)
                                    }
                                } else {
                                    Intent(this, DashboardLandlordActivity::class.java)
                                }
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                            .addOnFailureListener {
                                showLoading(false)
                                // Fallback to Dashboard if the check fails (e.g. index error)
                                val intent = Intent(this, DashboardLandlordActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            }
                    } else {
                        showLoading(false)
                        val intent = Intent(this, DashboardLandlordActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                }
                "Tenant" -> {
                    showLoading(false)
                    // Tenants go to their dashboard
                    val intent = Intent(this, DashboardTenantActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                else -> {
                    showLoading(false)
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
