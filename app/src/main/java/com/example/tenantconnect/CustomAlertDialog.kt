package com.example.tenantconnect

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.tenantconnect.databinding.DialogCustomAlertBinding

class CustomAlertDialog : DialogFragment() {
    private var _binding: DialogCustomAlertBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"

        fun newInstance(title: String, message: String): CustomAlertDialog {
            val fragment = CustomAlertDialog()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            args.putString(ARG_MESSAGE, message)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCustomAlertBinding.inflate(LayoutInflater.from(context))
        
        binding.tvAlertTitle.text = arguments?.getString(ARG_TITLE)
        binding.tvAlertMessage.text = arguments?.getString(ARG_MESSAGE)
        
        binding.btnAlertOk.setOnClickListener {
            dismiss()
        }

        return AlertDialog.Builder(requireContext(), R.style.TransparentDialog)
            .setView(binding.root)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
