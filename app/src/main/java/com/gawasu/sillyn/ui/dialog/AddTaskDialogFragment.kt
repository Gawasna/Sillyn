package com.gawasu.sillyn.ui.dialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.PorterDuff // Required for tinting if used
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat // Required for tinting
import androidx.core.os.BundleCompat
import androidx.core.widget.doAfterTextChanged // Use doAfterTextChanged for Flows
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope // Required for lifecycleScope
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.DialogAddTaskBinding
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.fragment.TaskFragment // Import TaskFragment to access its public constants
import com.gawasu.sillyn.utils.ParseHelper // Import ParseHelper
import com.gawasu.sillyn.utils.ParsedEntities // Import ParsedEntities
import com.google.android.material.snackbar.Snackbar // Import Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.launch // Import launch for coroutines
// import com.google.firebase.auth.FirebaseAuth // Không cần FirebaseAuth ở đây nếu không fetch categories từ dialog

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
    private var selectedDueTime: Calendar? = null // Calendar to hold time component
    private var selectedRepeatMode: Task.RepeatMode = Task.RepeatMode.NONE // Default to NONE
    private var selectedReminderLabel: String = "" // Store string label from prototype


    // Variable to hold Task ID if we are editing
    private var taskIdToEdit: String? = null

    // Danh sách category có sẵn (sẽ được nhận qua arguments)
    private var availableCategories: List<String> = emptyList()

    // Parse Helper and Coroutine Job for Debouncing
    private lateinit var parseHelper: ParseHelper
    // Use MutableStateFlow to hold current text in EditTexts
    private val titleTextFlow = MutableStateFlow("")
    private val descriptionTextFlow = MutableStateFlow("")
    private var parseJob: Job? = null


    // Factory methods
    companion object {
        const val TAG = "AddTaskDialog"
        private const val ARG_TASK_TO_EDIT = "task_to_edit"
        private const val ARG_DEFAULT_CATEGORY_NAME = "default_category_name"
        private const val ARG_AVAILABLE_CATEGORIES = "available_categories" // Argument key for categories

        /**
         * Factory method to create a new dialog for editing an existing task.
         * NOTE: Auto-parsing is NOT enabled in Edit mode per requirements.
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
         * Auto-parsing IS enabled in Add mode.
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
        Log.d(TAG, "onCreateView")
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        Log.d(TAG, "onStart")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        // Get available categories from arguments
        availableCategories = arguments?.getStringArrayList(ARG_AVAILABLE_CATEGORIES) ?: emptyList()
        Log.d(TAG, "Received ${availableCategories.size} available categories: $availableCategories")

        // Initialize ParseHelper (will start ML Kit model loading)
        parseHelper = ParseHelper(requireContext())
        Log.d(TAG, "ParseHelper initialized.")

        // Check arguments for editing or default category
        arguments?.let {
            val taskToEdit: Task? = BundleCompat.getParcelable(it, ARG_TASK_TO_EDIT, Task::class.java)
            if (taskToEdit != null) {
                loadTaskForEditing(taskToEdit)
                // Disable auto-parsing in edit mode
                Log.d(TAG, "Edit mode (${taskToEdit.id}), auto-parsing disabled.")
            } else {
                // --- ADD MODE ---
                Log.d(TAG, "Add mode, auto-parsing enabled.")
                val defaultCategoryName = it.getString(ARG_DEFAULT_CATEGORY_NAME)
                if (defaultCategoryName != null) {
                    selectedCategory = defaultCategoryName
                    Log.d(TAG, "Default category set from args: $selectedCategory")
                }
                setupTextChangeListenersForParsing() // Setup parsing only in Add mode
            }
        }

        setupClickListeners()

        // Initial UI update based on default values or loaded task
        updatePriorityButtonIcon()
        updateCategoryButtonText()
        updateDueDateButtonText()
        updateDueTimeButtonText()
        Log.d(TAG, "Initial UI updated.")

        // Initialize selectedReminderLabel and update UI
        if (selectedReminderLabel.isBlank()) {
            selectedReminderLabel = getString(R.string.reminder_ontime) // Default label
            Log.d(TAG, "Reminder label defaulted to: $selectedReminderLabel")
        }
        updateReminderTypeButtonTextAndIcon()

        // TODO: updateRepeatModeButtonText()

        // Set initial values for Flow. This is crucial to trigger the combine
        // and potentially an initial parse if EditTexts are pre-filled (e.g., in edit mode, though parse is disabled).
        // In add mode, they are initially empty, so flows will start with "".
        titleTextFlow.value = binding.editTextTaskTitle.text?.toString() ?: ""
        descriptionTextFlow.value = binding.editTextTaskDescription.text?.toString() ?: ""
        Log.d(TAG, "Initial flow values set: title='${titleTextFlow.value}', description='${descriptionTextFlow.value}'")

        // Manually trigger parse if in Add mode and model is ready (handles case where model is ready immediately)
        if (taskIdToEdit == null && parseHelper.isModelDownloaded()) {
            Log.d(TAG, "Model ready on creation, triggering initial parse.")
            // Use current text, as flow might not have emitted yet depending on lifecycle
            val currentTitle = binding.editTextTaskTitle.text?.toString() ?: ""
            val currentDescription = binding.editTextTaskDescription.text?.toString() ?: ""
            if (currentTitle.isNotBlank() || currentDescription.isNotBlank()) {
                parseAndApply(currentTitle, currentDescription)
            }
        } else if (taskIdToEdit == null && parseHelper.isModelDownloadInProgress()) {
            Log.d(TAG, "Model downloading on creation.")
        } else if (taskIdToEdit == null) {
            Log.d(TAG, "Model not ready on creation, parse will trigger after download or text change.")
        }

    }

    // Load data if editing an existing task
    private fun loadTaskForEditing(task: Task) {
        taskIdToEdit = task.id
        Log.d(TAG, "Loading task for editing: ${task.id}")

        binding.editTextTaskTitle.setText(task.title)
        binding.editTextTaskDescription.setText(task.description)

        selectedPriority = try { Task.Priority.valueOf(task.priority) } catch (e: IllegalArgumentException) { Task.Priority.NONE }
        Log.d(TAG, "Loaded Priority: $selectedPriority")
        selectedCategory = task.category
        Log.d(TAG, "Loaded Category: $selectedCategory")


        selectedDueDate = task.dueDate
        selectedDueDate?.let {
            val calendar = Calendar.getInstance().apply { time = it }
            // Check if time component is non-midnight
            if (calendar.get(Calendar.HOUR_OF_DAY) != 0 || calendar.get(Calendar.MINUTE) != 0 || calendar.get(Calendar.SECOND) != 0 || calendar.get(Calendar.MILLISECOND) != 0) {
                selectedDueTime = calendar
                Log.d(TAG, "Loaded Due Date/Time: ${it} (with time)")
            } else {
                selectedDueTime = null // Clear time if it was midnight (date-only)
                Log.d(TAG, "Loaded Due Date: ${it} (date only)")
            }
        } ?: Log.d(TAG, "Loaded Due Date: null")


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
        Log.d(TAG, "Loaded Reminder Label: $selectedReminderLabel")

        // TODO: Load Repeat Mode, Tags, Status, Type if UI supports

        // Change button UI for editing mode
        binding.buttonAddTask.contentDescription = getString(R.string.update_task)
        binding.buttonAddTask.setImageResource(R.drawable.baseline_check_box_outline_blank_24) // Need ic_check_24 icon - assuming this is the 'check' icon
    }


    private fun setupClickListeners() {
        Log.d(TAG, "Setting up click listeners.")
        binding.buttonPriority.setOnClickListener { showPriorityPicker() }
        binding.buttonCategory.setOnClickListener { showCategoryPicker() }
        binding.buttonDueDate.setOnClickListener { showDatePicker() }
        binding.buttonDueTime.setOnClickListener { showTimePicker() }
        binding.buttonReminderType.setOnClickListener { showReminderTypePicker() }
        // binding.buttonRepeat.setOnClickListener { showRepeatModePicker() }

        binding.buttonAddTask.setOnClickListener { handleSaveTaskClick() }
    }

    // Setup text change listeners for auto-parsing (only in Add mode)
    private fun setupTextChangeListenersForParsing() {
        Log.d(TAG, "Setting up text change listeners for parsing.")

        // Manually update flows on text change. This is simpler than a custom flow extension.
        binding.editTextTaskTitle.doAfterTextChanged { editable ->
            titleTextFlow.value = editable?.toString() ?: ""
            Log.v(TAG, "Title text changed: ${titleTextFlow.value}")
        }
        binding.editTextTaskDescription.doAfterTextChanged { editable ->
            descriptionTextFlow.value = editable?.toString() ?: ""
            Log.v(TAG, "Description text changed: ${descriptionTextFlow.value}")
        }


        // Combine the flows from title and description EditTexts
        parseJob = combine(
            titleTextFlow,
            descriptionTextFlow
        ) { title, description ->
            // This combiner function runs whenever *either* flow emits a new value
            Pair(title, description)
        }
            .debounce(500) // Wait for 500ms of inactivity before parsing
            .onEach { (title, description) ->
                Log.d(TAG, "Debounce finished. Triggering parse for title='${title}', description='${description}'")
                // Only parse if model is ready, or if text is empty (for reset)
                if (parseHelper.isModelDownloaded() || (title.isBlank() && description.isBlank())) {
                    parseAndApply(title, description)
                } else {
                    // Optionally show a temporary message if model is still downloading
                    if (parseHelper.isModelDownloadInProgress()) {
                        Log.i(TAG, "Model is still downloading, skipping parse.")
                        // Consider a UI indicator here instead of a Snackbar
                    } else {
                        // This case should be rare if initialization is handled correctly, but logs it.
                        Log.w(TAG, "Model not downloaded and not downloading, skipping parse.")
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope) // Launch coroutine in the fragment's view lifecycle scope
    }

    // Removed the textChangesAsFlow extension as doAfterTextChanged with MutableStateFlow is cleaner


    private fun parseAndApply(title: String, description: String) {
        // Run parsing in a coroutine
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Starting parseAndApply for title='${title}', description='${description}'")
            val parsedEntities = parseHelper.parseText(title, description)
            Log.d(TAG, "ParseHelper returned: $parsedEntities")

            // --- Apply parsed results and update UI/State ---
            // Note: Apply one by one and show snackbar for each *if* they changed and are non-null

            // 1. Category
            val oldCategory = selectedCategory
            if (parsedEntities.category != null && parsedEntities.category != oldCategory) {
                selectedCategory = parsedEntities.category
                Log.d(TAG, "Category changed: '$oldCategory' -> '$selectedCategory'. Updating UI.")
                updateCategoryButtonText()
                showSnackbar("Đã phát hiện danh mục: ${selectedCategory}")
            } else if (parsedEntities.category == null && oldCategory != arguments?.getString(ARG_DEFAULT_CATEGORY_NAME) && taskIdToEdit == null) {
                // Reset category only if it was previously set (and wasn't the default) and parse now yields null
                selectedCategory = arguments?.getString(ARG_DEFAULT_CATEGORY_NAME)
                if (selectedCategory != oldCategory) {
                    Log.d(TAG, "Category reset: '$oldCategory' -> '$selectedCategory' (default). Updating UI.")
                    updateCategoryButtonText()
                } else {
                    Log.d(TAG, "Category reset logic triggered, but category is already default or null. No UI update needed.")
                }
                // No snackbar needed for reset
            } else {
                Log.d(TAG, "Category parse result: ${parsedEntities.category}. No change or cannot reset default. Current: '$oldCategory'")
            }


            // 2. Priority
            val oldPriority = selectedPriority
            if (parsedEntities.priority != Task.Priority.NONE && parsedEntities.priority != oldPriority) {
                selectedPriority = parsedEntities.priority
                Log.d(TAG, "Priority changed: '$oldPriority' -> '$selectedPriority'. Updating UI.")
                updatePriorityButtonIcon()
                showSnackbar("Đã phát hiện ưu tiên: ${selectedPriority.getLocalizedName()}") // Need localized name
            } else if (parsedEntities.priority == Task.Priority.NONE && oldPriority != Task.Priority.NONE) {
                // Reset priority only if it was previously set and parse now yields NONE
                selectedPriority = Task.Priority.NONE
                Log.d(TAG, "Priority reset: '$oldPriority' -> '$selectedPriority'. Updating UI.")
                updatePriorityButtonIcon()
                // No snackbar needed for reset
            } else {
                Log.d(TAG, "Priority parse result: ${parsedEntities.priority}. No change. Current: '$oldPriority'")
            }


            // 3. Date and Time
            val oldDueDate = selectedDueDate
            val oldDueTime = selectedDueTime

            // Handle parsed Date: Overwrites current date part
            // Handle parsed Time: Overwrites current time part
            // The ParseHelper now returns a combined timestamp in parsedDueDate and a Calendar in parsedDueTime
            // We should use parsedDueTime if available, as it contains both date and time information parsed by ML Kit
            // If only parsedDueDate is available, it means ML Kit found only a date (time is midnight).

            var appliedDueDate: Date? = null
            var appliedDueTime: Calendar? = null

            if (parsedEntities.dueTime != null) {
                // ML Kit found a DateTime or Time, use the calendar provided
                appliedDueTime = parsedEntities.dueTime
                appliedDueDate = parsedEntities.dueTime.time // Sync date part
                Log.d(TAG, "Applying parsed DueTime/DueDate from ML Kit Calendar.")
            } else if (parsedEntities.dueDate != null) {
                // ML Kit found only a Date (granularity < HOUR), time is midnight
                appliedDueDate = parsedEntities.dueDate
                appliedDueTime = null // Explicitly nullify time
                Log.d(TAG, "Applying parsed DueDate only from ML Kit (time is midnight).")
            } else {
                // No Date or Time parsed
                Log.d(TAG, "No Date or Time parsed by ML Kit.")
                // Keep null, the reset logic below handles it
            }


            // Apply updates if changed
            val dueDateChanged = !datesEqual(appliedDueDate, oldDueDate)
            val dueTimeChanged = !calendarsEqual(appliedDueTime, oldDueTime) // Compare Calendar instances


            if (dueDateChanged) {
                selectedDueDate = appliedDueDate
                Log.d(TAG, "DueDate changed. Updating UI. Old: ${oldDueDate}, New: ${selectedDueDate}")
                updateDueDateButtonText()
                if (selectedDueDate != null) {
                    showSnackbar("Đã phát hiện ngày đến hạn: ${SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(selectedDueDate!!)}")
                } else {
                    // Snackbar for date reset isn't explicitly requested, but could add one if needed
                    // showSnackbar("Ngày đến hạn đã được xóa.")
                }
            } else {
                Log.d(TAG, "DueDate did not change. Current: $selectedDueDate")
            }

            if (dueTimeChanged) {
                selectedDueTime = appliedDueTime
                Log.d(TAG, "DueTime changed. Updating UI. Old: ${oldDueTime?.time}, New: ${selectedDueTime?.time}")
                updateDueTimeButtonText()
                // Note: updateDueDateButtonText is also called if time changes, to show combined format
                updateDueDateButtonText()
                if (selectedDueTime != null) {
                    showSnackbar("Đã phát hiện thời gian: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedDueTime!!.time)}")
                } else {
                    // Snackbar for time reset isn't explicitly requested
                    // showSnackbar("Thời gian đến hạn đã được xóa.")
                }
            } else {
                Log.d(TAG, "DueTime did not change. Current: ${selectedDueTime?.time}")
            }

            // Handle case where text becomes empty and previously there was date/time
            if (title.isBlank() && description.isBlank() && (oldDueDate != null || oldDueTime != null)) {
                // Only reset if parse result was also null for date/time (handled by appliedDueDate/Time being null)
                if (appliedDueDate == null && appliedDueTime == null) {
                    selectedDueDate = null
                    selectedDueTime = null
                    Log.d(TAG, "Text is blank and no date/time parsed. Resetting DueDate and DueTime.")
                    updateDueDateButtonText()
                    updateDueTimeButtonText()
                }
            }

            Log.d(TAG, "parseAndApply finished. Current state: Category=${selectedCategory}, Priority=${selectedPriority}, DueDate=${selectedDueDate}, DueTime=${selectedDueTime?.time}")

        }
    }

    // Helper function for showing snackbar
    private fun showSnackbar(message: String) {
        // Ensure Snackbar is shown on the root view of the dialog for correct placement
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        Log.d(TAG, "Showing Snackbar: \"$message\"")
    }

    // Helper extension for Task.Priority to get localized name
    // Put this in a shared utils file or in Task.kt if preferred
    fun Task.Priority.getLocalizedName(): String {
        // Use requireContext() instead of context? for safety in onViewCreated scope
        return when (this) {
            Task.Priority.NONE -> requireContext().getString(R.string.priority_none_text)
            Task.Priority.LOW -> requireContext().getString(R.string.priority_low_text)
            Task.Priority.MEDIUM -> requireContext().getString(R.string.priority_medium_text)
            Task.Priority.HIGH -> requireContext().getString(R.string.priority_high_text)
        }
    }


    //region // --- Show Picker Dialogs --- (Existing code - Unchanged)
    // ... (showPriorityPicker, showCategoryPicker, showAddNewCategoryDialog, showDatePicker, showTimePicker, showReminderTypePicker, showRepeatModePicker) ...
    private fun showPriorityPicker() {
        Log.d(TAG, "Showing priority picker.")
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
            Log.e(TAG, "Error forcing icons in priority menu", e)
        }

        popupMenu.setOnMenuItemClickListener { item ->
            selectedPriority = when (item.itemId) {
                R.id.priority_none -> Task.Priority.NONE
                R.id.priority_low -> Task.Priority.LOW
                R.id.priority_medium -> Task.Priority.MEDIUM
                R.id.priority_high -> Task.Priority.HIGH
                else -> Task.Priority.NONE
            }
            Log.d(TAG, "Priority selected from picker: $selectedPriority")
            updatePriorityButtonIcon()
            true
        }
        popupMenu.show()
    }


    private fun showCategoryPicker() {
        Log.d(TAG, "Showing category picker. Available: $availableCategories, Selected: $selectedCategory")
        val categories = availableCategories.toMutableList()
        categories.add(0, getString(R.string.none)) // Add "No Category" option

        val categoryOptions = categories.toTypedArray()
        // Find the current selection index, defaulting to "No Category" if selectedCategory is null or not in the list
        val selectedIndex = if (selectedCategory == null) 0 else categoryOptions.indexOf(selectedCategory).takeIf { it != -1 } ?: 0


        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.select_category))
            .setSingleChoiceItems(categoryOptions, selectedIndex) { dialog, which ->
                selectedCategory = if (which == 0) null else categoryOptions[which]
                Log.d(TAG, "Category selected from picker: $selectedCategory")
                updateCategoryButtonText()
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.add)) { dialog, which ->
                Log.d(TAG, "Add New Category button clicked.")
                showAddNewCategoryDialog()
            }
            .setNegativeButton(getString(R.string.cancel), null)
        builder.create().show()
    }

    private fun showAddNewCategoryDialog() {
        Log.d(TAG, "Showing Add New Category dialog.")
        val input = EditText(requireContext())
        input.hint = getString(R.string.hint_new_category)
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.add_new_category))
            .setView(input)
            .setPositiveButton(getString(R.string.add)) { dialog, _ ->
                val newCategory = input.text.toString().trim()
                if (newCategory.isNotBlank()) {
                    // For now, just select the new category in the picker UI state
                    // It will be saved with the task.
                    selectedCategory = newCategory
                    Log.d(TAG, "New category '$newCategory' added and selected.")
                    updateCategoryButtonText()
                    // Add the new category to the available list *temporarily* for this dialog session
                    if (!availableCategories.contains(newCategory)) {
                        availableCategories = availableCategories + newCategory
                        Log.d(TAG, "Added '$newCategory' to availableCategories list temporarily.")
                    }
                    Toast.makeText(requireContext(), "Danh mục '$newCategory' đã chọn. Sẽ lưu khi thêm task.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "New category name is blank.")
                    Toast.makeText(requireContext(), "Tên danh mục không được để trống", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }


    private fun showDatePicker() {
        Log.d(TAG, "Showing Date Picker. Current selected date: $selectedDueDate")
        val calendar = Calendar.getInstance()
        if (selectedDueDate != null) { calendar.time = selectedDueDate!! }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                Log.d(TAG, "Date selected: $selectedYear-${selectedMonth+1}-$selectedDay")
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)

                // Preserve existing time if any, otherwise set to midnight
                if (selectedDueTime != null) {
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, selectedDueTime!!.get(Calendar.HOUR_OF_DAY))
                    selectedCalendar.set(Calendar.MINUTE, selectedDueTime!!.get(Calendar.MINUTE))
                    selectedCalendar.set(Calendar.SECOND, selectedDueTime!!.get(Calendar.SECOND))
                    selectedCalendar.set(Calendar.MILLISECOND, selectedDueTime!!.get(Calendar.MILLISECOND))
                } else {
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, 0)
                    selectedCalendar.set(Calendar.MINUTE, 0)
                    selectedCalendar.set(Calendar.SECOND, 0)
                    selectedCalendar.set(Calendar.MILLISECOND, 0)
                }

                selectedDueDate = selectedCalendar.time
                // selectedDueTime is potentially updated here if it was previously set
                if (selectedDueTime != null) {
                    selectedDueTime = selectedCalendar // Update the Calendar instance with the new date
                }

                Log.d(TAG, "Selected Due Date after picker: $selectedDueDate")
                updateDueDateButtonText()
                // updateDueTimeButtonText() // May need update if time was reset to midnight
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
        Log.d(TAG, "Showing Time Picker. Current selected time: ${selectedDueTime?.time}")
        val calendar = Calendar.getInstance()
        val initialHour = selectedDueTime?.get(Calendar.HOUR_OF_DAY) ?: calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = selectedDueTime?.get(Calendar.MINUTE) ?: calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                Log.d(TAG, "Time selected: $selectedHour:$selectedMinute")
                // Use existing date if selectedDueDate is set, otherwise use today's date
                val selectedCalendar = selectedDueDate?.let { Calendar.getInstance().apply { time = it } } ?: Calendar.getInstance()

                selectedCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                selectedCalendar.set(Calendar.MINUTE, selectedMinute)
                selectedCalendar.set(Calendar.SECOND, 0) // Clear seconds/milliseconds
                selectedCalendar.set(Calendar.MILLISECOND, 0)

                selectedDueTime = selectedCalendar
                selectedDueDate = selectedCalendar.time // Update selectedDueDate with time component

                Log.d(TAG, "Selected Due Time after picker: ${selectedDueTime?.time}")
                updateDueTimeButtonText()
                updateDueDateButtonText() // Update date text too as it now includes time
            },
            initialHour,
            initialMinute,
            true // 24-hour format
        )
        timePickerDialog.show()
    }

    private fun showReminderTypePicker() {
        Log.d(TAG, "Showing Reminder Type picker. Current selected label: $selectedReminderLabel")
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
                Log.d(TAG, "Reminder label selected from picker: $selectedReminderLabel")
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
        // Add tinting based on priority color if you have it
        // Example tinting (requires color resources like R.color.priority_low, etc.)
        /*
        val tintColorResId = when (selectedPriority) {
            Task.Priority.NONE -> android.R.color.darker_gray // Or a custom color
            Task.Priority.LOW -> R.color.priority_low // Define these colors
            Task.Priority.MEDIUM -> R.color.priority_medium
            Task.Priority.HIGH -> R.color.priority_high
        }
        binding.buttonPriority.setColorFilter(ContextCompat.getColor(requireContext(), tintColorResId), PorterDuff.Mode.SRC_IN)
        */
        Log.d(TAG, "Priority icon updated for: $selectedPriority")
    }

    private fun updateCategoryButtonText() {
        binding.buttonCategory.text = selectedCategory ?: getString(R.string.select_category)
        Log.d(TAG, "Category button text updated to: ${binding.buttonCategory.text}")
    }

    private fun updateDueDateButtonText() {
        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        val dateTimeFormat = SimpleDateFormat("EEE, MMM d, yyyy 'at' HH:mm", Locale.getDefault()) // Format with time

        if (selectedDueDate != null) {
            // Check if there is a time component (i.e., selectedDueTime is not null or date's time is not midnight)
            val cal = Calendar.getInstance().apply { time = selectedDueDate!! }
            // Consider dateOnly if selectedDueTime is null OR if the time component is midnight (as set by date-only parse)
            val hasTime = selectedDueTime != null && (cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0 || cal.get(Calendar.MILLISECOND) != 0)

            if (hasTime) {
                binding.buttonDueDate.contentDescription = dateTimeFormat.format(selectedDueDate)
                binding.buttonDueDate.setImageResource(R.drawable.baseline_calendar_today_24) // Calendar icon? Maybe a calendar+clock icon?
            } else {
                binding.buttonDueDate.contentDescription = dateFormat.format(selectedDueDate)
                binding.buttonDueDate.setImageResource(R.drawable.baseline_calendar_today_24) // Just calendar icon
            }
            Log.d(TAG, "DueDate button text updated to: ${binding.buttonDueDate.contentDescription}")

        } else {
            binding.buttonDueDate.contentDescription = getString(R.string.select_due_date)
            binding.buttonDueDate.setImageResource(R.drawable.baseline_calendar_today_24) // Reset icon if cleared
            Log.d(TAG, "DueDate button text reset to default.")
        }
    }

    private fun updateDueTimeButtonText() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (selectedDueTime != null) {
            binding.buttonDueTime.contentDescription = timeFormat.format(selectedDueTime!!.time)
            binding.buttonDueTime.setImageResource(R.drawable.clock_svgrepo_com) // Clock icon
            Log.d(TAG, "DueTime button text updated to: ${binding.buttonDueTime.contentDescription}")
        } else {
            binding.buttonDueTime.contentDescription = getString(R.string.select_due_time)
            binding.buttonDueTime.setImageResource(R.drawable.clock_svgrepo_com) // Reset icon if cleared
            Log.d(TAG, "DueTime button text reset to default.")
        }
    }

    private fun updateReminderTypeButtonTextAndIcon() {
        // Determine icon based on whether it's "On Time" or any "Early" type
        val iconResId = when (selectedReminderLabel) {
            getString(R.string.reminder_ontime) -> R.drawable.bell_svgrepo_com // Default alarm icon
            else -> R.drawable.rounded_circle_24 // Use a different icon for set reminder? Assuming rounded_circle_24 is a suitable placeholder
        }
        binding.buttonReminderType.setImageResource(iconResId)
        binding.buttonReminderType.contentDescription = selectedReminderLabel.ifBlank { getString(R.string.select_reminder_type) }
        Log.d(TAG, "ReminderType button updated. Label: $selectedReminderLabel")
    }

    // TODO: Add updateRepeatModeButtonText()

    //endregion


    // --- Handle Save Click and Send Result --- (Existing code)
    private fun handleSaveTaskClick() {
        Log.d(TAG, "Add/Save task button clicked.")
        val title = binding.editTextTaskTitle.text.toString().trim()
        val description = binding.editTextTaskDescription.text.toString().trim()

        if (title.isBlank()) {
            binding.editTextTaskTitle.error = getString(R.string.title_cannot_be_empty)
            Log.d(TAG, "Task title is blank, showing error.")
            return
        }

        // Finalize the dueDate by combining Date and Time components
        val finalDueDate = if (selectedDueDate != null) {
            val calendar = Calendar.getInstance().apply { time = selectedDueDate!! }
            if (selectedDueTime != null) {
                // Combine date part from selectedDueDate and time part from selectedDueTime
                calendar.set(Calendar.HOUR_OF_DAY, selectedDueTime!!.get(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, selectedDueTime!!.get(Calendar.MINUTE))
                calendar.set(Calendar.SECOND, 0) // Keep seconds/milliseconds consistent (usually 0 from pickers/parse)
                calendar.set(Calendar.MILLISECOND, 0)
            } else {
                // If only date was selected/parsed, ensure time is midnight
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            calendar.time
        } else {
            null // No date selected/parsed
        }
        Log.d(TAG, "Final Due Date calculated: $finalDueDate")


        val standardReminderType = when (selectedReminderLabel) {
            getString(R.string.reminder_ontime) -> "ON_TIME"
            getString(R.string.reminder_early_30m) -> "EARLY_30M"
            getString(R.string.reminder_early_1h) -> "EARLY_1H"
            getString(R.string.reminder_early_3h) -> "EARLY_3H"
            getString(R.string.reminder_early_1d) -> "EARLY_1D"
            else -> "ON_TIME" // Default if label is not recognized
        }
        Log.d(TAG, "Final Reminder Type: $standardReminderType (from label: $selectedReminderLabel)")


        // Create or update the Task object
        val task = Task(
            id = taskIdToEdit, // Will be null for new tasks
            title = title,
            description = description.ifBlank { null },
            priority = selectedPriority.name,
            category = selectedCategory,
            // tags = null, // TODO: Add UI for tags
            dueDate = finalDueDate, // Use the combined finalDueDate
            repeatMode = selectedRepeatMode.name, // Will be "NONE" if not implemented
            reminderType = standardReminderType, // Store the standard string
            status = Task.TaskStatus.PENDING.name, // Assume pending unless loading an existing completed task
            type = Task.TaskType.TASK.name // Assuming it's always a task
        )

        // Determine the request key based on whether we are adding or editing
        val requestKey = if (task.id != null) TaskFragment.REQUEST_KEY_EDIT_TASK else TaskFragment.REQUEST_KEY_ADD_TASK
        Log.d(TAG, "Task object created: $task. Sending result with key: $requestKey")

        // Send the new/updated task back to the calling Fragment (TaskFragment)
        val resultBundle = Bundle().apply {
            putParcelable(TaskFragment.BUNDLE_KEY_TASK, task)
        }

        // Use parentFragmentManager if TaskFragment added this dialog
        parentFragmentManager.setFragmentResult(requestKey, resultBundle)
        // childFragmentManager.setFragmentResult(requestKey, resultBundle) // Use parentFragmentManager as dialogs usually attach there
        Log.d(TAG, "Fragment result set. Dismissing dialog.")
        dismiss() // Close the dialog
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView. Cancelling parse job and releasing extractor.")
        parseJob?.cancel() // Cancel parsing coroutine job
        parseHelper.releaseExtractor() // Release ML Kit resources
        _binding = null
    }

    // Helper function to compare Dates ignoring time components (can be moved to utils)
    private fun datesEqual(date1: Date?, date2: Date?): Boolean {
        if (date1 == null && date2 == null) return true
        if (date1 == null || date2 == null) return false
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    // Helper function to compare Calendar instances for time components (can be moved to utils)
    private fun calendarsEqual(cal1: Calendar?, cal2: Calendar?): Boolean {
        if (cal1 == null && cal2 == null) return true
        if (cal1 == null || cal2 == null) return false
        // Compare Hour and Minute, maybe Second if ML Kit provides it consistently
        return cal1.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY) &&
                cal1.get(Calendar.MINUTE) == cal2.get(Calendar.MINUTE)
        // && cal1.get(Calendar.SECOND) == cal2.get(Calendar.SECOND) // Only if relevant
    }
}