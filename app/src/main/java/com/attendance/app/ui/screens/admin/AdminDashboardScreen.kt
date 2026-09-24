package com.attendance.app.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.attendance.app.data.model.AttendanceRecord
import com.attendance.app.data.model.Staff
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AppViewModel,
    onNavigateToAddStaff: () -> Unit,
    onNavigateToStaffProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val staffList by viewModel.staffList.collectAsStateWithLifecycle()
    val staffCount by viewModel.staffCount.collectAsStateWithLifecycle()
    val todayRecords by viewModel.todayRecords.collectAsStateWithLifecycle()
    val todayAttendanceCount by viewModel.todayAttendanceCount.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Today's Present, 1 = All Staff
    var searchQuery by remember { mutableStateOf("") }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }

    // Map staffId to today's record for quick lookup
    val todayStaffMap = remember(todayRecords) {
        todayRecords.associateBy { it.staffId }
    }

    val filteredStaff = remember(staffList, searchQuery) {
        if (searchQuery.isBlank()) staffList
        else staffList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.employeeId.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredTodayRecords = remember(todayRecords, searchQuery) {
        if (searchQuery.isBlank()) todayRecords
        else todayRecords.filter {
            it.staffName.contains(searchQuery, ignoreCase = true) ||
            it.employeeId.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Admin Dashboard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        val todayStr = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date())
                        Text(
                            text = todayStr,
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out",
                            tint = Slate600
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddStaff,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enrol Staff", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Slate50)
        ) {
            // Stats Row: Total, Present, Absent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Enrolled
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Staff", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$staffCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }

                // Present Today
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = EmeraldLight),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Present Today", fontSize = 11.sp, color = EmeraldDark, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$todayAttendanceCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                    }
                }

                // Absent Today
                val absentCount = (staffCount - todayAttendanceCount).coerceAtLeast(0)
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Not Marked", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$absentCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    }
                }
            }

            // Tabs: Present Today vs All Staff
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = PrimaryBlue,
                divider = { HorizontalDivider(color = Slate200) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Who is Present",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedTab == 0) PrimaryBlue else Slate200
                            ) {
                                Text(
                                    text = "${todayRecords.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 0) Color.White else Slate600,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Staff Directory",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedTab == 1) PrimaryBlue else Slate200
                            ) {
                                Text(
                                    text = "$staffCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == 1) Color.White else Slate600,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by name or employee ID...", fontSize = 13.sp, color = Slate400) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate400)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Slate200
                ),
                singleLine = true
            )

            // Content according to selected tab
            if (selectedTab == 0) {
                // TAB 0: WHO IS PRESENT TODAY
                if (filteredTodayRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Slate100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.EventBusy, contentDescription = null, tint = Slate400, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No check-ins today yet" else "No matching check-ins",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "When staff mark attendance using face verification, their details, time, and selfie appear here.",
                                fontSize = 13.sp,
                                color = Slate500,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredTodayRecords, key = { it.id }) { record ->
                            PresentStaffCard(
                                record = record,
                                onClick = {
                                    val staff = staffList.firstOrNull { it.id == record.staffId }
                                    if (staff != null) {
                                        viewModel.selectStaffForProfile(staff)
                                        onNavigateToStaffProfile()
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // TAB 1: ALL STAFF DIRECTORY
                if (filteredStaff.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Slate100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PersonOff, contentDescription = null, tint = Slate400, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No staff members enrolled" else "No matching staff found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap the 'Enrol Staff' button below to add your first employee.",
                                    fontSize = 13.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredStaff, key = { it.id }) { staff ->
                            val isPresentToday = todayStaffMap.containsKey(staff.id)
                            val todayRecord = todayStaffMap[staff.id]

                            DirectoryStaffCard(
                                staff = staff,
                                isPresentToday = isPresentToday,
                                checkInTime = todayRecord?.formattedShortTime,
                                onClick = {
                                    viewModel.selectStaffForProfile(staff)
                                    onNavigateToStaffProfile()
                                },
                                onDelete = { staffToDelete = staff }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    staffToDelete?.let { staff ->
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = { Text("Delete Employee", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${staff.name} (${staff.employeeId})? This will delete their enrolled face embedding and all attendance records.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStaff(staff)
                        staffToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RoseRed)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { staffToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Card showing someone who is CONFIRMED PRESENT TODAY
 */
@Composable
fun PresentStaffCard(
    record: AttendanceRecord,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selfie taken during today's attendance
            val selfieFile = File(record.selfiePath)
            if (selfieFile.exists()) {
                AsyncImage(
                    model = selfieFile,
                    contentDescription = "Today's Selfie",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Slate400)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.staffName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    // Check-in Time Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldLight
                    ) {
                        Text(
                            text = record.formattedShortTime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "ID: ${record.employeeId}",
                    fontSize = 12.sp,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Location address
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = RoseRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = record.address,
                        fontSize = 11.sp,
                        color = Slate600,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Slate300,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Card for All Staff Directory with Present/Not Marked Status Pill
 */
@Composable
fun DirectoryStaffCard(
    staff: Staff,
    isPresentToday: Boolean,
    checkInTime: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile thumbnail
            val photoFile = staff.photoPath?.let { File(it) }
            if (photoFile != null && photoFile.exists()) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = staff.name.take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = staff.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "ID: ${staff.employeeId}",
                    fontSize = 12.sp,
                    color = Slate500
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Attendance Status Badge for today
                if (isPresentToday) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldGreen))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Present (${checkInTime ?: ""})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldDark
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slate100
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Slate400))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Not Marked Today",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate600
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Slate400
                )
            }
        }
    }
}
