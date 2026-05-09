package com.example.tenantconnect

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class SignUpActivity : AppCompatActivity() {

    private lateinit var btnTopLogin: Button
    private lateinit var btnNext: Button
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var etGender: AutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // Initialize views
        btnTopLogin = findViewById(R.id.btn_top_login)
        btnNext = findViewById(R.id.btnNext)
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        etBirthDate = findViewById(R.id.etBirthDate)
        etGender = findViewById(R.id.etGender)

        // Populate fields if returning from edit
        intent.extras?.let { extras ->
            etFirstName.setText(extras.getString("firstName"))
            etMiddleName.setText(extras.getString("middleName"))
            etLastName.setText(extras.getString("lastName"))
            etBirthDate.setText(extras.getString("birthDate"))
            etGender.setText(extras.getString("gender"), false)
        }

        // Set up Gender dropdown
        val genderOptions = resources.getStringArray(R.array.gender_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genderOptions)
        etGender.setAdapter(adapter)

        // Show dropdown when Gender field is clicked
        etGender.setOnClickListener {
            etGender.showDropDown()
        }

        // Set up Birth Date calendar picker
        etBirthDate.setOnClickListener {
            showDatePickerDialog()
        }

        // Navigate back to Login
        btnTopLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        // Handle Next step
        btnNext.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val birthDate = etBirthDate.text.toString().trim()
            val gender = etGender.text.toString().trim()

            if (firstName.isEmpty() || lastName.isEmpty() || birthDate.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, SignUpStep2Activity::class.java)
            intent.putExtra("firstName", firstName)
            intent.putExtra("middleName", etMiddleName.text.toString().trim())
            intent.putExtra("lastName", lastName)
            intent.putExtra("birthDate", birthDate)
            intent.putExtra("gender", gender)
            startActivity(intent)
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar[Calendar.YEAR]
        val month = calendar[Calendar.MONTH]
        val day = calendar[Calendar.DAY_OF_MONTH]

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Format the date and set it to the EditText
                val dateString = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                etBirthDate.setText(dateString)
            },
            year,
            month,
            day,
        )
        datePickerDialog.show()
    }
}
