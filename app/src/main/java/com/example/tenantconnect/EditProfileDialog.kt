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
            
            val uid = user.userId ?: return@setOnClickListener

            if (selectedImageUri != null) {
                uploadImage(selectedImageUri!!) { downloadUrl ->
                    if (downloadUrl != null) {
                        updates["profilePhotoUrl"] = downloadUrl
                    }
                    saveTenantUpdates(uid, updates)
                }
            } else {
                saveTenantUpdates(uid, updates)
            }
        }

        b.btnCancel.setOnClickListener { dismiss() }
    }

    private fun saveTenantUpdates(uid: String, updates: Map<String, Any?>) {
        setLoading(true)
        FirebaseManager.usersRef.child(uid).updateChildren(updates)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(context, "Profile updated!", Toast.LENGTH_SHORT).show()
                onUpdateSuccess()
                dismiss()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(context, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImage(uri: Uri, callback: (String?) -> Unit) {
        setLoading(true)
        val fileName = "${user.userId}_${System.currentTimeMillis()}.jpg"
        val ref = FirebaseManager.profilePhotosRef.child(fileName)

        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    callback(downloadUri.toString())
                }.addOnFailureListener {
                    setLoading(false)
                    Toast.makeText(context, "Failed to get download URL", Toast.LENGTH_SHORT).show()
                    callback(null)
                }
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(context, "Upload failed: ${it.message}", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    private fun setLoading(isLoading: Boolean) {
        val progressBar = if (user.role == "Landlord") _landlordBinding?.progressBar else _tenantBinding?.progressBar
        val btnSave = if (user.role == "Landlord") _landlordBinding?.btnSave else _tenantBinding?.btnSave
        
        progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSave?.isEnabled = !isLoading
        isCancelable = !isLoading
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
            
            // 2. Property Node
            updates["properties/$propId/propertyName"] = propName
            updates["properties/$propId/address"] = propAddr
            updates["properties/$propId/totalRooms"] = units

            if (selectedImageUri != null) {
                uploadImage(selectedImageUri!!) { downloadUrl ->
                    if (downloadUrl != null) {
                        updates["users/$uid/profilePhotoUrl"] = downloadUrl
                    }
                    performLandlordUpdates(updates)
                }
            } else {
                performLandlordUpdates(updates)
            }
        }

        b.btnCancel.setOnClickListener { dismiss() }
    }

    private fun performLandlordUpdates(updates: Map<String, Any?>) {
        setLoading(true)
        FirebaseManager.database.reference.updateChildren(updates)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(context, "Profile and Property updated!", Toast.LENGTH_SHORT).show()
                onUpdateSuccess()
                dismiss()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(context, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _tenantBinding = null
        _landlordBinding = null
    }
}
