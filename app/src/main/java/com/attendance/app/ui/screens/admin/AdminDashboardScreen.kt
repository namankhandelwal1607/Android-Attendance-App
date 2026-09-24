package com.attendance.app.ui.screens.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
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
    val allRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val todayAttendanceCount by viewModel.todayAttendanceCount.collectAsStateWithLifecycle()

    val filterStaffId by viewModel.filterStaffId.collectAsStateWithLifecycle()
    val filterDateFrom by viewModel.filterDateFrom.collectAsStateWithLifecycle()
    val filterDateTo by viewModel.filterDateTo.collectAsStateWithLifecycle()
    val filterTimeFrom by viewModel.filterTimeFrom.collectAsStateWithLifecycle()
    val filterTimeTo by viewModel.filterTimeTo.collectAsStateWithLifecycle()

    val aiQueryState by viewModel.aiQueryState.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiQueryLoading.collectAsStateWithLifecycle()
    val dailySummary by viewModel.dailySummaryState.collectAsStateWithLifecycle()
    val isSummaryLoading by viewModel.isSummaryLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Staff, 1 = Records, 2 = AI
    var searchQuery by remember { mutableStateOf("") }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }
    var aiInputText by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Map each staff member to their total check-in count and latest check-in
    val staffSummaryMap = remember(staffList, allRecords) {
        staffList.associate { staff ->
            val staffRecords = allRecords.filter { it.staffId == staff.id }
            val count = staffRecords.size
            val latest = staffRecords.maxByOrNull { it.timestamp }
            staff.id to Pair(count, latest)
        }
    }

    val filteredStaff = remember(staffList, searchQuery) {
        if (searchQuery.isBlank()) staffList
        else staffList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.employeeId.contains(searchQuery, ignoreCase = true) ||
            it.username.contains(searchQuery, ignoreCase = true)
        }
    }

    val hasActiveFilters = filterStaffId != null || filterDateFrom != null || filterDateTo != null || filterTimeFrom != null || filterTimeTo != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Admin Portal",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
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
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register Staff", fontWeight = FontWeight.SemiBold)
                    }
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
            // Stats Row: Total Staff, Marked Today, Total Logged
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Registered", fontSize = 11.sp, color = Slate500)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$staffCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = EmeraldLight),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Marked Today", fontSize = 11.sp, color = EmeraldDark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$todayAttendanceCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EmeraldDark)
                    }
                }
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Records", fontSize = 11.sp, color = Slate500)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${allRecords.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }
            }

            // Primary Navigation Tabs
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
                            Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("All Staff ($staffCount)", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Records (${filteredRecords.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Assistant", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                )
            }

            // TAB 0: ALL STAFF LIST WITH QUICK SUMMARY
            if (selectedTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, ID, or username...", fontSize = 13.sp, color = Slate400) },
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Slate200
                    ),
                    singleLine = true
                )

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
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Slate100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PersonOff, contentDescription = null, tint = Slate400, modifier = Modifier.size(30.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No staff registered yet" else "No matching staff found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Register Staff' below to enrol your first staff member.",
                                    fontSize = 13.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredStaff, key = { it.id }) { staff ->
                            val summary = staffSummaryMap[staff.id]
                            val totalCheckIns = summary?.first ?: 0
                            val latestRecord = summary?.second

                            AdminStaffCard(
                                staff = staff,
                                totalCheckIns = totalCheckIns,
                                lastCheckInTime = latestRecord?.formattedTime,
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

            // TAB 1: ATTENDANCE RECORDS WITH COMBINABLE FILTERS
            else if (selectedTab == 1) {
                // Filter controls card
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FilterAlt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Filter Attendance Records", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                            }
                            if (hasActiveFilters) {
                                TextButton(
                                    onClick = { viewModel.clearAllFilters() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Clear All", fontSize = 12.sp, color = RoseRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Combinable Filter Pills
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Date Presets
                            item {
                                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                FilterChip(
                                    selected = filterDateFrom == todayStr && filterDateTo == todayStr,
                                    onClick = {
                                        if (filterDateFrom == todayStr) viewModel.setDateFilter(null)
                                        else viewModel.setDateFilter(todayStr)
                                    },
                                    label = { Text("Today") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filterTimeFrom == "09:00" && filterTimeTo == "10:00",
                                    onClick = {
                                        if (filterTimeFrom == "09:00") viewModel.setTimeRangeFilter(null, null)
                                        else viewModel.setTimeRangeFilter("09:00", "10:00")
                                    },
                                    label = { Text("9:00 AM - 10:00 AM") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filterTimeFrom == "10:00" && filterTimeTo == "12:00",
                                    onClick = {
                                        if (filterTimeFrom == "10:00") viewModel.setTimeRangeFilter(null, null)
                                        else viewModel.setTimeRangeFilter("10:00", "12:00")
                                    },
                                    label = { Text("10:00 AM - 12:00 PM") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = showFilterSheet,
                                    onClick = { showFilterSheet = !showFilterSheet },
                                    label = { Text(if (showFilterSheet) "Close Custom" else "Custom Filters...") },
                                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }

                        // Custom Filters Expandable Area
                        AnimatedVisibility(visible = showFilterSheet) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = Slate100)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Staff Filter Selector
                                Text("Filter by Staff Member:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        FilterChip(
                                            selected = filterStaffId == null,
                                            onClick = { viewModel.setStaffFilter(null) },
                                            label = { Text("All Staff") }
                                        )
                                    }
                                    items(staffList) { staff ->
                                        FilterChip(
                                            selected = filterStaffId == staff.id,
                                            onClick = {
                                                if (filterStaffId == staff.id) viewModel.setStaffFilter(null)
                                                else viewModel.setStaffFilter(staff.id)
                                            },
                                            label = { Text(staff.name) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Date Range Inputs
                                Text("Date Range (YYYY-MM-DD):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = filterDateFrom ?: "",
                                        onValueChange = { viewModel.setDateRangeFilter(it.takeIf { it.isNotBlank() }, filterDateTo) },
                                        placeholder = { Text("From (e.g. 2026-09-01)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = filterDateTo ?: "",
                                        onValueChange = { viewModel.setDateRangeFilter(filterDateFrom, it.takeIf { it.isNotBlank() }) },
                                        placeholder = { Text("To (e.g. 2026-09-30)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Time Range Inputs (HH:mm)
                                Text("Time-of-Day Range (HH:mm):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = filterTimeFrom ?: "",
                                        onValueChange = { viewModel.setTimeRangeFilter(it.takeIf { it.isNotBlank() }, filterTimeTo) },
                                        placeholder = { Text("Start (e.g. 09:00)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    OutlinedTextField(
                                        value = filterTimeTo ?: "",
                                        onValueChange = { viewModel.setTimeRangeFilter(filterTimeFrom, it.takeIf { it.isNotBlank() }) },
                                        placeholder = { Text("End (e.g. 17:00)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Records List
                if (filteredRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (hasActiveFilters) "No records match these filters" else "No attendance recorded yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                            if (hasActiveFilters) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(onClick = { viewModel.clearAllFilters() }) {
                                    Text("Reset Filters")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredRecords, key = { it.id }) { record ->
                            AdminAttendanceRecordCard(record = record)
                        }
                    }
                }
            }

            // TAB 2: AI ASSISTANT & DAILY INSIGHTS (GROQ)
            else if (selectedTab == 2) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Daily Summary Card
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Insights, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Daily Executive Summary", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                                    }

                                    Button(
                                        onClick = { viewModel.generateDailySummary() },
                                        enabled = !isSummaryLoading,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        if (isSummaryLoading) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Generate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (dailySummary != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = PrimaryBlueLight,
                                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f))
                                    ) {
                                        MarkdownText(
                                            text = dailySummary!!.summaryMarkdown,
                                            fontSize = 13.sp,
                                            color = Slate800,
                                            lineHeight = 19.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Tap 'Generate' to have Groq AI analyze today's attendance logs, present counts, and check-in timing patterns.",
                                        fontSize = 13.sp,
                                        color = Slate500
                                    )
                                }
                            }
                        }
                    }

                    // Natural Language Query Card
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Ask About Attendance (Groq AI)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick suggestion chips
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        SuggestionChip(
                                            onClick = {
                                                aiInputText = "Who marked attendance today?"
                                                viewModel.askAiAssistant("Who marked attendance today?")
                                            },
                                            label = { Text("Who marked today?", fontSize = 11.sp) }
                                        )
                                    }
                                    item {
                                        SuggestionChip(
                                            onClick = {
                                                aiInputText = "Who checked in between 9 and 10 AM?"
                                                viewModel.askAiAssistant("Who checked in between 9 and 10 AM?")
                                            },
                                            label = { Text("Check-ins 9–10 AM", fontSize = 11.sp) }
                                        )
                                    }
                                    item {
                                        SuggestionChip(
                                            onClick = {
                                                aiInputText = "Show attendance for this week"
                                                viewModel.askAiAssistant("Show attendance for this week")
                                            },
                                            label = { Text("This week's records", fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = aiInputText,
                                    onValueChange = { aiInputText = it },
                                    placeholder = { Text("Ask a question in natural language...", fontSize = 13.sp, color = Slate400) },
                                    trailingIcon = {
                                        if (isAiLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        } else {
                                            IconButton(
                                                onClick = { viewModel.askAiAssistant(aiInputText) },
                                                enabled = aiInputText.isNotBlank()
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = "Query", tint = PrimaryBlue)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { viewModel.askAiAssistant(aiInputText) }),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // AI Query Results Section
                    aiQueryState?.let { queryResult ->
                        item {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("AI Answer & Matching Records", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                                        }
                                        IconButton(onClick = { viewModel.clearAiQuery() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    MarkdownText(text = queryResult.aiAnswer, fontSize = 13.sp, color = Slate700, lineHeight = 19.sp)

                                    queryResult.structuredFilter?.let { filter ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Slate100
                                        ) {
                                            Text(
                                                text = "Parsed Filter: " + listOfNotNull(
                                                    filter.staffName?.let { "Staff: $it" },
                                                    filter.dateFrom?.let { "Date: $it" },
                                                    filter.timeFrom?.let { "Time: $it to ${filter.timeTo ?: ""}" }
                                                ).joinToString(" • ").ifEmpty { "All Records" },
                                                fontSize = 11.sp,
                                                color = Slate600,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Matching Records (${queryResult.matchingRecords.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900
                                    )
                                }
                            }
                        }

                        if (queryResult.matchingRecords.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate100,
                                    border = BorderStroke(1.dp, Slate200)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Slate500, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("No check-ins matched these criteria in the database.", fontSize = 13.sp, color = Slate600)
                                    }
                                }
                            }
                        } else {
                            items(queryResult.matchingRecords) { record ->
                                AdminAttendanceRecordCard(record = record)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Staff Dialog
    staffToDelete?.let { staff ->
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = { Text("Delete Staff Member", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${staff.name} (${staff.employeeId})? This will delete their registered credentials and attendance history.") },
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

@Composable
fun AdminStaffCard(
    staff: Staff,
    totalCheckIns: Int,
    lastCheckInTime: String?,
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
            val photoFile = staff.photoPath?.let { File(it) }
            if (photoFile != null && photoFile.exists()) {
                AsyncImage(
                    model = photoFile,
                    contentDescription = "Staff Enrolled Photo",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PrimaryBlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = staff.name.take(1).uppercase(),
                        fontSize = 20.sp,
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
                    text = "ID: ${staff.employeeId} • @${staff.username}",
                    fontSize = 12.sp,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lastCheckInTime != null) "Last: $lastCheckInTime" else "No check-ins yet",
                    fontSize = 11.sp,
                    color = Slate500
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (totalCheckIns > 0) EmeraldLight else Slate100
                ) {
                    Text(
                        text = "$totalCheckIns Check-in${if (totalCheckIns == 1) "" else "s"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totalCheckIns > 0) EmeraldDark else Slate600,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Slate400, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun AdminAttendanceRecordCard(record: AttendanceRecord) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
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
            val selfieFile = File(record.selfiePath)
            if (selfieFile.exists()) {
                AsyncImage(
                    model = selfieFile,
                    contentDescription = "Check-in Selfie",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
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
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldLight
                    ) {
                        Text(
                            text = record.formattedShortTime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldDark,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${record.employeeId} • ${record.formattedDate}",
                    fontSize = 12.sp,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = RoseRed, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = record.address, fontSize = 11.sp, color = Slate600)
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${(record.confidenceScore * 100).toInt()}% Match Confidence",
                    fontSize = 10.sp,
                    color = Slate400
                )
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Slate800,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 19.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val annotatedString = remember(text, color) {
        buildAnnotatedString {
            // Regex to match **bold** or *italic*
            val pattern = Regex("""(\*\*(.*?)\*\*|\*(.*?)\*)""")
            var lastIndex = 0
            for (match in pattern.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }
                val boldText = match.groups[2]?.value
                val italicText = match.groups[3]?.value
                if (boldText != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                        append(boldText)
                    }
                } else if (italicText != null) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color)) {
                        append(italicText)
                    }
                }
                lastIndex = end
            }
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight
    )
}

