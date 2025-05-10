package com.gawasu.sillyn.ui.fragment.calendar

import android.content.Context
import android.graphics.Color // Keep if needed elsewhere, but ContextCompat is used now
import android.util.Log
import androidx.core.content.ContextCompat // Import ContextCompat
import androidx.lifecycle.* // Import all necessary Lifecycle classes
import com.alamkanak.weekview.WeekViewEvent
import com.gawasu.sillyn.R // Assume your color resources are here
import com.gawasu.sillyn.data.repository.TaskRepositoryInterface
import com.gawasu.sillyn.domain.model.Task
import com.gawasu.sillyn.utils.FirebaseResult
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.* // Import CoroutineScope and other utilities
import kotlinx.coroutines.flow.* // Import Flow operators
import java.util.*
import javax.inject.Inject

enum class ViewMode {
    DAY, WEEK
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepository: TaskRepositoryInterface,
    private val firebaseAuth: FirebaseAuth,
    @ApplicationContext private val context: Context // Inject context for color resources
) : ViewModel() {

    private val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    // State for the currently displayed date (start of the view period).
    // This date is typically the *first* day visible in Week mode, or the *only* day in Day mode.
    // Initialize with today's date.
    private val _currentDate = MutableLiveData<Calendar>(Calendar.getInstance())
    val currentDate: LiveData<Calendar> get() = _currentDate

    // State for the current view mode (Day or Week)
    private val _currentViewMode = MutableLiveData<ViewMode>(ViewMode.WEEK) // Default to Week view
    val currentViewMode: LiveData<ViewMode> get() = _currentViewMode

    // Use SharedFlow to combine state changes and trigger data loading.
    // SharedFlow is suitable here because we want collectors to receive the latest state
    // when they become active, and new emissions should trigger processing.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _stateTrigger = MutableSharedFlow<Pair<Calendar, ViewMode>>(
        replay = 1, // Replay the last emitted state to new collectors
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST // Handle backpressure
    )

    // LiveData holding the result of loading WeekViewEvents for the current view period
    val tasksForCurrentPeriod: LiveData<FirebaseResult<List<WeekViewEvent>>> = _stateTrigger
        .onStart { // Emit initial state when collector starts observing
            // Ensure both _currentDate and _currentViewMode have values before emitting
            _currentDate.value?.let { initialDate ->
                _currentViewMode.value?.let { initialMode ->
                    emit(initialDate to initialMode)
                }
            }
        }
        .distinctUntilChanged() // Only process if the date or view mode actually changes
        .flatMapLatest { (calendar, viewMode) ->
            // Calculate the date range based on the received calendar and view mode
            val (startDate, endDate) = calculateDateRange(calendar, viewMode)
            Log.d("CalendarViewModel", "Fetching tasks for range: ${startDate.toLocaleString()} to ${endDate.toLocaleString()}")

            // Get tasks from repository for the calculated range
            currentUserId?.let { userId ->
                // Use the new getTasksInRange method
                taskRepository.getTasksInRange(userId, startDate, endDate)
            } ?: flowOf(FirebaseResult.Error(Exception("User not logged in"))) // Emit error if user ID is null
        }
        .map { result ->
            // Map FirebaseResult<List<Task>> to FirebaseResult<List<WeekViewEvent>>
            when (result) {
                is FirebaseResult.Success -> {
                    val weekViewEvents = result.data.mapNotNull { task ->
                        mapTaskToWeekViewEvent(task)
                    }
                    FirebaseResult.Success(weekViewEvents)
                }
                is FirebaseResult.Error -> FirebaseResult.Error(result.exception)
                is FirebaseResult.Loading -> FirebaseResult.Loading
            }
        }
        // Convert the Flow to LiveData, bound to the ViewModel's lifecycle
        .asLiveData(viewModelScope.coroutineContext)


    // --- Initialization ---
    init {
        // Manually trigger the initial data load by emitting the current state
        // This needs to be in a coroutine because emit is a suspend function.
        // Use viewModelScope.launch for this.
        viewModelScope.launch {
            _currentDate.value?.let {
                _currentViewMode.value?.let { mode ->
                    _stateTrigger.emit(it to mode)
                }
            }
        }
    }

    // --- Date Range Calculation ---
    // Calculates the start and end date for the repository query based on the given calendar and view mode
    private fun calculateDateRange(calendar: Calendar, viewMode: ViewMode): Pair<Date, Date> {
        val startCalendar = calendar.clone() as Calendar
        val endCalendar = calendar.clone() as Calendar

        // Reset time components to the beginning of the day for accurate date range calculation
        startCalendar.set(Calendar.HOUR_OF_DAY, 0)
        startCalendar.set(Calendar.MINUTE, 0)
        startCalendar.set(Calendar.SECOND, 0)
        startCalendar.set(Calendar.MILLISECOND, 0)

        when (viewMode) {
            ViewMode.DAY -> {
                // Range is the full day of the current date [start of day, start of next day)
                endCalendar.time = startCalendar.time // Start from the beginning of the day
                endCalendar.add(Calendar.DAY_OF_YEAR, 1) // End at the beginning of the next day (exclusive)
            }
            ViewMode.WEEK -> {
                // Range is the full week containing the current date [start of week, start of next week)
                // Ensure the startCalendar is the first day of the week (e.g., Monday)
                val todayOfWeek = startCalendar.get(Calendar.DAY_OF_WEEK)
                val firstDayOfWeek = Calendar.MONDAY // Assume Monday is the start of the week
                // Calculate days to subtract to get to the first day of the week
                // (todayOfWeek - firstDayOfWeek + 7) % 7 handles cases where todayOfWeek < firstDayOfWeek
                val diff = (todayOfWeek - firstDayOfWeek + 7) % 7
                startCalendar.add(Calendar.DAY_OF_YEAR, -diff) // Subtract difference

                // Reset time components again after adjusting the date, just to be safe
                startCalendar.set(Calendar.HOUR_OF_DAY, 0)
                startCalendar.set(Calendar.MINUTE, 0)
                startCalendar.set(Calendar.SECOND, 0)
                startCalendar.set(Calendar.MILLISECOND, 0)


                endCalendar.time = startCalendar.time // Start from the beginning of the week
                endCalendar.add(Calendar.DAY_OF_YEAR, 7) // End at the beginning of the next week (exclusive)
            }
        }
        return Pair(startCalendar.time, endCalendar.time)
    }


    // --- Task to WeekViewEvent Mapping ---
    private fun mapTaskToWeekViewEvent(task: Task): WeekViewEvent? {
        // Only map tasks with a dueDate
        val dueDate = task.dueDate ?: return null

        val startTime = Calendar.getInstance().apply { time = dueDate }
        val endTime = startTime.clone() as Calendar // Clone to avoid modifying startTime
        endTime.add(Calendar.MINUTE, 30) // Set end time 30 minutes after due date (Requirement 1)

        // Map priority to color
        val priorityColor = getPriorityColor(task.priority)

        val weekViewEvent = WeekViewEvent(
            task.id.toString(), // Use String ID (v2.2.0+)
            task.title,         // Only title as name (Requirement 3)
            null,              // No location (Requirement 3)
            startTime,
            endTime,
            false // Not an all-day event
        )
        weekViewEvent.color = priorityColor
        return weekViewEvent
    }

    // Helper to get color Int from Priority enum name using ContextCompat
    private fun getPriorityColor(priority: String): Int {
        val colorResId = when (priority.uppercase()) {
            Task.Priority.HIGH.name -> R.color.priority_high
            Task.Priority.MEDIUM.name -> R.color.priority_medium
            Task.Priority.LOW.name -> R.color.priority_low
            Task.Priority.NONE.name -> R.color.priority_none
            else -> R.color.priority_none // Default color
        }
        return ContextCompat.getColor(context, colorResId)
    }

    // --- Navigation Methods ---
    fun navigateToPrevious() {
        _currentDate.value?.let { currentCal ->
            val newCal = currentCal.clone() as Calendar
            when (_currentViewMode.value) {
                ViewMode.DAY -> newCal.add(Calendar.DAY_OF_YEAR, -1)
                ViewMode.WEEK -> newCal.add(Calendar.DAY_OF_YEAR, -7) // Navigate a full week back
                else -> {} // Should not happen with current ViewMode enum
            }
            _currentDate.value = newCal // Update LiveData
            // Data load is triggered automatically by the observation of _stateTrigger
        }
    }

    fun navigateToNext() {
        _currentDate.value?.let { currentCal ->
            val newCal = currentCal.clone() as Calendar
            when (_currentViewMode.value) {
                ViewMode.DAY -> newCal.add(Calendar.DAY_OF_YEAR, 1)
                ViewMode.WEEK -> newCal.add(Calendar.DAY_OF_YEAR, 7) // Navigate a full week forward
                else -> {} // Should not happen
            }
            _currentDate.value = newCal // Update LiveData
            // Data load is triggered automatically by the observation of _stateTrigger
        }
    }

    fun navigateToToday() {
        val today = Calendar.getInstance()
        _currentDate.value = today // Update LiveData
        // Data load is triggered automatically by the observation of _stateTrigger
    }

    fun navigateToDate(year: Int, month: Int, dayOfMonth: Int) {
        val newDateCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month) // Month is 0-indexed from DatePickerDialog
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            // Clear time components to focus on the start of the day
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        _currentDate.value = newDateCal // Update LiveData
        // Data load is triggered automatically by the observation of _stateTrigger
    }

    // --- View Mode Method ---
    fun setViewMode(viewMode: ViewMode) {
        if (_currentViewMode.value != viewMode) {
            _currentViewMode.value = viewMode // Update LiveData
            // Data load is triggered automatically by the observation of _stateTrigger
            // The calculateDateRange logic will use the new mode and current date.
        }
    }

    // No longer need an explicit triggerDataLoad function due to _stateTrigger.emit in init
    // and flatMapLatest reacting to _stateTrigger updates caused by LiveData changes.
}