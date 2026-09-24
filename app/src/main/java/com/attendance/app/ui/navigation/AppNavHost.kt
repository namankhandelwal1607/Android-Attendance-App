package com.attendance.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.attendance.app.ui.screens.LoginScreen
import com.attendance.app.ui.screens.admin.AdminDashboardScreen
import com.attendance.app.ui.screens.admin.RegisterStaffScreen
import com.attendance.app.ui.screens.admin.StaffProfileScreen
import com.attendance.app.ui.screens.kiosk.KioskAttendanceScreen
import com.attendance.app.ui.screens.staff.StaffHistoryScreen
import com.attendance.app.ui.viewmodel.AppViewModel

@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                appViewModel = viewModel,
                onNavigateToAdmin = {
                    navController.navigate(Screen.AdminDashboard.route)
                },
                onNavigateToStaff = {
                    navController.navigate(Screen.StaffHistory.route)
                },
                onNavigateToKiosk = {
                    navController.navigate(Screen.KioskAttendance.route)
                }
            )
        }

        composable(Screen.KioskAttendance.route) {
            KioskAttendanceScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(
                viewModel = viewModel,
                onNavigateToAddStaff = {
                    navController.navigate(Screen.RegisterStaff.route)
                },
                onNavigateToStaffProfile = {
                    navController.navigate(Screen.StaffProfile.route)
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }

        composable(Screen.RegisterStaff.route) {
            RegisterStaffScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.StaffProfile.route) {
            StaffProfileScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.StaffHistory.route) {
            StaffHistoryScreen(
                viewModel = viewModel,
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0)
                    }
                }
            )
        }
    }
}
