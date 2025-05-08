package com.gawasu.sillyn.ui.dialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.DialogAddTaskBinding
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.fragment.TaskFragment // Import TaskFragment to access its public constants
// Không cần FirebaseAuth ở đây nếu không fetch categories từ dialog
// import com.google.firebase.auth.FirebaseAuth

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddTaskDialogFragment : DialogFragment() {

    private var _binding: DialogAddTaskBinding? = null
    private val binding get() = _binding!!

    // State variables to hold selected values
    private var selectedPriority: Task.Priority = Task.Priority.NONE
    private var selectedCategory: String? = null
    private var selectedDueDate: Date? = null
    private var selectedDueTime: Calendar? = null
    private var selectedRepeatMode: Task.RepeatMode = Task.RepeatMode.NONE // Default to NONE
    // Lưu string label được chọn từ prototype, sau đó map sang string tiêu chuẩn cho Task object
    private var selectedReminderLabel: String = ""

    // Variable to hold Task ID if we are editing
    private var taskIdToEdit: String? = null

    // Danh sách category có sẵn (sẽ được nhận qua arguments)
    private var availableCategories: List<String> = emptyList()


    // Factory methods
    companion object {
        const val TAG = "AddTaskDialog"
        private const val ARG_TASK_TO_EDIT = "task_to_edit"
        private const val ARG_DEFAULT_CATEGORY_NAME = "default_category_name"
        private const val ARG_AVAILABLE_CATEGORIES = "available_categories" // Argument key for categories

        /**
         * Factory method to create a new dialog for editing an existing task.
         * @param taskToEdit The task object to edit.
         * @param availableCategories List of existing category names.
         */
        fun newInstance(taskToEdit: Task, availableCategories: List<String>): AddTaskDialogFragment {
            val fragment = AddTaskDialogFragment()
            val args = Bundle().apply {
                putParcelable(ARG_TASK_TO_EDIT, taskToEdit)
                putStringArrayList(ARG_AVAILABLE_CATEGORIES, ArrayList(availableCategories))
            }
            fragment.arguments = args
            return fragment
        }

        /**
         * Factory method to create a new dialog for adding a new task, with an optional default category.
         * @param defaultCategoryName Optional default category name.
         * @param availableCategories List of existing category names.
         */
        fun newInstance(defaultCategoryName: String?, availableCategories: List<String>): AddTaskDialogFragment {
            val fragment = AddTaskDialogFragment()
            val args = Bundle().apply {
                putString(ARG_DEFAULT_CATEGORY_NAME, defaultCategoryName)
                putStringArrayList(ARG_AVAILABLE_CATEGORIES, ArrayList(availableCategories))
            }
            fragment.arguments = args
            return fragment
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get available categories from arguments
        availableCategories = arguments?.getStringArrayList(ARG_AVAILABLE_CATEGORIES) ?: emptyList()
        Log.d(TAG, "Received ${availableCategories.size} available categories")


        // Check arguments for editing or default category
        arguments?.let {
            val taskToEdit: Task? = BundleCompat.getParcelable(it, ARG_TASK_TO_EDIT, Task::class.java)
            if (taskToEdit != null) {
                loadTaskForEditing(taskToEdit)
            } else {
                val defaultCategoryName = it.getString(ARG_DEFAULT_CATEGORY_NAME)
                if (defaultCategoryName != null) {
                    selectedCategory = defaultCategoryName
                }
            }
        }

        setupClickListeners()

        // Initial UI update based on default values or loaded task
        updatePriorityButtonIcon()
        updateCategoryButtonText()
        updateDueDateButtonText()
        updateDueTimeButtonText()

        // Initialize selectedReminderLabel and update UI
        if (selectedReminderLabel.isBlank()) {
            selectedReminderLabel = getString(R.string.reminder_ontime) // Default label
        }
        updateReminderTypeButtonTextAndIcon()

        // TODO: updateRepeatModeButtonText()

        // Set up cancel button listener
        // Make sure your dialog_add_task.xml has a button with id button_cancel if you want this
//        binding.buttonCancel?.setOnClickListener {
//            dismiss()
//        }
    }

    // Load data if editing an existing task
    private fun loadTaskForEditing(task: Task) {
        taskIdToEdit = task.id

        binding.editTextTaskTitle.setText(task.title)
        binding.editTextTaskDescription.setText(task.description)

        selectedPriority = try { Task.Priority.valueOf(task.priority) } catch (e: IllegalArgumentException) { Task.Priority.NONE }
        selectedCategory = task.category

        selectedDueDate = task.dueDate
        selectedDueDate?.let {
            val calendar = Calendar.getInstance().apply { time = it }
            // Check if time component is non-midnight
            if (it.time % (24 * 60 * 60 * 1000) != 0L) {
                selectedDueTime = calendar
            } else {
                selectedDueTime = null // Clear time if it was midnight
            }
        }

        // Load reminder type (Needs mapping from Task.reminderType string to prototype labels)
        // Assuming Task.reminderType stores the standard string ("ON_TIME", "EARLY_30M", etc.)
        selectedReminderLabel = when(task.reminderType) {
            "ON_TIME" -> getString(R.string.reminder_ontime)
            "EARLY_30M" -> getString(R.string.reminder_early_30m)
            "EARLY_1H" -> getString(R.string.reminder_early_1h)
            "EARLY_3H" -> getString(R.string.reminder_early_3h)
            "EARLY_1D" -> getString(R.string.reminder_early_1d)
            else -> getString(R.string.reminder_ontime) // Default if value is not recognized
        }
        // selectedReminderEnum is implicitly determined by the label later

        // TODO: Load Repeat Mode, Tags, Status, Type if UI supports

        // Change button UI for editing mode
        binding.buttonAddTask.contentDescription = getString(R.string.update_task)
        binding.buttonAddTask.setImageResource(R.drawable.baseline_check_box_outline_blank_24) // Need ic_check_24 icon
    }


    private fun setupClickListeners() {
        binding.buttonPriority.setOnClickListener { showPriorityPicker() }
        binding.buttonCategory.setOnClickListener { showCategoryPicker() }
        binding.buttonDueDate.setOnClickListener { showDatePicker() }
        binding.buttonDueTime.setOnClickListener { showTimePicker() }
        binding.buttonReminderType.setOnClickListener { showReminderTypePicker() }
        // binding.buttonRepeat.setOnClickListener { showRepeatModePicker() }

        binding.buttonAddTask.setOnClickListener { handleSaveTaskClick() }
    }

    //region // --- Show Picker Dialogs ---

    private fun showPriorityPicker() {
        val popupMenu = PopupMenu(requireContext(), binding.buttonPriority)
        popupMenu.menuInflater.inflate(R.menu.menu_priority, popupMenu.menu) // menu_priority.xml

        try { // Force icons to show
            val fields = popupMenu.javaClass.declaredFields
            for (field in fields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popupMenu)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popupMenu.setOnMenuItemClickListener { item ->
            selectedPriority = when (item.itemId) {
                R.id.priority_none -> Task.Priority.NONE
                R.id.priority_low -> Task.Priority.LOW
                R.id.priority_medium -> Task.Priority.MEDIUM
                R.id.priority_high -> Task.Priority.HIGH
                else -> Task.Priority.NONE
            }
            updatePriorityButtonIcon()
            true
        }
        popupMenu.show()
    }


    private fun showCategoryPicker() {
        // Use the available categories received via arguments
        val categories = availableCategories.toMutableList() // Use a mutable copy
        // Add "No Category" option at the top
        categories.add(0, getString(R.string.none))

        val categoryOptions = categories.toTypedArray()
        val selectedIndex = categoryOptions.indexOf(selectedCategory ?: getString(R.string.none))


        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.select_category))
            .setSingleChoiceItems(categoryOptions, selectedIndex) { dialog, which ->
                selectedCategory = if (which == 0) null else categoryOptions[which]
                updateCategoryButtonText()
                dialog.dismiss()
            }
            // "Add New Category" button is a separate action, show input dialog
            .setPositiveButton(getString(R.string.add)) { dialog, which ->
                showAddNewCategoryDialog() // Show input dialog
            }
            .setNegativeButton(getString(R.string.cancel), null)
        builder.create().show()
    }

    private fun showAddNewCategoryDialog() {
        val input = EditText(requireContext())
        input.hint = getString(R.string.hint_new_category)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_new_category))
            .setView(input)
            .setPositiveButton(getString(R.string.add)) { dialog, _ ->
                val newCategory = input.text.toString().trim()
                if (newCategory.isNotBlank()) {
                    // For now, just select the new category in the picker UI
                    // It will be saved with the task.
                    selectedCategory = newCategory
                    updateCategoryButtonText()
                    Toast.makeText(requireContext(), "Danh mục '$newCategory' đã chọn. Sẽ lưu khi thêm task.", Toast.LENGTH_SHORT).show()
                    // Optionally, you could re-open the main category picker dialog here
                    // after selecting the new category.
                } else {
                    Toast.makeText(requireContext(), "Tên danh mục không được để trống", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }


    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        if (selectedDueDate != null) { calendar.time = selectedDueDate!! }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                selectedCalendar.set(Calendar.HOUR_OF_DAY, 0) // Clear time components initially
                selectedCalendar.set(Calendar.MINUTE, 0)
                selectedCalendar.set(Calendar.SECOND, 0)
                selectedCalendar.set(Calendar.MILLISECOND, 0)

                selectedDueDate = selectedCalendar.time

                if (selectedDueTime != null) { // Combine date with previously selected time
                    val combinedCalendar = Calendar.getInstance().apply { time = selectedDueDate!! }
                    combinedCalendar.set(Calendar.HOUR_OF_DAY, selectedDueTime!!.get(Calendar.HOUR_OF_DAY))
                    combinedCalendar.set(Calendar.MINUTE, selectedDueTime!!.get(Calendar.MINUTE))
                    combinedCalendar.set(Calendar.SECOND, selectedDueTime!!.get(Calendar.SECOND)) // Keep original time seconds
                    combinedCalendar.set(Calendar.MILLISECOND, selectedDueTime!!.get(Calendar.MILLISECOND)) // Keep original time millis
                    selectedDueDate = combinedCalendar.time
                }

                updateDueDateButtonText()
                Log.d(TAG, "Selected Date: ${selectedDueDate}")
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Optional: Set min/max date
        // datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000 // Prevent selecting past dates
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val initialHour = selectedDueTime?.get(Calendar.HOUR_OF_DAY) ?: calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = selectedDueTime?.get(Calendar.MINUTE) ?: calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                val selectedCalendar = selectedDueTime ?: selectedDueDate?.let { Calendar.getInstance().apply { time = it } } ?: Calendar.getInstance()
                selectedCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                selectedCalendar.set(Calendar.MINUTE, selectedMinute)
                selectedCalendar.set(Calendar.SECOND, 0) // Clear seconds/milliseconds
                selectedCalendar.set(Calendar.MILLISECOND, 0)

                selectedDueTime = selectedCalendar
                selectedDueDate = selectedCalendar.time // Update selectedDueDate with time component

                updateDueTimeButtonText()
                updateDueDateButtonText() // Update date text too
                Log.d(TAG, "Selected Time: ${selectedDueTime?.time}")
            },
            initialHour,
            initialMinute,
            true // 24-hour format
        )
        timePickerDialog.show()
    }

    private fun showReminderTypePicker() {
        val prototypeReminderOptions = arrayOf(
            getString(R.string.reminder_ontime),
            getString(R.string.reminder_early_30m),
            getString(R.string.reminder_early_1h),
            getString(R.string.reminder_early_3h),
            getString(R.string.reminder_early_1d)
        )

        val selectedIndex = prototypeReminderOptions.indexOf(selectedReminderLabel)


        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.select_reminder_type))
            .setSingleChoiceItems(prototypeReminderOptions, selectedIndex) { dialog, which ->
                selectedReminderLabel = prototypeReminderOptions[which] // Store the selected string label
                updateReminderTypeButtonTextAndIcon()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
        builder.create().show()
    }

    private fun showRepeatModePicker() {
        // TODO: Implement Repeat Mode Picker Dialog/Fragment
        Log.d(TAG, "Show Repeat Mode Picker (Not implemented yet)")
        Toast.makeText(requireContext(), "Tính năng lặp lại chưa triển khai", Toast.LENGTH_SHORT).show()
    }

    //endregion

    //region // --- Update UI based on selections ---

    private fun updatePriorityButtonIcon() {
        val iconResId = when (selectedPriority) {
            Task.Priority.NONE -> R.drawable.flag_svgrepo_com_none // Ensure these drawable names are correct
            Task.Priority.LOW -> R.drawable.flag_svgrepo_com_low
            Task.Priority.MEDIUM -> R.drawable.flag_svgrepo_com_medium
            Task.Priority.HIGH -> R.drawable.flag_svgrepo_com_high
        }
        binding.buttonPriority.setImageResource(iconResId)
        binding.buttonPriority.imageTintList = null // Reset tint from PopupMenu if any
    }

    private fun updateCategoryButtonText() {
        binding.buttonCategory.text = selectedCategory ?: getString(R.string.select_category)
    }

    private fun updateDueDateButtonText() {
        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        if (selectedDueDate != null) {
            binding.buttonDueDate.contentDescription = dateFormat.format(selectedDueDate)
        } else {
            binding.buttonDueDate.contentDescription = getString(R.string.select_due_date)
        }
    }

    private fun updateDueTimeButtonText() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (selectedDueTime != null) {
            binding.buttonDueTime.contentDescription = timeFormat.format(selectedDueTime!!.time)
        } else {
            binding.buttonDueTime.contentDescription = getString(R.string.select_due_time)
        }
    }

    private fun updateReminderTypeButtonTextAndIcon() {
        // Determine icon based on whether it's "On Time" or any "Early" type
        val iconResId = when (selectedReminderLabel) {
            getString(R.string.reminder_ontime) -> R.drawable.bell_svgrepo_com // Default alarm icon
            else -> R.drawable.rounded_circle_24 // Use a different icon for set reminder? Need this icon.
        }
        binding.buttonReminderType.setImageResource(iconResId)
        binding.buttonReminderType.contentDescription = selectedReminderLabel.ifBlank { getString(R.string.select_reminder_type) }
    }

    // TODO: Add updateRepeatModeButtonText()

    //endregion


    // --- Handle Save Click and Send Result ---
    private fun handleSaveTaskClick() {
        val title = binding.editTextTaskTitle.text.toString().trim()
        val description = binding.editTextTaskDescription.text.toString().trim()

        if (title.isBlank()) {
            binding.editTextTaskTitle.error = getString(R.string.title_cannot_be_empty)
            return
        }

        val finalDueDate = if (selectedDueDate != null) {
            val calendar = Calendar.getInstance().apply { time = selectedDueDate!! }
            if (selectedDueTime != null) {
                calendar.set(Calendar.HOUR_OF_DAY, selectedDueTime!!.get(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, selectedDueTime!!.get(Calendar.MINUTE))
                calendar.set(Calendar.SECOND, selectedDueTime!!.get(Calendar.SECOND)) // Keep original time seconds
                calendar.set(Calendar.MILLISECOND, selectedDueTime!!.get(Calendar.MILLISECOND)) // Keep original time millis
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            calendar.time
        } else {
            null
        }

        val standardReminderType = when (selectedReminderLabel) {
            getString(R.string.reminder_ontime) -> "ON_TIME"
            getString(R.string.reminder_early_30m) -> "EARLY_30M"
            getString(R.string.reminder_early_1h) -> "EARLY_1H"
            getString(R.string.reminder_early_3h) -> "EARLY_3H"
            getString(R.string.reminder_early_1d) -> "EARLY_1D"
            else -> "ON_TIME" // Default if label is not recognized
        }

        // Create or update the Task object
        val task = Task(
            id = taskIdToEdit,
            title = title,
            description = description.ifBlank { null },
            priority = selectedPriority.name,
            category = selectedCategory,
            // tags = null, // TODO: Add UI for tags
            dueDate = finalDueDate,
            repeatMode = selectedRepeatMode.name, // Will be "NONE" if not implemented
            reminderType = standardReminderType, // Store the standard string
            status = Task.TaskStatus.PENDING.name, // Assume pending unless loading an existing completed task
            type = Task.TaskType.TASK.name // Assuming it's always a task
        )

        // Determine the request key based on whether we are adding or editing
        val requestKey = if (task.id != null) TaskFragment.REQUEST_KEY_EDIT_TASK else TaskFragment.REQUEST_KEY_ADD_TASK

        // Send the new/updated task back to the calling Fragment (TaskFragment)
        val resultBundle = Bundle().apply {
            putParcelable(TaskFragment.BUNDLE_KEY_TASK, task)
        }

        setFragmentResult(requestKey, resultBundle) // Use the appropriate request key
        childFragmentManager.setFragmentResult(requestKey, resultBundle)
        dismiss() // Close the dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}