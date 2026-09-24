package com.attendance.app.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffProfileScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedStaff by viewModel.selectedStaff.collectAsStateWithLifecycle()
    val dailyPairs by viewModel.selectedStaffDailyPairs.collectAsStateWithLifecycle()

    val staff = selectedStaff

    Scaffold(
        containerColor = SoftOffWhite,
        topBar = {
            TopAppBar(
                title = { Text("Staff Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftOffWhite)
            )
        }
    ) { padding ->
        if (staff == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No staff selected", color = TextMuted)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Profile Header Card
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, Color(0xFFE2ECE7))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val photoFile = staff.photoPath?.let { File(it) }
                        if (photoFile != null && photoFile.exists()) {
                            AsyncImage(
                                model = photoFile,
                                contentDescription = "Staff Enrolled Photo",
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(AccentMint),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = staff.name.take(1).uppercase(),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = staff.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Employee ID: ${staff.employeeId}",
                            fontSize = 13.sp,
                            color = ForestGreen,
                            fontWeight = FontWeight.Medium
                        )
                        if (staff.username.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Username: @${staff.username}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        val enrolledDateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(staff.enrolledAt))
                        Text(
                            text = "Enrolled on $enrolledDateStr",
                            fontSize = 12.sp,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFF0F4F2))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${dailyPairs.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text(text = "Days Logged", fontSize = 11.sp, color = TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val totalHoursSum = dailyPairs.sumOf { it.totalHours }
                                val h = totalHoursSum.toInt()
                                val m = ((totalHoursSum - h) * 60).toInt()
                                Text(text = "${h}h ${m}m", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                Text(text = "Total Hours", fontSize = 11.sp, color = TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Active", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                                Text(text = "Status", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }

                // Attendance Logs Section
                Text(
                    text = "Attendance History (${dailyPairs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                if (dailyPairs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No attendance records logged yet.", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(dailyPairs, key = { it.dayKey }) { pair ->
                            AdminDailyPairRecordCard(pair = pair)
                        }
                    }
                }
            }
        }
    }
}
