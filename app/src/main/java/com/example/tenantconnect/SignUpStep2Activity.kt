package com.example.tenantconnect

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivitySignupStep2Binding

class SignUpStep2Activity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupStep2Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupStep2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val roles = arrayOf("Tenant", "Landlord")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        binding.spinnerRole.adapter = adapter

        // Populate fields if returning from edit
        intent.extras?.let { extras ->
            binding.etUserName.setText(extras.getString("userName"))
            binding.etEmail.setText(extras.getString("email"))
            binding.etPassword.setText(extras.getString("password"))
            binding.etConfirmPassword.setText(extras.getString("password"))
            val role = extras.getString("role")
            if (role != null) {
                val position = roles.indexOf(role)
                if (position >= 0) binding.spinnerRole.setSelection(position)
            }
        }

        binding.btnConfirm.setOnClickListener {
            val intent = Intent(this, SignUpConfirmActivity::class.java)
            // Pass data from step 1 (received via previous intent) and step 2
            intent.putExtras(getIntent().extras ?: Bundle())
            intent.putExtra("userName", binding.etUserName.text.toString())
            intent.putExtra("email", binding.etEmail.text.toString())
            intent.putExtra("password", binding.etPassword.text.toString())
            intent.putExtra("role", binding.spinnerRole.selectedItem.toString())
            startActivity(intent)
        }

        binding.btnTopLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }
    }
}
