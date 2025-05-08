package com.gawasu.sillyn.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView // Import TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult // Import setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gawasu.sillyn.R
import com.gawasu.sillyn.ui.adapter.CategoryAdapter // You'll need to create this adapter
import com.gawasu.sillyn.databinding.DialogCategoryPickerBinding // Make sure this matches the layout name

// Assume you have a simple CategoryAdapter that takes a list of strings and a click listener
// And a simple item_category.xml layout

class CategoryDialogFragment : DialogFragment(), CategoryAdapter.OnItemClickListener {

    private var _binding: DialogCategoryPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var editTextNewCategory: EditText
    private lateinit var buttonAddNewCategory: Button

    // Args
    private var existingCategories: List<String>? = null
    private var initialSelectedCategory: String? = null

    companion object {
        const val TAG = "CategoryDialog"
        const val REQUEST_KEY_CATEGORY = "request_key_category"
        const val BUNDLE_KEY_CATEGORY = "bundle_key_category"
        private const val ARG_CATEGORIES = "arg_categories"
        private const val ARG_SELECTED_CATEGORY = "arg_selected_category"


        fun newInstance(categories: List<String>, selectedCategory: String?): CategoryDialogFragment {
            val fragment = CategoryDialogFragment()
            val args = Bundle().apply {
                putStringArrayList(ARG_CATEGORIES, ArrayList(categories)) // Need ArrayList
                putString(ARG_SELECTED_CATEGORY, selectedCategory)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCategoryPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editTextNewCategory = binding.editTextNewCategory
        buttonAddNewCategory = binding.buttonAddNewCategory

        // Get arguments
        arguments?.let {
            existingCategories = it.getStringArrayList(ARG_CATEGORIES)
            initialSelectedCategory = it.getString(ARG_SELECTED_CATEGORY)
        }

        setupRecyclerView()
        setupClickListeners()

        // TODO: Observe categories from ViewModel instead of getting arguments directly
        // This dialog might need a ViewModel (shared with AddTaskDialogFragment or Activity)
        // to observe category updates and handle adding new categories.
    }

    private fun setupRecyclerView() {
        val categoriesToDisplay = existingCategories ?: emptyList()
        categoryAdapter = CategoryAdapter(categoriesToDisplay, this) // Pass listener
        binding.recyclerviewCategories.apply {
            adapter = categoryAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        // Optional: Highlight initialSelectedCategory in the list
        // categoryAdapter.setSelectedItem(initialSelectedCategory)
    }

    private fun setupClickListeners() {
        buttonAddNewCategory.setOnClickListener {
            val newCategory = editTextNewCategory.text.toString().trim()
            if (newCategory.isNotBlank()) {
                // TODO: Call ViewModel to add new category to backend
                // For now, just treat it as the selected category
                sendResultAndDismiss(newCategory)
            } else {
                Toast.makeText(requireContext(), "Category name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }
        // Handle item clicks in Adapter (handled by onItemClick interface)
    }

    // Implementation of CategoryAdapter.OnItemClickListener
    override fun onItemClick(category: String) {
        // When an existing category is clicked, send it back and dismiss
        sendResultAndDismiss(category)
    }

    private fun sendResultAndDismiss(selectedCategoryName: String) {
        val resultBundle = Bundle().apply {
            putString(BUNDLE_KEY_CATEGORY, selectedCategoryName)
        }
        setFragmentResult(REQUEST_KEY_CATEGORY, resultBundle)
        dismiss() // Close the dialog
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Optional: Make dialog fill width
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}