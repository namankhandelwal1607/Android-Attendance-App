package com.attendance.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.AttendanceApplication
import com.attendance.app.data.model.AttendanceRecord
import com.attendance.app.data.model.Staff
import com.attendance.app.ml.FaceNetModelHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class EnrollUiState {
    object Idle : EnrollUiState()
    object Processing : EnrollUiState()
    data class Success(val staffId: Long) : EnrollUiState()
    data class Error(val message: String) : EnrollUiState()
}

sealed class AttendanceUiState {
    object Idle : AttendanceUiState()
    object Processing : AttendanceUiState()
    data class Success(
        val record: AttendanceRecord,
        val matchPercentage: Int
    ) : AttendanceUiState()
    data class Error(val message: String) : AttendanceUiState()
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

    // Enrolment state
    private val _enrollState = MutableStateFlow<EnrollUiState>(EnrollUiState.Idle)
    val enrollState: StateFlow<EnrollUiState> = _enrollState.asStateFlow()

    // Attendance state
    private val _attendanceState = MutableStateFlow<AttendanceUiState>(AttendanceUiState.Idle)
    val attendanceState: StateFlow<AttendanceUiState> = _attendanceState.asStateFlow()

    // Selected staff for profile view
    private val _selectedStaff = MutableStateFlow<Staff?>(null)
    val selectedStaff: StateFlow<Staff?> = _selectedStaff.asStateFlow()

    private val _selectedStaffRecords = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    val selectedStaffRecords: StateFlow<List<AttendanceRecord>> = _selectedStaffRecords.asStateFlow()

    fun login(role: UserRole) {
        _currentUserRole.value = role
    }

    fun logout() {
        _currentUserRole.value = UserRole.NONE
        resetStates()
    }

    fun resetStates() {
        _enrollState.value = EnrollUiState.Idle
        _attendanceState.value = AttendanceUiState.Idle
    }

    fun selectStaffForProfile(staff: Staff) {
        _selectedStaff.value = staff
        viewModelScope.launch {
            repository.getRecordsForStaff(staff.id).collect { records ->
                _selectedStaffRecords.value = records
            }
        }
    }

    fun enrollStaff(name: String, employeeId: String, photoBitmap: Bitmap) {
        if (name.isBlank() || employeeId.isBlank()) {
            _enrollState.value = EnrollUiState.Error("Name and Employee ID cannot be empty")
            return
        }

        _enrollState.value = EnrollUiState.Processing
        viewModelScope.launch {
            try {
                // 1. Detect and crop face
                val croppedFace = faceDetectorHelper.cropPrimaryFace(photoBitmap)
                if (croppedFace == null) {
                    _enrollState.value = EnrollUiState.Error("No face detected. Please ensure good lighting and center face in the frame.")
                    return@launch
                }

                // 2. Generate embedding
                val embedding = faceNetHelper.getFaceEmbedding(croppedFace)

                // 3. Save to database
                val result = repository.enrollStaff(name, employeeId, embedding, photoBitmap)
                if (result.isSuccess) {
                    _enrollState.value = EnrollUiState.Success(result.getOrThrow())
                } else {
                    _enrollState.value = EnrollUiState.Error(result.exceptionOrNull()?.message ?: "Failed to enroll staff")
                }
            } catch (e: Exception) {
                _enrollState.value = EnrollUiState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun markAttendance(staff: Staff, selfieBitmap: Bitmap) {
        _attendanceState.value = AttendanceUiState.Processing
        viewModelScope.launch {
            try {
                // 1. Detect and crop face from selfie
                val croppedFace = faceDetectorHelper.cropPrimaryFace(selfieBitmap)
                if (croppedFace == null) {
                    _attendanceState.value = AttendanceUiState.Error("No face detected in selfie. Please look directly at the camera.")
                    return@launch
                }

                // 2. Generate embedding for current selfie
                val currentEmbedding = faceNetHelper.getFaceEmbedding(croppedFace)
                val enrolledEmbedding = staff.getEmbeddingArray()

                if (enrolledEmbedding.isEmpty()) {
                    _attendanceState.value = AttendanceUiState.Error("No enrolled face found for this staff member.")
                    return@launch
                }

                // 3. Compare embeddings
                val similarity = faceNetHelper.calculateCosineSimilarity(enrolledEmbedding, currentEmbedding)
                val matchPercentage = (similarity * 100).toInt()

                if (similarity < FaceNetModelHelper.MATCH_THRESHOLD) {
                    _attendanceState.value = AttendanceUiState.Error(
                        "Face does not match enrolled profile! Match confidence: $matchPercentage% (Required: ${(FaceNetModelHelper.MATCH_THRESHOLD * 100).toInt()}%)"
                    )
                    return@launch
                }

                // 4. Capture GPS location
                val location = locationHelper.getCurrentLocation()

                // 5. Store attendance record
                val result = repository.recordAttendance(
                    staff = staff,
                    selfieBitmap = selfieBitmap,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = location.readableAddress,
                    confidence = similarity
                )

                if (result.isSuccess) {
                    val recordId = result.getOrThrow()
                    val record = AttendanceRecord(
                        id = recordId,
                        staffId = staff.id,
                        staffName = staff.name,
                        employeeId = staff.employeeId,
                        timestamp = System.currentTimeMillis(),
                        selfiePath = "",
                        latitude = location.latitude,
                        longitude = location.longitude,
                        address = location.readableAddress,
                        confidenceScore = similarity
                    )
                    _attendanceState.value = AttendanceUiState.Success(record, matchPercentage)
                } else {
                    _attendanceState.value = AttendanceUiState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to save attendance record"
                    )
                }
            } catch (e: Exception) {
                _attendanceState.value = AttendanceUiState.Error(e.message ?: "Failed to process face recognition")
            }
        }
    }

    fun deleteStaff(staff: Staff) {
        viewModelScope.launch {
            repository.deleteStaff(staff)
        }
    }
}
