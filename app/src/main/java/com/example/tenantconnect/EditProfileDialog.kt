package com.example.tenantconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogEditProfileTenantBinding
import com.example.tenantconnect.databinding.DialogEditProfileLandlordBinding

class EditProfileDialog(
    private val user: User,
    private val property: Property? = null,
    private val onUpdateSuccess: () -> Unit
) : BottomSheetDialogFragment() {

    private var _tenantBinding: DialogEditProfileTenantBinding? = null
    private var _landlordBinding: DialogEditProfileLandlordBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return if (user.role == "Landlord") {
            _landlordBinding = DialogEditProfileLandlordBinding.inflate(inflater, container, false)
            _landlordBinding!!.root
        } else {
            _tenantBinding = DialogEditProfileTenantBinding.inflate(inflater, container, false)
            _tenantBinding!!.root
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (user.role == "Landlord") {
            setupLandlordEdit()
        } else {
            setupTenantEdit()
        }
    }

    private fun setupTenantEdit() {
        val b = _tenantBinding!!
        b.etOccupation.setText(user.occupation)
        b.etAddress.setText(user.originalAddress)
        b.etCivilStatus.setText(user.civilStatus)

        b.btnSave.setOnClickListener {
            val occupation = b.etOccupation.text.toString().trim()
            val address = b.etAddress.text.toString().trim()
            val civilStatus = b.etCivilStatus.text.toString().trim()

            val updates = mapOf(
                "occupation" to occupation,
                "originalAddress" to address,
                "civilStatus" to civilStatus
            )

            user.userId?.let { uid ->
                FirebaseManager.usersRef.child(uid).updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                        onUpdateSuccess()
                        dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        b.btnCancel.setOnClickListener { dismiss() }
    }

    private fun setupLandlordEdit() {
        val b = _landlordBinding!!
        b.etPropertyName.setText(property?.propertyName)
        b.etPropertyAddress.setText(property?.address)
        b.etTotalUnits.setText(property?.totalRooms?.toString())

        b.btnSave.setOnClickListener {
            val name = b.etPropertyName.text.toString().trim()
            val addr = b.etPropertyAddress.text.toString().trim()
            val units = b.etTotalUnits.text.toString().trim().toIntOrNull() ?: 0

            if (name.isEmpty() || addr.isEmpty()) {
                Toast.makeText(context, "Name and Address are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val propId = property?.propertyId ?: return@setOnClickListener

            val propUpdates = mapOf(
                "propertyName" to name,
                "address" to addr,
                "totalRooms" to units
            )

            FirebaseManager.propertiesRef.child(propId).updateChildren(propUpdates)
                .addOnSuccessListener {
                    Toast.makeText(context, "Property info updated!", Toast.LENGTH_SHORT).show()
                    onUpdateSuccess()
                    dismiss()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        b.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _tenantBinding = null
        _landlordBinding = null
    }
}
