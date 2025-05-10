package com.gawasu.sillyn.ui.fragment

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.alamkanak.weekview.MonthLoader
import com.alamkanak.weekview.WeekViewEvent
import com.gawasu.sillyn.R
import com.gawasu.sillyn.databinding.FragmentCalendarBinding // Generated binding class
import com.gawasu.sillyn.ui.fragment.calendar.CalendarViewModel
import com.gawasu.sillyn.ui.fragment.calendar.ViewMode
import com.gawasu.sillyn.utils.FirebaseResult
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

// Add Hilt annotation
@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // Use viewModels() delegate for ViewModel provided by Hilt
    private val viewModel: CalendarViewModel by viewModels()

    // Store the list of events loaded by the ViewModel
    // Use a mutable list to align with WeekView's MonthLoader return type requirement
    private var weekViewEvents: MutableList<WeekViewEvent> = mutableListOf()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWeekView()
        setupListeners()
        observeViewModel()

        // Initial UI state based on ViewModel default state.
        // The ViewModel's init block triggers the initial data load.
        updateViewModeUI(viewModel.currentViewMode.value ?: ViewMode.WEEK)
    }

    private fun setupWeekView() {
        binding.weekView.apply {
            // Use MonthChangeListener to provide events to the WeekView.
            // We will provide the events already loaded by the ViewModel.
            // WeekView requests events month by month as the user scrolls.
            // We provide the events we currently have that fall within that month.
            monthChangeListener = object : MonthLoader.MonthChangeListener {
                override fun onMonthChange(newYear: Int, newMonth: Int): MutableList<WeekViewEvent> {
                    // newMonth is 1-indexed in WeekView library (1=January, 12=December)
                    // Calendar.MONTH is 0-indexed (0=January, 11=December)

                    // Filter the events currently held by the Fragment for the requested month
                    val eventsForMonth = weekViewEvents.filter { event ->
                        event.startTime?.let { startTime ->
                            startTime.get(Calendar.YEAR) == newYear &&
                                    startTime.get(Calendar.MONTH) == newMonth - 1
                        } ?: false // Exclude events without a start time
                    }.toMutableList() // WeekView requires MutableList

                    Log.d("CalendarFragment", "onMonthChange requested $newMonth/$newYear, providing ${eventsForMonth.size} events from cache")
                    return eventsForMonth
                }
            }

            // Set initial properties based on XML defaults and ViewModel state
            // app:noOfVisibleDays is set in XML, we update it when mode changes
            // using the setter method setNumberOfVisibleDays
            // app:firstDayOfWeek - Let's assume Calendar.MONDAY for now, consistent with ViewModel logic

            // Leave interaction listeners empty as requested for now
            // onEventClickListener = null
            // eventLongPressListener = null
            // emptyViewClickListener = null
            // emptyViewLongPressListener = null
            // addEventClickListener = null
            // dropListener = null
        }
    }

    private fun setupListeners() {
        binding.btnPrev.setOnClickListener {
            viewModel.navigateToPrevious()
        }

        binding.btnNext.setOnClickListener {
            viewModel.navigateToNext()
        }

        binding.btnToday.setOnClickListener {
            viewModel.navigateToToday()
        }

        binding.btnDatePicker.setOnClickListener {
            showDatePicker()
        }

        binding.viewModeToggle.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioDay.id -> viewModel.setViewMode(ViewMode.DAY)
                binding.radioWeek.id -> viewModel.setViewMode(ViewMode.WEEK)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.tasksForCurrentPeriod.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FirebaseResult.Success -> {
                    // Update the cached list of events
                    weekViewEvents = result.data.toMutableList() // Convert to MutableList

                    // Notify WeekView to reload events from the monthChangeListener callback
                    // This is crucial because WeekView pulls data, we don't push the list directly.
                    binding.weekView.notifyDatasetChanged()

                    Log.d("CalendarFragment", "ViewModel data loaded successfully: ${weekViewEvents.size} WeekViewEvents")
                    // Handle empty state if necessary
                }
                is FirebaseResult.Error -> {
                    Toast.makeText(requireContext(), "Error loading tasks: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                    Log.e("CalendarFragment", "Error loading tasks", result.exception)
                    weekViewEvents = mutableListOf() // Clear events on error
                    binding.weekView.notifyDatasetChanged() // Update WeekView
                }
                is FirebaseResult.Loading -> {
                    // Show loading indicator (Optional for V1)
                    Log.d("CalendarFragment", "ViewModel loading tasks...")
                }
            }
        }

        viewModel.currentViewMode.observe(viewLifecycleOwner) { viewMode ->
            updateViewModeUI(viewMode)
        }

        // Observe currentDate to make WeekView navigate to the correct date
        viewModel.currentDate.observe(viewLifecycleOwner) { calendar ->
            // WeekView needs to know which date/week to focus on.
            // goToDate() is used to scroll the view to the specified date.
            binding.weekView.goToDate(calendar)

            // Optional: Update a TextView to show the current date/week range string
            // e.g., binding.dateRangeTextView.text = viewModel.getDateRangeString(calendar, viewMode)
        }
    }

    private fun updateViewModeUI(viewMode: ViewMode) {
        // Corrected: Use the setter method
        binding.weekView.setNumberOfVisibleDays(
            when (viewMode) {
                ViewMode.DAY -> 1
                ViewMode.WEEK -> 7 // Or 5 for work week
            }
        )

        // Update radio button state visually if needed
        val radioBtnId = when(viewMode) {
            ViewMode.DAY -> binding.radioDay.id
            ViewMode.WEEK -> binding.radioWeek.id
        }
        if (binding.viewModeToggle.checkedRadioButtonId != radioBtnId) {
            binding.viewModeToggle.check(radioBtnId)
        }

        // When changing mode, ensure WeekView focuses on the correct date based on the ViewModel state
        // This is now handled by the currentDate observation.
    }

    private fun showDatePicker() {
        val c = viewModel.currentDate.value ?: Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH) // Calendar.MONTH is 0-indexed (0-11)
        val day = c.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                // selectedMonth from DatePickerDialog is 0-indexed (0-11)
                viewModel.navigateToDate(selectedYear, selectedMonth, selectedDay)
            },
            year, month, day
        )
        // Optional: Set min/max dates for the picker if needed
        // datePickerDialog.datePicker.minDate = ...
        datePickerDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clean up binding
    }
}