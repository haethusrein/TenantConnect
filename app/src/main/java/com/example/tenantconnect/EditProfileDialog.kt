package com.example.tenantconnect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import java.io.ByteArrayOutputStream

class EditProfileDialog(
    private val user: User,
    private val property: Property? = null,
    private val onUpdateSuccess: () -> Unit
) : BottomSheetDialogFragment() {

    private var _tenantBinding: DialogEditProfileTenantBinding? = null
    private var _landlordBinding: DialogEditProfileLandlordBinding? = null
    private var selectedImageUri: Uri? = null
    private var selectedQrUri: Uri? = null

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedImageUri = it
            if (user.role == "Landlord") {
                _landlordBinding?.ivProfilePreview?.load(it)
            } else {
                _tenantBinding?.ivProfilePreview?.load(it)
            }
        }
    }

    private val selectQrLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedQrUri = it
            _landlordBinding?.ivQrPreview?.load(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
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
        if (user.role == "Landlord") setupLandlordEdit() else setupTenantEdit()
    }

    private fun setupTenantEdit() {
        val b = _tenantBinding!!
        b.etOccupation.setText(user.occupation)
        b.etAddress.setText(user.originalAddress)
        b.etCivilStatus.setText(user.civilStatus)
        ImageUtils.loadImage(b.ivProfilePreview, user.profilePhotoUrl)

        b.btnChangePhoto.setOnClickListener {
            selectImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        b.btnSave.setOnClickListener {
            val uid = user.userId ?: return@setOnClickListener
            val updates = hashMapOf<String, Any?>(
                "occupation" to b.etOccupation.text.toString().trim(),
                "originalAddress" to b.etAddress.text.toString().trim(),
                "civilStatus" to b.etCivilStatus.text.toString().trim()
            )

            if (selectedImageUri != null) {
                processImageToBase64(selectedImageUri!!, 300) { base64 ->
                    if (base64 != null) {
                        updates["profilePhotoUrl"] = base64
                    }
                    saveUpdates(uid, updates)
                }
            } else {
                saveUpdates(uid, updates)
            }
        }
        b.btnCancel.setOnClickListener { dismiss() }
    }

    private fun setupLandlordEdit() {
        val b = _landlordBinding!!
        b.etOccupation.setText(user.occupation)
        b.etPersonalAddress.setText(user.originalAddress)
        b.etCivilStatus.setText(user.civilStatus)
        ImageUtils.loadImage(b.ivProfilePreview, user.profilePhotoUrl)
        
        b.etPropertyName.setText(property?.propertyName)
        b.etPropertyAddress.setText(property?.address)
        b.etTotalUnits.setText(property?.totalRooms?.toString())
        ImageUtils.loadImage(b.ivQrPreview, property?.coverPhotoUrl, android.R.drawable.ic_menu_gallery)

        b.btnChangePhoto.setOnClickListener {
            selectImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        b.btnChangeQr.setOnClickListener {
            selectQrLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        b.btnSave.setOnClickListener {
            val uid = user.userId ?: return@setOnClickListener
            val propId = property?.propertyId ?: return@setOnClickListener
            val updates = hashMapOf<String, Any?>()
            
            updates["users/$uid/occupation"] = b.etOccupation.text.toString().trim()
            updates["users/$uid/originalAddress"] = b.etPersonalAddress.text.toString().trim()
            updates["users/$uid/civilStatus"] = b.etCivilStatus.text.toString().trim()
            updates["properties/$propId/propertyName"] = b.etPropertyName.text.toString().trim()
            updates["properties/$propId/address"] = b.etPropertyAddress.text.toString().trim()
            updates["properties/$propId/totalRooms"] = b.etTotalUnits.text.toString().trim().toIntOrNull() ?: 0

            processLandlordImages(uid, propId, updates)
        }
        b.btnCancel.setOnClickListener { dismiss() }
    }

    private fun processLandlordImages(uid: String, propId: String, updates: HashMap<String, Any?>) {
        setLoading(true)
        var pDone = selectedImageUri == null
        var qDone = selectedQrUri == null
        var hasError = false

        fun check() { 
            if (hasError) return
            if (pDone && qDone) saveUpdates("", updates, true) 
        }

        if (selectedImageUri != null) {
            processImageToBase64(selectedImageUri!!, 300) { 
                if (it != null) updates["users/$uid/profilePhotoUrl"] = it else hasError = true
                pDone = true
                check() 
            }
        }
        if (selectedQrUri != null) {
            processImageToBase64(selectedQrUri!!, 500) { 
                if (it != null) updates["properties/$propId/coverPhotoUrl"] = it else hasError = true
                qDone = true
                check() 
            }
        }
        if (pDone && qDone) check()
    }

    private fun processImageToBase64(uri: Uri, maxSide: Int, callback: (String?) -> Unit) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (original == null) {
                return callback(null)
            }

            val width = original.width
            val height = original.height
            val ratio = width.toFloat() / height.toFloat()
            
            var newWidth = maxSide
            var newHeight = maxSide
            
            if (width > height) {
                newHeight = (maxSide / ratio).toInt()
            } else {
                newWidth = (maxSide * ratio).toInt()
            }

            val scaled = Bitmap.createScaledBitmap(original, newWidth, newHeight, true)
            val out = ByteArrayOutputStream()
            // Increased quality to 80 to prevent white-screen issues
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val bytes = out.toByteArray()
            
            val base64 = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            callback(base64)
        } catch (e: Exception) {
            callback(null)
        }
    }

    private fun saveUpdates(uid: String, updates: Map<String, Any?>, isLandlord: Boolean = false) {
        setLoading(true)
        val ref = if (isLandlord) FirebaseManager.database.reference else FirebaseManager.usersRef.child(uid)
        ref.updateChildren(updates).addOnSuccessListener {
            setLoading(false)
            Toast.makeText(context, "Update successful!", Toast.LENGTH_SHORT).show()
            onUpdateSuccess()
            dismiss()
        }.addOnFailureListener { 
            setLoading(false)
        }
    }

    private fun setLoading(load: Boolean) {
        val pb = if (user.role == "Landlord") _landlordBinding?.progressBar else _tenantBinding?.progressBar
        val btn = if (user.role == "Landlord") _landlordBinding?.btnSave else _tenantBinding?.btnSave
        pb?.visibility = if (load) View.VISIBLE else View.GONE
        btn?.isEnabled = !load
        isCancelable = !load
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _tenantBinding = null
        _landlordBinding = null
    }
}
