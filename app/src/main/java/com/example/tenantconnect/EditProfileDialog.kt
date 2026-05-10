package com.example.tenantconnect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import coil.load
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    private var selectedImageUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedImageUri = it
            
            // 1. Request persistent permission so it's accessible later
            try {
                val contentResolver = requireContext().contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Dynamic Update: Show preview immediately in the dialog using Coil
            if (user.role == "Landlord") {
                _landlordBinding?.ivProfilePreview?.load(it)
            } else {
                _tenantBinding?.ivProfilePreview?.load(it)
            }
        }
    }

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
        
        // Initial display of existing photo
        user.profilePhotoUrl?.let { uriString ->
            b.ivProfilePreview.load(uriString) {
                crossfade(true)
            }
        }

        b.btnChangePhoto.setOnClickListener {
            selectImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        b.btnSave.setOnClickListener {
            val occupation = b.etOccupation.text.toString().trim()
            val address = b.etAddress.text.toString().trim()
            val civilStatus = b.etCivilStatus.text.toString().trim()

            val updates = mutableMapOf<String, Any?>(
                "occupation" to occupation,
                "originalAddress" to address,
                "civilStatus" to civilStatus
            )
            
            selectedImageUri?.let { updates["profilePhotoUrl"] = it.toString() }

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
        
        // Populate Personal Info
        b.etOccupation.setText(user.occupation)
        b.etPersonalAddress.setText(user.originalAddress)
        b.etCivilStatus.setText(user.civilStatus)
        
        // Initial display of existing photo
        user.profilePhotoUrl?.let { uriString ->
            b.ivProfilePreview.load(uriString) {
                crossfade(true)
            }
        }

        b.btnChangePhoto.setOnClickListener {
            selectImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        
        // Populate Property Info
        b.etPropertyName.setText(property?.propertyName)
        b.etPropertyAddress.setText(property?.address)
        b.etTotalUnits.setText(property?.totalRooms?.toString())

        b.btnSave.setOnClickListener {
            val occupation = b.etOccupation.text.toString().trim()
            val personalAddr = b.etPersonalAddress.text.toString().trim()
            val civilStatus = b.etCivilStatus.text.toString().trim()
            
            val propName = b.etPropertyName.text.toString().trim()
            val propAddr = b.etPropertyAddress.text.toString().trim()
            val units = b.etTotalUnits.text.toString().trim().toIntOrNull() ?: 0

            if (propName.isEmpty() || propAddr.isEmpty()) {
                Toast.makeText(context, "Property Name and Address are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = user.userId ?: return@setOnClickListener
            val propId = property?.propertyId ?: return@setOnClickListener

            // Perform updates across two nodes
            val updates = hashMapOf<String, Any?>()
            
            // 1. User Node
            updates["users/$uid/occupation"] = occupation
            updates["users/$uid/originalAddress"] = personalAddr
            updates["users/$uid/civilStatus"] = civilStatus
            selectedImageUri?.let { updates["users/$uid/profilePhotoUrl"] = it.toString() }
            
            // 2. Property Node
            updates["properties/$propId/propertyName"] = propName
            updates["properties/$propId/address"] = propAddr
            updates["properties/$propId/totalRooms"] = units

            FirebaseManager.database.reference.updateChildren(updates)
                .addOnSuccessListener {
                    Toast.makeText(context, "Profile and Property updated!", Toast.LENGTH_SHORT).show()
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
