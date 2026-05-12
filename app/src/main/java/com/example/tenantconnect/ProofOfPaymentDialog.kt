package com.example.tenantconnect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.tenantconnect.databinding.DialogViewProofBinding

class ProofOfPaymentDialog(
    private val imageUrl: String?,
    private val referenceNumber: String?
) : BottomSheetDialogFragment() {

    private var _binding: DialogViewProofBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogViewProofBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ImageUtils.loadImage(binding.ivProofImage, imageUrl, android.R.drawable.ic_menu_gallery)
        binding.tvReferenceNumber.text = "Ref: ${referenceNumber ?: "N/A"}"

        binding.btnClose.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}