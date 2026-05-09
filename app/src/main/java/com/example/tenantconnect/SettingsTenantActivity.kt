package com.example.tenantconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tenantconnect.databinding.ActivitySettingsTenantBinding

class SettingsTenantActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsTenantBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
    }
}
