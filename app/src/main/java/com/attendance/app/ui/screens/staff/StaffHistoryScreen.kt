package com.attendance.app.ui.screens.staff

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.attendance.app.data.model.DailyAttendancePair
import com.attendance.app.ui.components.CameraCaptureView
import com.attendance.app.ui.screens.kiosk.AttendanceStatTile
import com.attendance.app.ui.screens.kiosk.ConcentricRingButton
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffHistoryScreen(
    viewModel: AppViewModel,
    onLogout: () -> Unit
) {
    val loggedInStaff by viewModel.loggedInStaff.collectAsStateWithLifecycle()
    val staffTodayStatus by viewModel.staffTodayStatus.collectAsStateWithLifecycle()
    val dailyPairs by viewModel.staffDailyPairs.collectAsStateWithLifecycle()

    val staff = loggedInStaff
    var isCameraOpen by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(staff) {
        if (staff != null) {
            viewModel.loadStaffTodayStatus(staff)
        }
    }

    // Live clock ticker
    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            currentDateStr = SimpleDateFormat("MMM -dd yyyy • EEEE", Locale.getDefault()).format(now)
            delay(1000)
        }
    }

    if (isCameraOpen && staff != null) {
        CameraCaptureView(
            title = "Verify face to ${if (pendingAction == "CHECK_IN") "Check In" else "Check Out"}",
            onImageCaptured = { bitmap ->
                isCameraOpen = false
                val action = pendingAction ?: if (staffTodayStatus?.canCheckOut == true) "CHECK_OUT" else "CHECK_IN"
                viewModel.recordKioskAction(staff, action, bitmap)
                viewModel.loadStaffTodayStatus(staff)
            },
            onError = { isCameraOpen = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Attendance", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestSlate900) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log Out", tint = ForestSlate600)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftOffWhite)
            )
        },
        containerColor = SoftOffWhite
    ) { padding ->
        if (staff == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No staff session active", color = ForestSlate500)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header (matching reference Screen 2: "Hey Rose" + avatar)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Hey ${staff.name}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestSlate900
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Good day! Mark your attendance",
                                fontSize = 13.sp,
                                color = ForestSlate500
                            )
                        }

                        val photoFile = staff.photoPath?.let { File(it) }
                        if (photoFile != null && photoFile.exists()) {
                            AsyncImage(
                                model = photoFile,
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, ForestGreenLight, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(ForestGreenLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = staff.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = ForestGreen
                                )
                            }
                        }
                    }
                }

                // Big Clock Display (matching reference: 09:00 AM)
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentTimeStr.ifEmpty { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) },
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestSlate900,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentDateStr.ifEmpty { SimpleDateFormat("MMM -dd yyyy • EEEE", Locale.getDefault()).format(Date()) },
                            fontSize = 13.sp,
                            color = ForestSlate500,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Concentric Ring Button (matching reference screen 2)
                item {
                    val status = staffTodayStatus
                    val isCheckIn = status?.canCheckIn ?: true

                    ConcentricRingButton(
                        text = if (isCheckIn) "Check in" else "Check out",
                        subtext = if (isCheckIn) "Tap to record entry" else "Tap to record departure",
                        isCheckIn = isCheckIn,
                        onClick = {
                            pendingAction = if (isCheckIn) "CHECK_IN" else "CHECK_OUT"
                            isCameraOpen = true
                        }
                    )
                }

                // 3 Stat Tiles below button (matching reference screen 2)
                item {
                    val status = staffTodayStatus
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardWhite)
                            .border(1.dp, ForestSlate200, RoundedCornerShape(20.dp))
                            .padding(vertical = 18.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AttendanceStatTile(
                            icon = Icons.Default.Login,
                            label = "Check in",
                            value = status?.formattedCheckInTime ?: "--:--"
                        )
                        AttendanceStatTile(
                            icon = Icons.Default.Logout,
                            label = "Check out",
                            value = status?.formattedCheckOutTime ?: "--:--"
                        )
                        AttendanceStatTile(
                            icon = Icons.Default.CheckCircle,
                            label = "Total Hrs",
                            value = status?.formattedHours ?: "--:--"
                        )
                    }
                }

                // History Section Header
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attendance History",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestSlate900
                        )
                        Text(
                            text = "${dailyPairs.size} day(s)",
                            fontSize = 12.sp,
                            color = ForestSlate500
                        )
                    }
                }

                // History List: Daily Pairs with Check In + Check Out + Hours Worked
                if (dailyPairs.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = CardWhite,
                            border = BorderStroke(1.dp, ForestSlate200)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.EventBusy, contentDescription = null, tint = ForestSlate400, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No past attendance logged yet.", fontSize = 13.sp, color = ForestSlate500)
                            }
                        }
                    }
                } else {
                    items(dailyPairs) { pair ->
                        StaffDailyPairCard(pair = pair)
                    }
                }
            }
        }
    }
}

@Composable
fun StaffDailyPairCard(pair: DailyAttendancePair) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, ForestSlate200)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Date & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pair.formattedDate,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestSlate900
                )
                Surface(
                    color = if (pair.checkOut != null) ForestGreenLight else AmberLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = pair.statusText,
                        color = if (pair.checkOut != null) ForestGreen else AmberWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = ForestSlate100)
            Spacer(modifier = Modifier.height(12.dp))

            // Two-column Check In & Check Out Details + Total Hours
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Check In column
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check In", fontSize = 11.sp, color = ForestSlate500, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pair.checkIn?.formattedShortTime ?: "--:--",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestSlate900
                    )
                    pair.checkIn?.let {
                        Text(it.address.take(20), fontSize = 10.sp, color = ForestSlate400, maxLines = 1)
                    }
                }

                // Check Out column
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check Out", fontSize = 11.sp, color = ForestSlate500, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pair.checkOut?.formattedShortTime ?: "--:--",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestSlate900
                    )
                    pair.checkOut?.let {
                        Text(it.address.take(20), fontSize = 10.sp, color = ForestSlate400, maxLines = 1)
                    }
                }

                // Total Hours column
                Column(horizontalAlignment = Alignment.End) {
                    Text("Hours", fontSize = 11.sp, color = ForestSlate500, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pair.formattedHours,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                }
            }
        }
    }
}
