package com.gawasu.sillyn.ui.dialog

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.DialogAddTaskBinding
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.ui.fragment.TaskFragment
import com.gawasu.sillyn.utils.ParseHelper
import com.gawasu.sillyn.utils.ParsedEntities
import com.google.android.material.snackbar.Snackbar
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
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit // Import TimeUnit

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
    // Variable to hold the original task object if editing
    private var originalTaskToEdit: Task? = null


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
                originalTaskToEdit = taskToEdit // Store original task
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

                // Check ML Kit model status immediately on creation in Add mode
                if (parseHelper.isModelDownloaded()) {
                    Log.d(TAG, "ML Kit model is already downloaded and ready on create (Add Mode).")
                    // Trigger initial parse if text fields are pre-filled (unlikely in add mode but safe)
                    val currentTitle = binding.editTextTaskTitle.text?.toString() ?: ""
                    val currentDescription = binding.editTextTaskDescription.text?.toString() ?: ""
                    if (currentTitle.isNotBlank() || currentDescription.isNotBlank()) {
                        parseAndApply(currentTitle, currentDescription)
                    } else {
                        // notthing
                    }
                } else if (parseHelper.isModelDownloadInProgress()) {
                    Log.i(TAG, "ML Kit model download in progress on create (Add Mode). Parse will trigger after debounce if model becomes ready.")
                    // No need to observe a non-existent flow. Rely on isModelDownloaded() in onEach.
                } else {
                    Log.w(TAG, "ML Kit model not downloaded on create (Add Mode). Parse will trigger after debounce if model becomes ready.")
                    // No need to observe a non-existent flow. Rely on isModelDownloaded() in onEach.
                }
            }
        }

        setupClickListeners()

        // Initial UI update based on default values or loaded task
        updatePriorityButtonIcon()
        updateCategoryButtonText()
        updateDueDateButtonText() // This will now only update contentDescription/icon for date button
        updateDueTimeButtonText() // This will now only update contentDescription/icon for time button
        Log.d(TAG, "Initial UI updated.")

        // Initialize selectedReminderLabel and update UI
        // If editing, it's loaded in loadTaskForEditing. If adding, use default.
        if (selectedReminderLabel.isBlank() && originalTaskToEdit == null) { // Only default if adding and not loaded
            selectedReminderLabel = mapReminderTypeToLabel(Task.ReminderType.ON_TIME.name) // Default label
            Log.d(TAG, "Reminder label defaulted to: $selectedReminderLabel")
        }
        updateReminderTypeButtonTextAndIcon() // This will update text on the reminder button

        // TODO: updateRepeatModeButtonText()

        // Set initial values for Flow. This is mainly relevant for Add mode parsing.
        // Ensure flows are updated from current text if the dialog is recreated (e.g. config change)
        if (taskIdToEdit == null) { // Only do this in Add mode
            titleTextFlow.value = binding.editTextTaskTitle.text?.toString() ?: ""
            descriptionTextFlow.value = binding.editTextTaskDescription.text?.toString() ?: ""
            Log.d(TAG, "Initial flow values set (Add Mode): title='${titleTextFlow.value}', description='${descriptionTextFlow.value}'")
        } else {
            Log.d(TAG, "Edit mode, skipping initial flow value setting.")
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
        // Load selectedDueTime if the loaded date has a time component
        task.dueDate?.let { date ->
            val calendar = Calendar.getInstance().apply { time = date }
            // Check if time component is non-midnight or non-zero seconds/milliseconds
            if (calendar.get(Calendar.HOUR_OF_DAY) != 0 || calendar.get(Calendar.MINUTE) != 0 || calendar.get(Calendar.SECOND) != 0 || calendar.get(Calendar.MILLISECOND) != 0) {
                selectedDueTime = calendar
                Log.d(TAG, "Loaded Due Date/Time: ${date} (with time component)")
            } else {
                selectedDueTime = null // Treat as date-only if time is midnight
                Log.d(TAG, "Loaded Due Date: ${date} (date only)")
            }
        } ?: run {
            selectedDueDate = null
            selectedDueTime = null
            Log.d(TAG, "Loaded Due Date: null")
        }


        // Load reminder type (Mapping from Task.reminderType string to prototype labels)
        selectedReminderLabel = mapReminderTypeToLabel(task.reminderType)
        Log.d(TAG, "Loaded Reminder Label: $selectedReminderLabel (from type: ${task.reminderType})")


        // TODO: Load Repeat Mode, Tags, Status, Type if UI supports

        // Change button UI for editing mode
        binding.buttonAddTask.contentDescription = getString(R.string.update_task)
        // Assuming baseline_done_24 is available or using a placeholder
        // FIX: Changed icon to one that exists in the provided XMLs (checked_checkbox_svgrepo_com)
        // Also tinting with a standard teal color.
        binding.buttonAddTask.setImageResource(R.drawable.checked_checkbox_svgrepo_com) // Use a checkmark/done icon
        binding.buttonAddTask.setColorFilter(ContextCompat.getColor(requireContext(), R.color.teal_200), PorterDuff.Mode.SRC_IN) // Optional: Tint green


        // Disable or hide elements not relevant for edit mode if needed (e.g., repeat)
        // binding.buttonRepeat.visibility = View.GONE // Example
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
        // Ensure this is only called when taskIdToEdit is null (Add mode)
        if (taskIdToEdit != null) {
            Log.w(TAG, "setupTextChangeListenersForParsing called in Edit mode. Skipping.")
            return
        }


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
                // Rely on isModelDownloaded() check
                if (parseHelper.isModelDownloaded() || (title.isBlank() && description.isBlank())) {
                    parseAndApply(title, description)
                } else {
                    // Optionally log if model is still downloading or not ready
                    if (parseHelper.isModelDownloadInProgress()) {
                        Log.i(TAG, "ML Kit Model is still downloading, skipping parse after debounce.")
                    } else {
                        Log.w(TAG, "ML Kit Model not ready after debounce, skipping parse.")
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope) // Launch coroutine in the fragment's view lifecycle scope
    }

    private fun parseAndApply(title: String, description: String) {
        // Ensure this is only called in Add mode
        if (taskIdToEdit != null) {
            Log.w(TAG, "parseAndApply called in Edit mode. Skipping.")
            return
        }

        // Run parsing in a coroutine
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d(TAG, "Starting parseAndApply for title='${title}', description='${description}'")
            val parsedEntities = parseHelper.parseText(title, description)
            Log.d(TAG, "ParseHelper returned: $parsedEntities")

            // --- Apply parsed results and update UI/State ---
            // Note: Apply one by one and show snackbar for each *if* they changed and are non-null

            // 1. Category
            val oldCategory = selectedCategory
            // Only apply parsed category if it's NOT null AND it's different from the current state
            if (parsedEntities.category != null && parsedEntities.category != oldCategory) {
                // Also check if the parsed category is one of the available categories, or allow new?
                // For simplicity, allow parsed category even if not in the initial list
                selectedCategory = parsedEntities.category
                Log.d(TAG, "Category changed by parse: '$oldCategory' -> '$selectedCategory'. Updating UI.")
                updateCategoryButtonText()
                showSnackbar("Đã phát hiện danh mục: ${selectedCategory}")
            } else if (parsedEntities.category == null && oldCategory != arguments?.getString(ARG_DEFAULT_CATEGORY_NAME)) {
                // Reset category to default or null ONLY if the parse result is null
                // AND the current category is NOT already the default category from args
                selectedCategory = arguments?.getString(ARG_DEFAULT_CATEGORY_NAME)
                if (selectedCategory != oldCategory) {
                    Log.d(TAG, "Category reset by parse: '$oldCategory' -> '$selectedCategory' (default/null). Updating UI.")
                    updateCategoryButtonText()
                    // No snackbar for reset
                } else {
                    Log.d(TAG, "Category parse result is null, but current category is already default/null. No change.")
                }
            } else {
                Log.d(TAG, "Category parse result: ${parsedEntities.category}. No change from parse or currently matches default. Current: '$oldCategory'")
            }


            // 2. Priority
            val oldPriority = selectedPriority
            // Only apply parsed priority if it's NOT NONE AND it's different from the current state
            if (parsedEntities.priority != Task.Priority.NONE && parsedEntities.priority != oldPriority) {
                selectedPriority = parsedEntities.priority
                Log.d(TAG, "Priority changed by parse: '$oldPriority' -> '$selectedPriority'. Updating UI.")
                updatePriorityButtonIcon()
                showSnackbar("Đã phát hiện ưu tiên: ${selectedPriority.getLocalizedName()}") // Need localized name
            } else if (parsedEntities.priority == Task.Priority.NONE && oldPriority != Task.Priority.NONE) {
                // Reset priority ONLY if parse result is NONE and current priority is not already NONE
                selectedPriority = Task.Priority.NONE
                Log.d(TAG, "Priority reset by parse: '$oldPriority' -> '$selectedPriority'. Updating UI.")
                updatePriorityButtonIcon()
                // No snackbar for reset
            } else {
                Log.d(TAG, "Priority parse result: ${parsedEntities.priority}. No change from parse. Current: '$oldPriority'")
            }


            // 3. Date and Time
            val oldDueDate = selectedDueDate
            val oldDueTime = selectedDueTime // Calendar instance

            var parsedDate: Date? = parsedEntities.dueDate // Date part from parse
            var parsedTime: Calendar? = parsedEntities.dueTime // Calendar (Date + Time) from parse

            // Logic: Prioritize parsedTime (if DateTime parsed), then parsedDate (if only Date parsed).
            // If parsedTime is available, it contains both date and time from ML Kit.
            // If only parsedDate is available, ML Kit found a date entity with granularity < HOUR (time is implicitly midnight).
            // If both are null, no date/time found.

            var newSelectedDate: Date? = null
            var newSelectedTime: Calendar? = null

            if (parsedTime != null) {
                // ML Kit parsed a DateTime or Time. Use the full Calendar object.
                newSelectedTime = parsedTime
                newSelectedDate = parsedTime.time // Date part synced with the Calendar
                Log.d(TAG, "Parse: ML Kit provided Calendar (DateTime/Time). Using parsed time: ${newSelectedTime.time}")
            } else if (parsedDate != null) {
                // ML Kit parsed only a Date (time is midnight).
                newSelectedDate = parsedDate
                newSelectedTime = null // Ensure time is null if only date was parsed
                Log.d(TAG, "Parse: ML Kit provided only Date (time is midnight). Using parsed date: $newSelectedDate")
            } else {
                // No date or time parsed.
                newSelectedDate = null
                newSelectedTime = null
                Log.d(TAG, "Parse: No Date or Time found.")
            }

            // --- Determine if state changed and apply updates ---
            val dueDateChanged = !datesEqual(newSelectedDate, oldDueDate)
            // Compare time component *only* if both old and new states have a potential time source
            val dueTimeChanged = !calendarsEqual(newSelectedTime, oldDueTime)


            if (dueDateChanged || dueTimeChanged) {
                // Apply the new state only if *any* part changed or if both are null now and were not null before
                if (newSelectedDate != null || newSelectedTime != null || oldDueDate != null || oldDueTime != null) {
                    selectedDueDate = newSelectedDate
                    selectedDueTime = newSelectedTime // Update time calendar based on parse result

                    Log.d(TAG, "Date/Time state changed. Old: ${oldDueDate}/${oldDueTime?.time}, New: ${selectedDueDate}/${selectedDueTime?.time}. Updating UI.")

                    updateDueDateButtonText() // Only updates contentDescription/icon
                    updateDueTimeButtonText() // Only updates contentDescription/icon

                    // Show snackbar only if *something* was parsed successfully
                    if (newSelectedDate != null || newSelectedTime != null) {
                        val dateText = selectedDueDate?.let { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(it) } ?: ""
                        val timeText = selectedDueTime?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.time) } ?: ""
                        val message = when {
                            timeText.isNotBlank() && dateText.isNotBlank() -> "Đã phát hiện ngày và giờ: $dateText lúc $timeText"
                            dateText.isNotBlank() -> "Đã phát hiện ngày đến hạn: $dateText"
                            timeText.isNotBlank() -> "Đã phát hiện thời gian: $timeText"
                            else -> "" // Should not happen based on outer check
                        }
                        if (message.isNotBlank()) {
                            showSnackbar(message)
                        }
                    } else {
                        // Show snackbar for reset if needed (e.g., text cleared)
                        if (oldDueDate != null || oldDueTime != null) {
                            showSnackbar("Ngày và giờ đến hạn đã được xóa.")
                        }
                    }

                } else {
                    Log.d(TAG, "Date/Time parse result is null and current state was already null. No change.")
                }
            } else {
                Log.d(TAG, "Date/Time state did not change from parse results.")
            }


            // --- Handle Reminder Type (Not typically parsed by ML Kit entities like this, but could be if implemented) ---
            // If your ML Kit model *could* parse specific reminder phrases, you would add similar logic here.
            // For now, rely on manual selection via showReminderTypePicker.
            Log.d(TAG, "Reminder type parsing is not implemented in ParseHelper. Current reminder label: $selectedReminderLabel")

            Log.d(TAG, "parseAndApply finished. Current state: Category=${selectedCategory}, Priority=${selectedPriority}, DueDate=${selectedDueDate}, DueTime=${selectedDueTime?.time}, ReminderLabel=${selectedReminderLabel}")

        }
    }

    // Helper function for showing snackbar
    private fun showSnackbar(message: String) {
        // Ensure Snackbar is shown on the root view of the dialog for correct placement
        // Or potentially use requireView().parent as view if the root layout isn't ideal
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
        Log.d(TAG, "Showing Snackbar: \"$message\"")
    }

    // Helper extension for Task.Priority to get localized name
    // Put this in a shared utils file or in Task.kt if preferred
    fun Task.Priority.getLocalizedName(): String {
        // Use requireContext() instead of context? for safety in onViewCreated scope
        return try {
            when (this) {
                Task.Priority.NONE -> requireContext().getString(R.string.priority_none_text)
                Task.Priority.LOW -> requireContext().getString(R.string.priority_low_text)
                Task.Priority.MEDIUM -> requireContext().getString(R.string.priority_medium_text)
                Task.Priority.HIGH -> requireContext().getString(R.string.priority_high_text)
            }
        } catch (e: IllegalStateException) {
            // Catch case where context is not available (shouldn't happen in onViewCreated but defensive)
            this.name // Return raw name as fallback
        }
    }

    // Helper to map ReminderType string from Task object to prototype label strings
    private fun mapReminderTypeToLabel(reminderType: String?): String {
        // FIX: Ensure mapping handles null/unknown types safely
        return when(reminderType) {
            Task.ReminderType.ON_TIME.name -> getString(R.string.reminder_ontime)
            "EARLY_30M" -> getString(R.string.reminder_early_30m)
            "EARLY_1H" -> getString(R.string.reminder_early_1h)
            "EARLY_3H" -> getString(R.string.reminder_early_3h)
            "EARLY_1D" -> getString(R.string.reminder_early_1d)
            else -> getString(R.string.reminder_ontime) // Default for null or unknown types
        }
    }

    // Helper to map prototype label strings back to Task.ReminderType standard strings
    private fun mapLabelToReminderType(label: String): String {
        return when(label) {
            getString(R.string.reminder_ontime) -> Task.ReminderType.ON_TIME.name
            getString(R.string.reminder_early_30m) -> "EARLY_30M" // Use standard string
            getString(R.string.reminder_early_1h) -> "EARLY_1H"
            getString(R.string.reminder_early_3h) -> "EARLY_3H"
            getString(R.string.reminder_early_1d) -> "EARLY_1D"
            else -> Task.ReminderType.ON_TIME.name // Default
        }
    }


    //region // --- Show Picker Dialogs --- (Existing code with minor adjustments)

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
                R.id.priority_medium -> Task.Priority.MEDIUM // Corrected typo here
                R.id.priority_high -> Task.Priority.HIGH // Corrected typo here
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
                    // This ensures it appears in the picker if the user opens it again within the same dialog session
                    if (!availableCategories.contains(newCategory)) {
                        availableCategories = availableCategories + newCategory
                        Log.d(TAG, "Added '$newCategory' to availableCategories list temporarily.")
                    }
                    Toast.makeText(requireContext(), "Danh mục '$newCategory' đã chọn. Sẽ lưu khi thêm task.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d(TAG, "New category name is blank.")
                    Toast.makeText(requireContext(), "Tên danh mục không được để trống", Toast.LENGTH_SHORT).show()
                }
                // Don't dismiss the dialog here, let the system handle it after the button click listener
                // dialog.dismiss() // Removed this line
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }


    private fun showDatePicker() {
        Log.d(TAG, "Showing Date Picker. Current selected date: $selectedDueDate")
        val calendar = Calendar.getInstance()
        selectedDueDate?.let { calendar.time = it } // Use existing date if available

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                Log.d(TAG, "Date selected: $selectedYear-${selectedMonth+1}-$selectedDay")
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(selectedYear, selectedMonth, selectedDay)

                // Preserve existing time if selectedDueTime is set, otherwise set to midnight
                // Important: If selectedDueTime exists, use its time components, BUT use the DATE from the date picker
                val finalCalendar = selectedCalendar.apply {
                    selectedDueTime?.let { timeCal ->
                        set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                        set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
                        set(Calendar.MILLISECOND, timeCal.get(Calendar.MILLISECOND))
                    } ?: run {
                        // If no time was previously selected, set time to midnight for a date-only selection
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }


                selectedDueDate = finalCalendar.time
                // Update selectedDueTime *only if time was previously selected* so updateDueTimeButtonText works
                // If a date is picked *after* time was picked, we update the calendar instance backing selectedDueTime.
                // If a date is picked and *no time was ever picked*, selectedDueTime remains null.
                if (selectedDueTime != null || (finalCalendar.get(Calendar.HOUR_OF_DAY) != 0 || finalCalendar.get(Calendar.MINUTE) != 0)) {
                    selectedDueTime = finalCalendar // Update the Calendar instance with the new date part IF time is relevant
                } else {
                    selectedDueTime = null // Ensure time is null if resulting date is midnight and no time was picked
                }


                Log.d(TAG, "Selected Due Date after picker: $selectedDueDate, Selected Due Time (Calendar): ${selectedDueTime?.time}")
                updateDueDateButtonText() // Only updates contentDescription/icon
                // No need to update time text here unless time picker was also used - the text for time is on buttonDueTime
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        // Optional: Set min/max date
        // datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000 // Prevent selecting past dates (careful with timezones)
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        Log.d(TAG, "Showing Time Picker. Current selected time: ${selectedDueTime?.time}")
        val calendar = Calendar.getInstance()
        // Use current time if no time was previously selected
        val initialHour = selectedDueTime?.get(Calendar.HOUR_OF_DAY) ?: calendar.get(Calendar.HOUR_OF_DAY)
        val initialMinute = selectedDueTime?.get(Calendar.MINUTE) ?: calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                Log.d(TAG, "Time selected: $selectedHour:$selectedMinute")
                // Use existing date if selectedDueDate is set, otherwise use today's date
                // Important: If selectedDueDate exists, use its date components, BUT use the TIME from the time picker
                val selectedCalendar = selectedDueDate?.let { Calendar.getInstance().apply { time = it } } ?: Calendar.getInstance()

                selectedCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                selectedCalendar.set(Calendar.MINUTE, selectedMinute)
                selectedCalendar.set(Calendar.SECOND, 0) // Clear seconds/milliseconds
                selectedCalendar.set(Calendar.MILLISECOND, 0)

                selectedDueTime = selectedCalendar // Always set selectedDueTime when time is picked
                selectedDueDate = selectedCalendar.time // Update selectedDueDate with the new date+time value

                Log.d(TAG, "Selected Due Date/Time after time picker: ${selectedDueTime?.time}")
                updateDueTimeButtonText() // Only updates contentDescription/icon
                updateDueDateButtonText() // Only updates contentDescription/icon (to include time if present)
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

        // Find the index of the currently selected label. Default to 0 ("On Time") if not found.
        val selectedIndex = prototypeReminderOptions.indexOf(selectedReminderLabel).takeIf { it != -1 } ?: 0


        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.select_reminder_type))
            .setSingleChoiceItems(prototypeReminderOptions, selectedIndex) { dialog, which ->
                selectedReminderLabel = prototypeReminderOptions[which] // Store the selected string label
                Log.d(TAG, "Reminder label selected from picker: $selectedReminderLabel")
                updateReminderTypeButtonTextAndIcon() // Updates icon and contentDescription
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
            Task.Priority.NONE -> R.drawable.flag_svgrepo_com_none
            Task.Priority.LOW -> R.drawable.flag_svgrepo_com_low
            Task.Priority.MEDIUM -> R.drawable.flag_svgrepo_com_medium
            Task.Priority.HIGH -> R.drawable.flag_svgrepo_com_high
        }
        binding.buttonPriority.setImageResource(iconResId)
        // Optional: Add tinting based on priority color if you have it
        // Example tinting (requires color resources like R.color.priority_low, etc.)
        /*
        val tintColorResId = when (selectedPriority) {
            Task.Priority.NONE -> android.R.color.darker_gray // Or a custom color
            Task.Priority.LOW -> R.color.priority_low // Define these colors
            Task.Priority.MEDIUM -> R.color.priority_medium
            Task.Priority.HIGH -> R.color.priority_high
        }
         try {
            binding.buttonPriority.setColorFilter(ContextCompat.getColor(requireContext(), tintColorResId), PorterDuff.Mode.SRC_IN)
         } catch (e: Exception) {
             Log.e(TAG, "Error applying priority tint", e)
         }
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
            // Check if there is a time component (i.e., selectedDueTime is not null OR the date's time is not midnight)
            // Use selectedDueTime as the definitive source for the time component if it exists.
            // If selectedDueTime is null, check if the date itself has a non-midnight time (could happen if loaded from storage before split)
            // Correctly determine if a time component should be shown based on selectedDueTime primarily
            val hasTimeComponent = selectedDueTime != null // Rely primarily on selectedDueTime Calendar

            val textToShow = if (hasTimeComponent) {
                try { dateTimeFormat.format(selectedDueDate) } catch (e: Exception) { dateFormat.format(selectedDueDate) + " (Invalid Time)" }
            } else {
                dateFormat.format(selectedDueDate)
            }

            // FIX 5: Remove setting text on ImageButton. Only update contentDescription and icon.
            // binding.buttonDueDate.text = textToShow // REMOVED
            binding.buttonDueDate.contentDescription = textToShow // Accessibility text
            binding.buttonDueDate.setImageResource(R.drawable.baseline_calendar_today_24) // Use calendar icon
            binding.buttonDueTime.visibility = View.VISIBLE // Always show time button when date is selected
            Log.d(TAG, "DueDate button content description updated to: ${binding.buttonDueDate.contentDescription}")

        } else {
            // FIX 5: Remove setting text on ImageButton. Only update contentDescription and icon.
            // binding.buttonDueDate.text = getString(R.string.select_due_date) // REMOVED
            binding.buttonDueDate.contentDescription = getString(R.string.select_due_date) // Accessibility text
            binding.buttonDueDate.setImageResource(R.drawable.baseline_calendar_today_24) // Reset icon if cleared
            binding.buttonDueTime.visibility = View.VISIBLE // Always visible? Let's keep visible.
            Log.d(TAG, "DueDate button content description reset to default.")
        }
    }

    // Helper extension function to check if a Date object has a non-midnight time component
    // This is less relevant now that we rely on selectedDueTime, but keep for robustness
    private fun Date.hasTimeComponent(): Boolean {
        val calendar = Calendar.getInstance().apply { time = this@hasTimeComponent }
        return calendar.get(Calendar.HOUR_OF_DAY) != 0 ||
                calendar.get(Calendar.MINUTE) != 0 ||
                calendar.get(Calendar.SECOND) != 0 ||
                calendar.get(Calendar.MILLISECOND) != 0
    }


    private fun updateDueTimeButtonText() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (selectedDueTime != null) {
            val textToShow = timeFormat.format(selectedDueTime!!.time)
            // FIX 5: Remove setting text on ImageButton. Only update contentDescription and icon.
            // binding.buttonDueTime.text = textToShow // REMOVED
            binding.buttonDueTime.contentDescription = textToShow // Accessibility text
            binding.buttonDueTime.setImageResource(R.drawable.clock_svgrepo_com) // Clock icon
            Log.d(TAG, "DueTime button content description updated to: ${binding.buttonDueTime.contentDescription}")
        } else {
            // FIX 5: Remove setting text on ImageButton. Only update contentDescription and icon.
            // binding.buttonDueTime.text = getString(R.string.select_due_time) // REMOVED
            binding.buttonDueTime.contentDescription = getString(R.string.select_due_time) // Accessibility text
            binding.buttonDueTime.setImageResource(R.drawable.clock_svgrepo_com) // Reset icon if cleared
            Log.d(TAG, "DueTime button content description reset to default.")
        }
    }

    private fun updateReminderTypeButtonTextAndIcon() {
        // Determine icon based on whether it's "On Time" or any "Early" type
        val iconResId = when (selectedReminderLabel) {
            getString(R.string.reminder_ontime) -> R.drawable.bell_svgrepo_com // Default alarm icon
            // Add icons for other reminder types if available
            else -> R.drawable.bell_svgrepo_com // Use a different icon for set reminder? Example placeholder - Using same bell for now
        }
        binding.buttonReminderType.setImageResource(iconResId)
        binding.buttonReminderType.contentDescription = selectedReminderLabel.ifBlank { getString(R.string.select_reminder_type) } // Accessibility
        // Update button text as well for clarity
        // FIX 6: Remove setting text on ImageButton. Only update contentDescription and icon.
        // binding.buttonReminderType.text = selectedReminderLabel.ifBlank { getString(R.string.select_reminder_type) } // REMOVED
        Log.d(TAG, "ReminderType button updated. Content Description: ${binding.buttonReminderType.contentDescription}")
    }

    // TODO: Add updateRepeatModeButtonText()

    //endregion


    // --- Handle Save Click and Send Result --- (Existing code)
    private fun handleSaveTaskClick() {
        Log.d(TAG, "Add/Save task button clicked. Task ID: ${taskIdToEdit ?: "New Task"}")
        val title = binding.editTextTaskTitle.text.toString().trim()
        val description = binding.editTextTaskDescription.text.toString().trim()

        if (title.isBlank()) {
            binding.editTextTaskTitle.error = getString(R.string.title_cannot_be_empty)
            Log.d(TAG, "Task title is blank, showing error.")
            return
        }

        // Finalize the dueDate by combining Date and Time components from state variables
        // This logic is crucial as selectedDueDate might be Date-only or Date+Time,
        // and selectedDueTime (if not null) contains the preferred time.
        val finalDueDate: Date? = when {
            selectedDueTime != null -> {
                // If time was picked/parsed as Calendar, use its time
                selectedDueTime!!.time
            }
            selectedDueDate != null -> {
                // If no time was picked (selectedDueTime is null), but a date was picked/parsed as Date
                // Use the selectedDueDate, but ensure time is midnight
                Calendar.getInstance().apply {
                    time = selectedDueDate!!
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            }
            else -> null // Neither date nor time was selected/parsed
        }
        Log.d(TAG, "Final Due Date calculated: $finalDueDate (based on selectedDueDate=$selectedDueDate, selectedDueTime=${selectedDueTime?.time})")


        // Map the selected Reminder Label back to the standard ReminderType string
        val standardReminderType = mapLabelToReminderType(selectedReminderLabel)
        Log.d(TAG, "Final Reminder Type: $standardReminderType (from label: $selectedReminderLabel)")


        // Create or update the Task object
        val task = (originalTaskToEdit ?: Task(id = taskIdToEdit)).copy( // Use originalTaskToEdit if exists, otherwise create a new Task with potential id
            title = title,
            description = description.ifBlank { null },
            priority = selectedPriority.name,
            category = selectedCategory,
            // tags = null, // TODO: Add UI for tags
            dueDate = finalDueDate, // Use the combined finalDueDate
            repeatMode = selectedRepeatMode.name, // Will be "NONE" if not implemented
            reminderType = standardReminderType, // Store the standard string
            // Status should generally not be changed via this dialog, except maybe if editing a completed task?
            // For now, keep original status if editing, default to PENDING if adding.
            status = originalTaskToEdit?.status ?: Task.TaskStatus.PENDING.name,
            type = originalTaskToEdit?.type ?: Task.TaskType.TASK.name // Assuming it's always a task
        )

        // Determine the request key based on whether we are adding or editing
        val requestKey = if (task.id != null) TaskFragment.REQUEST_KEY_EDIT_TASK else TaskFragment.REQUEST_KEY_ADD_TASK
        Log.d(TAG, "Task object created/updated: $task. Sending result with key: $requestKey")

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
    // Used when comparing only the date part of parsed results
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
    // Used when comparing the time part of parsed results (which come as a Calendar)
    private fun calendarsEqual(cal1: Calendar?, cal2: Calendar?): Boolean {
        if (cal1 == null && cal2 == null) return true
        if (cal1 == null || cal2 == null) return false
        // Compare Hour and Minute, maybe Second if ML Kit provides it consistently
        // Note: This only compares *time*, assuming the dates within the calendars might be different.
        // For comparing if the *entire* date and time are the same, you'd use date.equals() or calendar.equals()
        // But for parsing logic, we might get a new date but the same time, or vice versa.
        // Let's compare full milliseconds for safety if it comes from ML Kit as a Calendar.
        return cal1.timeInMillis == cal2.timeInMillis
    }
}