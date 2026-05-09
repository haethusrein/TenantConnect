package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivitySignupConfirmBinding

class SignUpConfirmActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupConfirmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        var isPasswordVisible = false
        val originalPassword = extras?.getString("password") ?: ""

        if (extras != null) {
            val fullName = "${extras.getString("firstName")} ${extras.getString("middleName", "")} ${extras.getString("lastName")}"
            binding.tvConfirmName.text = fullName
            binding.tvConfirmBirthDate.text = extras.getString("birthDate")
            binding.tvConfirmGender.text = extras.getString("gender")
            binding.tvConfirmEmail.text = extras.getString("email")
            binding.tvConfirmPassword.text = originalPassword.replace(Regex("."), "*")
            binding.tvConfirmStatus.text = extras.getString("role")
        }

        binding.ivToggleConfirmPassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.tvConfirmPassword.text = originalPassword
                binding.ivToggleConfirmPassword.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            } else {
                binding.tvConfirmPassword.text = originalPassword.replace(Regex("."), "*")
                binding.ivToggleConfirmPassword.setImageResource(android.R.drawable.ic_menu_view)
            }
        }

        binding.btnEdit.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            val extras = intent.extras ?: Bundle()
            intent.putExtras(extras)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        binding.btnFinalConfirm.setOnClickListener {
            registerUser()
        }

        binding.btnTopLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }

    private fun showLoading(isLoading: Boolean, message: String = "Processing...") {
        val loadingOverlay = findViewById<View>(R.id.loading_overlay)
        val tvMessage = findViewById<TextView>(R.id.tv_loading_message)
        
        loadingOverlay?.visibility = if (isLoading) View.VISIBLE else View.GONE
        tvMessage?.text = message
        binding.btnFinalConfirm.isEnabled = !isLoading

        if (isLoading) {
            loadingOverlay?.postDelayed({
                if (loadingOverlay.visibility == View.VISIBLE) {
                    showLoading(false)
                    Toast.makeText(this, "Operation timed out. Please check your connection.", Toast.LENGTH_LONG).show()
                }
            }, 10000)
        }
    }

    private fun registerUser() {
        val extras = intent.extras ?: return
        val email = extras.getString("email") ?: return
        val password = extras.getString("password") ?: return
        val role = extras.getString("role") ?: "Tenant"

        showLoading(true, "Creating Account...")
        FirebaseManager.auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = FirebaseManager.auth.currentUser?.uid
                    if (userId != null) {
                        saveUserToDatabase(userId, role, extras)
                    }
                } else {
                    showLoading(false)
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserToDatabase(userId: String, role: String, extras: Bundle) {
        val user = User(
            userId = userId,
            role = role,
            firstName = extras.getString("firstName"),
            middleName = extras.getString("middleName"),
            lastName = extras.getString("lastName"),
            email = extras.getString("email"),
            birthDate = extras.getString("birthDate"),
            gender = extras.getString("gender")
        )

        showLoading(true, "Saving Profile...")
        FirebaseManager.usersRef.child(userId).setValue(user)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Registration Successful! Please log in.", Toast.LENGTH_LONG).show()
                
                // After successful registration, we redirect to LoginActivity
                val nextIntent = Intent(this, LoginActivity::class.java)
                nextIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(nextIntent)
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Failed to save user data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
