package com.attendance.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.attendance.app.AttendanceApplication
import com.attendance.app.data.model.Staff
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginResult {
    object Idle : LoginResult()
    object Loading : LoginResult()
    data class AdminSuccess(val username: String) : LoginResult()
    data class StaffSuccess(val staff: Staff?) : LoginResult()
    data class Error(val message: String) : LoginResult()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AttendanceApplication
    private val repository = app.repository

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _loginResult = MutableStateFlow<LoginResult>(LoginResult.Idle)
    val loginResult: StateFlow<LoginResult> = _loginResult.asStateFlow()

    fun onUsernameChange(value: String) {
        _username.value = value
        if (_loginResult.value is LoginResult.Error) {
            _loginResult.value = LoginResult.Idle
        }
    }

    fun onPasswordChange(value: String) {
        _password.value = value
        if (_loginResult.value is LoginResult.Error) {
            _loginResult.value = LoginResult.Idle
        }
    }

    fun setCredentials(user: String, pass: String) {
        _username.value = user
        _password.value = pass
        _loginResult.value = LoginResult.Idle
    }

    fun login(onSuccess: (UserRole, Staff?) -> Unit) {
        val u = _username.value.trim()
        val p = _password.value.trim()

        if (u.isBlank() || p.isBlank()) {
            _loginResult.value = LoginResult.Error("Invalid username or password")
            return
        }

        _loginResult.value = LoginResult.Loading
        viewModelScope.launch {
            try {
                // Ensure seeded records are loaded
                repository.seedDatabaseIfNeeded()

                // 1. Check Admin credentials (dummy admin/admin123 or database)
                if ((u.equals("admin", ignoreCase = true) && p == "admin123") ||
                    repository.authenticateAdmin(u, p)
                ) {
                    _loginResult.value = LoginResult.AdminSuccess(u)
                    onSuccess(UserRole.ADMIN, null)
                    return@launch
                }


                // 3. Check seeded/Room staff accounts (by username or employee ID)
                val matchedStaff = repository.authenticateStaff(u, p)
                if (matchedStaff != null) {
                    _loginResult.value = LoginResult.StaffSuccess(matchedStaff)
                    onSuccess(UserRole.STAFF, matchedStaff)
                    return@launch
                }

                // 4. Failed login
                _loginResult.value = LoginResult.Error("Invalid username or password")
            } catch (e: Exception) {
                _loginResult.value = LoginResult.Error("Invalid username or password")
            }
        }
    }

    fun resetState() {
        _username.value = ""
        _password.value = ""
        _loginResult.value = LoginResult.Idle
    }
}
