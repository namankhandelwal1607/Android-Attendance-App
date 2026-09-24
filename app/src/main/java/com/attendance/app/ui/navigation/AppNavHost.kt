package com.attendance.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.attendance.app.ui.screens.LoginScreen
import com.attendance.app.ui.screens.admin.AddStaffScreen
import com.attendance.app.ui.screens.admin.AdminDashboardScreen
import com.attendance.app.ui.screens.admin.StaffProfileScreen
import com.attendance.app.ui.screens.staff.StaffAttendanceScreen
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
                    navController.navigate(Screen.StaffAttendance.route)
                }
            )
        }

        composable(Screen.AdminDashboard.route) {
            AdminDashboardScreen(
                viewModel = viewModel,
                onNavigateToAddStaff = {
                    navController.navigate(Screen.AddStaff.route)
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

        composable(Screen.AddStaff.route) {
            AddStaffScreen(
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

        composable(Screen.StaffAttendance.route) {
            StaffAttendanceScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
