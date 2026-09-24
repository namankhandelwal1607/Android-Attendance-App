package com.attendance.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.AttendanceApplication
import com.attendance.app.ai.AgentQueryResult
import com.attendance.app.ai.AttendanceQueryAgent
import com.attendance.app.ai.DailySummaryResult
import com.attendance.app.ai.GroqAttendanceQueryAgent
import com.attendance.app.ai.StructuredFilter
import com.attendance.app.data.model.AttendanceRecord
import com.attendance.app.data.model.DailyAttendancePair
import com.attendance.app.data.model.Staff
import com.attendance.app.data.repository.KioskIdentificationState
import com.attendance.app.data.repository.StaffAttendanceTodayStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class EnrollUiState {
    object Idle : EnrollUiState()
    object Processing : EnrollUiState()
    data class Success(val staffId: Long) : EnrollUiState()
    data class Error(val message: String) : EnrollUiState()
}

enum class UserRole {
    NONE,
    ADMIN,
    STAFF
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AttendanceApplication
    val repository = app.repository
    private val faceNetHelper = app.faceNetHelper
    private val faceDetectorHelper = app.faceDetectorHelper
    private val locationHelper = app.locationHelper

    // User authentication state
    private val _currentUserRole = MutableStateFlow(UserRole.NONE)
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    private val _loggedInStaff = MutableStateFlow<Staff?>(null)
    val loggedInStaff: StateFlow<Staff?> = _loggedInStaff.asStateFlow()

    // Staff today's attendance status (Check In vs Check Out readiness + hours)
    private val _staffTodayStatus = MutableStateFlow<StaffAttendanceTodayStatus?>(null)
    val staffTodayStatus: StateFlow<StaffAttendanceTodayStatus?> = _staffTodayStatus.asStateFlow()

    // Data streams from Room
    val staffList: StateFlow<List<Staff>> = repository.allStaff.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val staffCount: StateFlow<Int> = repository.staffCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val allAttendanceRecords: StateFlow<List<AttendanceRecord>> = repository.allRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val todayAttendanceCount: StateFlow<Int> = repository.getTodayAttendanceCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val todayRecords: StateFlow<List<AttendanceRecord>> = repository.getTodayRecords().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Staff self-scoped history records
    val staffHistoryRecords: StateFlow<List<AttendanceRecord>> = _loggedInStaff.flatMapLatest { staff ->
        if (staff != null) {
            repository.getRecordsForStaff(staff.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Staff self-scoped daily pairs (Check In + Check Out + Hours Worked)
    val staffDailyPairs: StateFlow<List<DailyAttendancePair>> = staffHistoryRecords.map { records ->
        repository.groupRecordsIntoDailyPairs(records)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Staff Registration state
    private val _registerState = MutableStateFlow<EnrollUiState>(EnrollUiState.Idle)
    val registerState: StateFlow<EnrollUiState> = _registerState.asStateFlow()

    // Kiosk 1:N Attendance state
    private val _kioskState = MutableStateFlow<KioskIdentificationState>(KioskIdentificationState.Idle)
    val kioskState: StateFlow<KioskIdentificationState> = _kioskState.asStateFlow()

    // Selected staff for admin profile view
    private val _selectedStaff = MutableStateFlow<Staff?>(null)
    val selectedStaff: StateFlow<Staff?> = _selectedStaff.asStateFlow()

    private val _selectedStaffRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val selectedStaffRecords: StateFlow<List<AttendanceRecord>> = _selectedStaffRecords.asStateFlow()

    val selectedStaffDailyPairs: StateFlow<List<DailyAttendancePair>> = _selectedStaffRecords.map { records ->
        repository.groupRecordsIntoDailyPairs(records)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // COMBINABLE FILTERS FOR ADMIN ATTENDANCE RECORDS
    val filterStaffId = MutableStateFlow<Long?>(null)
    val filterDateFrom = MutableStateFlow<String?>(null) // YYYY-MM-DD
    val filterDateTo = MutableStateFlow<String?>(null)   // YYYY-MM-DD
    val filterTimeFrom = MutableStateFlow<String?>(null) // HH:mm
    val filterTimeTo = MutableStateFlow<String?>(null)   // HH:mm
    val filterEventType = MutableStateFlow<String>("EITHER") // EITHER, CHECK_IN, CHECK_OUT

    private val filterCriteria = combine(
        filterStaffId,
        filterDateFrom,
        filterDateTo,
        filterTimeFrom,
        filterTimeTo
    ) { staffId, dateFrom, dateTo, timeFrom, timeTo ->
        StructuredFilter(
            staffName = null,
            dateFrom = dateFrom,
            dateTo = dateTo,
            timeFrom = timeFrom,
            timeTo = timeTo,
            eventType = filterEventType.value
        ) to staffId
    }

    val filteredRecords: StateFlow<List<AttendanceRecord>> = combine(
        allAttendanceRecords,
        filterCriteria,
        filterEventType
    ) { records, (filter, staffId), eventType ->
        val effectiveFilter = filter.copy(eventType = eventType)
        val filteredByAttributes = GroqAttendanceQueryAgent.applyFilter(records, effectiveFilter)
        if (staffId != null) {
            filteredByAttributes.filter { it.staffId == staffId }
        } else {
            filteredByAttributes
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredDailyPairs: StateFlow<List<DailyAttendancePair>> = filteredRecords.map { records ->
        repository.groupRecordsIntoDailyPairs(records)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // AI ASSISTANT & DAILY SUMMARY STATE
    private val _aiQueryState = MutableStateFlow<AgentQueryResult?>(null)
    val aiQueryState: StateFlow<AgentQueryResult?> = _aiQueryState.asStateFlow()

    private val _isAiQueryLoading = MutableStateFlow(false)
    val isAiQueryLoading: StateFlow<Boolean> = _isAiQueryLoading.asStateFlow()

    private val _dailySummaryState = MutableStateFlow<DailySummaryResult?>(null)
    val dailySummaryState: StateFlow<DailySummaryResult?> = _dailySummaryState.asStateFlow()

    private val _isSummaryLoading = MutableStateFlow(false)
    val isSummaryLoading: StateFlow<Boolean> = _isSummaryLoading.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDatabaseIfNeeded()
        }
    }

    fun login(role: UserRole) {
        _currentUserRole.value = role
    }

    fun loginStaff(staff: Staff?) {
        _currentUserRole.value = UserRole.STAFF
        _loggedInStaff.value = staff
        if (staff != null) {
            loadStaffTodayStatus(staff)
        }
    }

    fun loadStaffTodayStatus(staff: Staff? = null) {
        val targetStaff = staff ?: _loggedInStaff.value ?: return
        viewModelScope.launch {
            val status = repository.getStaffTodayStatus(targetStaff)
            _staffTodayStatus.value = status
        }
    }

    fun logout() {
        _currentUserRole.value = UserRole.NONE
        _loggedInStaff.value = null
        _staffTodayStatus.value = null
        resetStates()
    }

    fun resetStates() {
        _registerState.value = EnrollUiState.Idle
        _kioskState.value = KioskIdentificationState.Idle
    }

    fun selectStaffForProfile(staff: Staff) {
        _selectedStaff.value = staff
        viewModelScope.launch {
            repository.getRecordsForStaff(staff.id).collect {
                _selectedStaffRecords.value = it
            }
        }
    }

    /**
     * ADMIN: Register new Staff member with mandatory face embedding
     */
    fun registerStaff(
        name: String,
        employeeId: String,
        username: String,
        pass: String,
        photoBitmap: Bitmap?
    ) {
        if (name.isBlank() || employeeId.isBlank() || username.isBlank() || pass.isBlank()) {
            _registerState.value = EnrollUiState.Error("Please fill in all staff details (Name, Employee ID, Username, Password).")
            return
        }

        if (photoBitmap == null) {
            _registerState.value = EnrollUiState.Error("Face selfie is required. Position face inside the oval guide and capture a selfie.")
            return
        }

        _registerState.value = EnrollUiState.Processing
        viewModelScope.launch {
            try {
                val croppedFace = faceDetectorHelper.cropPrimaryFace(photoBitmap)
                if (croppedFace == null) {
                    _registerState.value = EnrollUiState.Error("No clear face detected in selfie. Ensure good lighting and look directly into the camera.")
                    return@launch
                }

                val embedding = faceNetHelper.getFaceEmbedding(croppedFace)
                val result = repository.registerStaff(name, employeeId, username, pass, embedding, photoBitmap)
                if (result.isSuccess) {
                    _registerState.value = EnrollUiState.Success(result.getOrThrow())
                } else {
                    _registerState.value = EnrollUiState.Error(result.exceptionOrNull()?.message ?: "Failed to register staff")
                }
            } catch (e: Exception) {
                _registerState.value = EnrollUiState.Error(e.localizedMessage ?: "Unexpected error during registration")
            }
        }
    }

    /**
     * KIOSK STEP 1: Identify Staff via 1:N Facial Match
     */
    fun identifyKioskStaff(selfieBitmap: Bitmap) {
        _kioskState.value = KioskIdentificationState.Processing
        viewModelScope.launch {
            try {
                val result = repository.identifyStaffMember(selfieBitmap)
                _kioskState.value = result
            } catch (e: Exception) {
                _kioskState.value = KioskIdentificationState.Error(e.localizedMessage ?: "Failed to identify face")
            }
        }
    }

    /**
     * KIOSK STEP 2: Record Check In or Check Out for Identified Staff
     */
    fun recordKioskAction(staff: Staff, actionType: String, selfieBitmap: Bitmap, matchPercentage: Int = 95) {
        _kioskState.value = KioskIdentificationState.Processing
        viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                val result = repository.recordAttendanceAction(staff, actionType, selfieBitmap, location, matchPercentage)
                _kioskState.value = result
            } catch (e: Exception) {
                _kioskState.value = KioskIdentificationState.Error(e.localizedMessage ?: "Failed to record attendance")
            }
        }
    }

    // Legacy auto-action wrapper
    fun markKioskAttendance(selfieBitmap: Bitmap) {
        identifyKioskStaff(selfieBitmap)
    }

    fun resetKioskAttendance() {
        _kioskState.value = KioskIdentificationState.Idle
    }

    // FILTER HELPERS FOR ATTENDANCE RECORDS
    fun setStaffFilter(id: Long?) {
        filterStaffId.value = id
    }

    fun setDateFilter(date: String?) {
        filterDateFrom.value = date
        filterDateTo.value = date
    }

    fun setDateRangeFilter(from: String?, to: String?) {
        filterDateFrom.value = from
        filterDateTo.value = to
    }

    fun setTimeFilter(from: String?, to: String?, eventType: String = "EITHER") {
        filterTimeFrom.value = from
        filterTimeTo.value = to
        filterEventType.value = eventType
    }

    fun setTimeRangeFilter(from: String?, to: String?, eventType: String = "EITHER") =
        setTimeFilter(from, to, eventType)

    fun clearAllFilters() {
        filterStaffId.value = null
        filterDateFrom.value = null
        filterDateTo.value = null
        filterTimeFrom.value = null
        filterTimeTo.value = null
        filterEventType.value = "EITHER"
    }

    // AI ASSISTANT ACTIONS
    fun askAiAssistant(question: String) {
        if (question.isBlank()) return
        _isAiQueryLoading.value = true
        viewModelScope.launch {
            try {
                val result = repository.queryAiAttendance(question)
                _aiQueryState.value = result
            } catch (e: Exception) {
                // Heuristic error object
            } finally {
                _isAiQueryLoading.value = false
            }
        }
    }

    fun clearAiQuery() {
        _aiQueryState.value = null
    }

    fun generateDailySummary() {
        _isSummaryLoading.value = true
        viewModelScope.launch {
            try {
                val summary = repository.generateDailySummary()
                _dailySummaryState.value = summary
            } catch (_: Exception) {
            } finally {
                _isSummaryLoading.value = false
            }
        }
    }

    fun deleteStaff(staff: Staff) {
        viewModelScope.launch {
            repository.deleteStaff(staff)
        }
    }
}
