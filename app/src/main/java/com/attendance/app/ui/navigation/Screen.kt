package com.attendance.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object KioskAttendance : Screen("kiosk_attendance")
    object AdminDashboard : Screen("admin_dashboard")
    object RegisterStaff : Screen("register_staff")
    object StaffProfile : Screen("staff_profile")
    object StaffHistory : Screen("staff_history")
    
    // Legacy aliases
    object AddStaff : Screen("register_staff")
    object StaffAttendance : Screen("kiosk_attendance")
}
