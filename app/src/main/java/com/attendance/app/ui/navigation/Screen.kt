package com.attendance.app.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object AdminDashboard : Screen("admin_dashboard")
    object AddStaff : Screen("add_staff")
    object StaffProfile : Screen("staff_profile")
    object StaffAttendance : Screen("staff_attendance")
}
