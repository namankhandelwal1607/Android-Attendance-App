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
import com.attendance.app.data.model.Staff
import com.attendance.app.data.repository.KioskAttendanceResult
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
    private val repository = app.repository
    private val faceNetHelper = app.faceNetHelper
    private val faceDetectorHelper = app.faceDetectorHelper
    private val locationHelper = app.locationHelper

    // User authentication state
    private val _currentUserRole = MutableStateFlow(UserRole.NONE)
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    private val _loggedInStaff = MutableStateFlow<Staff?>(null)
    val loggedInStaff: StateFlow<Staff?> = _loggedInStaff.asStateFlow()

    // Staff list & stats
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

    val todayAttendanceCount: StateFlow<Int> = repository.getTodayAttendanceCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val allAttendanceRecords: StateFlow<List<AttendanceRecord>> = repository.allRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
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

    // Staff Registration state
    private val _registerState = MutableStateFlow<EnrollUiState>(EnrollUiState.Idle)
    val registerState: StateFlow<EnrollUiState> = _registerState.asStateFlow()

    // Kiosk 1:N Attendance state
    private val _kioskState = MutableStateFlow<KioskAttendanceResult>(KioskAttendanceResult.Idle)
    val kioskState: StateFlow<KioskAttendanceResult> = _kioskState.asStateFlow()

    // Selected staff for admin profile view
    private val _selectedStaff = MutableStateFlow<Staff?>(null)
    val selectedStaff: StateFlow<Staff?> = _selectedStaff.asStateFlow()

    private val _selectedStaffRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val selectedStaffRecords: StateFlow<List<AttendanceRecord>> = _selectedStaffRecords.asStateFlow()

    // COMBINABLE FILTERS FOR ADMIN ATTENDANCE RECORDS
    val filterStaffId = MutableStateFlow<Long?>(null)
    val filterDateFrom = MutableStateFlow<String?>(null) // YYYY-MM-DD
    val filterDateTo = MutableStateFlow<String?>(null)   // YYYY-MM-DD
    val filterTimeFrom = MutableStateFlow<String?>(null) // HH:mm
    val filterTimeTo = MutableStateFlow<String?>(null)   // HH:mm

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
            timeTo = timeTo
        ) to staffId
    }

    val filteredRecords: StateFlow<List<AttendanceRecord>> = combine(
        allAttendanceRecords,
        filterCriteria
    ) { records, (filter, staffId) ->
        val filteredByAttributes = GroqAttendanceQueryAgent.applyFilter(records, filter)
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
    }

    fun logout() {
        _currentUserRole.value = UserRole.NONE
        _loggedInStaff.value = null
        resetStates()
    }

    fun resetStates() {
        _registerState.value = EnrollUiState.Idle
        _kioskState.value = KioskAttendanceResult.Idle
    }

    fun selectStaffForProfile(staff: Staff) {
        _selectedStaff.value = staff
        viewModelScope.launch {
            repository.getRecordsForStaff(staff.id).collect { records ->
                _selectedStaffRecords.value = records
            }
        }
    }

    /**
     * Registers a new staff member. Face enrolment is strictly REQUIRED.
     */
    fun registerStaff(
        name: String,
        employeeId: String,
        username: String,
        pass: String,
        photoBitmap: Bitmap?
    ) {
        if (name.isBlank() || employeeId.isBlank() || username.isBlank() || pass.isBlank()) {
            _registerState.value = EnrollUiState.Error("Full Name, Employee ID, Username, and Password are all required.")
            return
        }

        if (photoBitmap == null) {
            _registerState.value = EnrollUiState.Error("Face selfie is required. Position face inside the oval guide and capture a selfie.")
            return
        }

        _registerState.value = EnrollUiState.Processing
        viewModelScope.launch {
            try {
                // 1. Detect and crop face
                val croppedFace = faceDetectorHelper.cropPrimaryFace(photoBitmap)
                if (croppedFace == null) {
                    _registerState.value = EnrollUiState.Error("No clear face detected in selfie. Ensure good lighting and look directly into the camera.")
                    return@launch
                }

                // 2. Generate 192-d facial embedding
                val embedding = faceNetHelper.getFaceEmbedding(croppedFace)

                // 3. Save staff record in Room
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
     * KIOSK: 1:N Face Identification Attendance
     */
    fun markKioskAttendance(selfieBitmap: Bitmap) {
        _kioskState.value = KioskAttendanceResult.Processing
        viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                val result = repository.identifyAndMarkAttendance(selfieBitmap, location)
                _kioskState.value = result
            } catch (e: Exception) {
                _kioskState.value = KioskAttendanceResult.Error(e.localizedMessage ?: "Failed to mark attendance")
            }
        }
    }

    fun resetKioskAttendance() {
        _kioskState.value = KioskAttendanceResult.Idle
    }

    // FILTER HELPERS
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

    fun setTimeRangeFilter(from: String?, to: String?) {
        filterTimeFrom.value = from
        filterTimeTo.value = to
    }

    fun clearAllFilters() {
        filterStaffId.value = null
        filterDateFrom.value = null
        filterDateTo.value = null
        filterTimeFrom.value = null
        filterTimeTo.value = null
    }

    // AI ASSISTANT METHODS
    fun askAiAssistant(question: String) {
        if (question.isBlank()) return
        _isAiQueryLoading.value = true
        viewModelScope.launch {
            val result = repository.queryAiAttendance(question)
            _aiQueryState.value = result
            _isAiQueryLoading.value = false
        }
    }

    fun clearAiQuery() {
        _aiQueryState.value = null
    }

    fun generateDailySummary() {
        _isSummaryLoading.value = true
        viewModelScope.launch {
            val result = repository.generateDailySummary()
            _dailySummaryState.value = result
            _isSummaryLoading.value = false
        }
    }

    fun deleteStaff(staff: Staff) {
        viewModelScope.launch {
            repository.deleteStaff(staff)
        }
    }
}
