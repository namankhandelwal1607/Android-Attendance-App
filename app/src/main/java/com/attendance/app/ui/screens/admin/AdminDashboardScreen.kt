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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.attendance.app.data.model.DailyAttendancePair
import com.attendance.app.data.model.Staff
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class WeeklyCalendarDay(
    val dateStr: String,
    val dayOfWeek: String,
    val dayOfMonth: String,
    val isToday: Boolean
)

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
    val filteredDailyPairs by viewModel.filteredDailyPairs.collectAsStateWithLifecycle()
    val todayAttendanceCount by viewModel.todayAttendanceCount.collectAsStateWithLifecycle()

    val filterStaffId by viewModel.filterStaffId.collectAsStateWithLifecycle()
    val filterDateFrom by viewModel.filterDateFrom.collectAsStateWithLifecycle()
    val filterDateTo by viewModel.filterDateTo.collectAsStateWithLifecycle()
    val filterTimeFrom by viewModel.filterTimeFrom.collectAsStateWithLifecycle()
    val filterTimeTo by viewModel.filterTimeTo.collectAsStateWithLifecycle()
    val filterEventType by viewModel.filterEventType.collectAsStateWithLifecycle()

    val aiQueryState by viewModel.aiQueryState.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiQueryLoading.collectAsStateWithLifecycle()
    val dailySummary by viewModel.dailySummaryState.collectAsStateWithLifecycle()
    val isSummaryLoading by viewModel.isSummaryLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Staff, 1 = Records
    var searchQuery by remember { mutableStateOf("") }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }
    var aiInputText by remember { mutableStateOf("") }

    // Dialog & Sheet States
    var showAiSheet by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // Weekly date strip reference calendar
    var weekOffset by remember { mutableStateOf(0) }
    val weekDays = remember(weekOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val todayCal = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(todayCal.time)
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val numFormat = SimpleDateFormat("d", Locale.getDefault())
        val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val list = mutableListOf<WeeklyCalendarDay>()
        for (i in 0 until 7) {
            val key = keyFormat.format(cal.time)
            list.add(
                WeeklyCalendarDay(
                    dateStr = key,
                    dayOfWeek = dayFormat.format(cal.time),
                    dayOfMonth = numFormat.format(cal.time),
                    isToday = (key == todayStr)
                )
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        list
    }

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
        containerColor = SoftOffWhite,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Admin Portal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        val todayStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date())
                        Text(
                            text = todayStr,
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Log Out",
                            tint = TextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftOffWhite)
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onNavigateToAddStaff,
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Register Staff", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        bottomBar = {
            // Pill-shaped Bottom Navigation Bar with Centered Floating AI Assistant Action (Part D)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(32.dp),
                color = CardWhite,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, Color(0xFFE2ECE7))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // TAB 0: STAFF
                    IconButton(
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Staff",
                                tint = if (selectedTab == 0) ForestGreen else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Staff",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) ForestGreen else TextMuted
                            )
                        }
                    }

                    // CENTER FLOATING AI ASSISTANT BUTTON (Part D)
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ForestGreen)
                            .clickable { showAiSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Groq AI Assistant",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // TAB 1: ATTENDANCE RECORDS
                    IconButton(
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = "Records",
                                tint = if (selectedTab == 1) ForestGreen else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Records",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) ForestGreen else TextMuted
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Stats Row: Total Staff, Marked Today, Total Records
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, Color(0xFFE2ECE7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Registered", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$staffCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                }
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = AccentMint),
                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Active Today", fontSize = 11.sp, color = ForestGreen, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$todayAttendanceCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    }
                }
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, Color(0xFFE2ECE7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Total Logged", fontSize = 11.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${allRecords.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    }
                }
            }

            // ==================== TAB 0: ALL STAFF MANAGEMENT ====================
            if (selectedTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, ID, or username...", fontSize = 13.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = Color(0xFFE2ECE7)
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
                                    .background(Color(0xFFE2ECE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PersonOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(30.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No staff registered yet" else "No matching staff found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Register Staff' below to enrol your first staff member.",
                                    fontSize = 13.sp,
                                    color = TextMuted
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

            // ==================== TAB 1: ATTENDANCE RECORDS (WEEKLY STRIP + FILTERS + PAIRED CARDS) ====================
            else if (selectedTab == 1) {
                // WEEKLY DATE STRIP (Matching Screen 3)
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, Color(0xFFE2ECE7))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Week strip navigation header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = ForestGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val headerMonthStr = remember(weekOffset) {
                                    val cal = Calendar.getInstance()
                                    cal.add(Calendar.WEEK_OF_YEAR, weekOffset)
                                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
                                }
                                Text(
                                    text = headerMonthStr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (weekOffset != 0) {
                                    TextButton(
                                        onClick = { weekOffset = 0 },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Current Week", fontSize = 11.sp, color = ForestGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                                IconButton(
                                    onClick = { weekOffset -= 1 },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Week", tint = TextDark)
                                }
                                IconButton(
                                    onClick = { weekOffset += 1 },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Week", tint = TextDark)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mon - Sun Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            weekDays.forEach { day ->
                                val isSelected = filterDateFrom == day.dateStr && filterDateTo == day.dateStr
                                val isCurrentDay = day.isToday

                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (isSelected) {
                                                viewModel.setDateFilter(null)
                                            } else {
                                                viewModel.setDateFilter(day.dateStr)
                                            }
                                        }
                                        .background(
                                            if (isSelected) ForestGreen
                                            else if (isCurrentDay) AccentMint.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = day.dayOfWeek.take(3),
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = day.dayOfMonth,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TextDark
                                    )
                                    if (isCurrentDay) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White else ForestGreen)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // FILTER CHIPS ROW (Part C: Calendar DatePicker & TimePicker)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // All / Reset Chip
                    item {
                        FilterChip(
                            selected = !hasActiveFilters,
                            onClick = { viewModel.clearAllFilters() },
                            label = { Text("All", fontSize = 11.sp) }
                        )
                    }

                    // Calendar DatePicker Trigger Chip
                    item {
                        val hasDateFilter = filterDateFrom != null
                        FilterChip(
                            selected = hasDateFilter,
                            onClick = { showDatePickerDialog = true },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = {
                                Text(
                                    text = if (filterDateFrom != null) {
                                        if (filterDateTo != null && filterDateTo != filterDateFrom) "${filterDateFrom}..${filterDateTo}"
                                        else filterDateFrom ?: "Pick Date"
                                    } else "Pick Date",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }

                    // Time Range Picker Trigger Chip
                    item {
                        val hasTimeFilter = filterTimeFrom != null
                        FilterChip(
                            selected = hasTimeFilter,
                            onClick = { showTimePickerDialog = true },
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = {
                                Text(
                                    text = if (hasTimeFilter) "${filterTimeFrom} - ${filterTimeTo ?: ""}" else "Time Range",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }

                    // Staff Filter Chips
                    items(staffList) { staff ->
                        FilterChip(
                            selected = filterStaffId == staff.id,
                            onClick = {
                                if (filterStaffId == staff.id) viewModel.setStaffFilter(null)
                                else viewModel.setStaffFilter(staff.id)
                            },
                            label = { Text(staff.name, fontSize = 11.sp) }
                        )
                    }

                    // Clear Filters Action
                    if (hasActiveFilters) {
                        item {
                            TextButton(
                                onClick = { viewModel.clearAllFilters() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Clear", fontSize = 11.sp, color = RoseRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // PAIRED ATTENDANCE RECORDS LIST (Part B: paired check-in + check-out & total hours)
                if (filteredDailyPairs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (hasActiveFilters) "No records match these filters" else "No attendance recorded yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )
                            if (hasActiveFilters) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(onClick = { viewModel.clearAllFilters() }) {
                                    Text("Reset All Filters", color = ForestGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredDailyPairs, key = { "${it.staffId}_${it.dayKey}" }) { pair ->
                            AdminDailyPairRecordCard(pair = pair)
                        }
                    }
                }
            }
        }
    }

    // ==================== CALENDAR DATE PICKER DIALOG (Part C) ====================
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        viewModel.setDateFilter(sdf.format(Date(millis)))
                    }
                    showDatePickerDialog = false
                }) {
                    Text("Select Date", color = ForestGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = ForestGreen,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = ForestGreen
                )
            )
        }
    }

    // ==================== TIME RANGE PICKER DIALOG (Part C) ====================
    if (showTimePickerDialog) {
        TimeRangePickerDialog(
            initialTimeFrom = filterTimeFrom,
            initialTimeTo = filterTimeTo,
            initialEventType = filterEventType,
            onApply = { from, to, eventType ->
                viewModel.setTimeRangeFilter(from, to, eventType)
            },
            onDismiss = { showTimePickerDialog = false }
        )
    }

    // ==================== AI ASSISTANT BOTTOM SHEET (Part D & E) ====================
    if (showAiSheet) {
        val matchingRecords = aiQueryState?.matchingRecords ?: emptyList()
        val matchingPairs = remember(matchingRecords) {
            viewModel.repository.groupRecordsIntoDailyPairs(matchingRecords)
        }

        ModalBottomSheet(
            onDismissRequest = { showAiSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CardWhite,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ForestGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Attendance Intelligence", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                            Text("Powered by Groq AI", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    IconButton(onClick = { showAiSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Daily Executive Summary Card
                    item {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = SoftOffWhite),
                            border = BorderStroke(1.dp, Color(0xFFE2ECE7))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Insights, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Daily Executive Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                    }

                                    Button(
                                        onClick = { viewModel.generateDailySummary() },
                                        enabled = !isSummaryLoading,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
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

                                Spacer(modifier = Modifier.height(8.dp))

                                if (dailySummary != null) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = CardWhite,
                                        border = BorderStroke(1.dp, Color(0xFFE2ECE7))
                                    ) {
                                        MarkdownText(
                                            text = dailySummary!!.summaryMarkdown,
                                            fontSize = 13.sp,
                                            color = TextDark,
                                            lineHeight = 19.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Tap 'Generate' to summarize today's attendance logs, present counts, and check-in timing patterns.",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    // Prompt Suggestions Chips
                    item {
                        Column {
                            Text("Suggested Queries:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                            Spacer(modifier = Modifier.height(6.dp))
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
                                            aiInputText = "Who is currently checked in?"
                                            viewModel.askAiAssistant("Who is currently checked in?")
                                        },
                                        label = { Text("Currently checked in?", fontSize = 11.sp) }
                                    )
                                }
                                item {
                                    SuggestionChip(
                                        onClick = {
                                            aiInputText = "How many staff are registered?"
                                            viewModel.askAiAssistant("How many staff are registered?")
                                        },
                                        label = { Text("Total staff count", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // Natural Language Search Input
                    item {
                        OutlinedTextField(
                            value = aiInputText,
                            onValueChange = { aiInputText = it },
                            placeholder = { Text("Ask any attendance question...", fontSize = 13.sp, color = TextMuted) },
                            trailingIcon = {
                                if (isAiLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ForestGreen)
                                } else {
                                    IconButton(
                                        onClick = { viewModel.askAiAssistant(aiInputText) },
                                        enabled = aiInputText.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Query", tint = ForestGreen)
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.askAiAssistant(aiInputText) }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // AI Query Results & Answer (Rich Markdown + Matching Records)
                    aiQueryState?.let { queryResult ->
                        item {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
                                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("AI Answer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                        }
                                        IconButton(onClick = { viewModel.clearAiQuery() }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    MarkdownText(text = queryResult.aiAnswer, fontSize = 13.sp, color = TextDark, lineHeight = 19.sp)

                                    queryResult.structuredFilter?.let { filter ->
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AccentMint
                                        ) {
                                            Text(
                                                text = "Parsed Filter: " + listOfNotNull(
                                                    filter.staffName?.let { "Staff: $it" },
                                                    filter.dateFrom?.let { "Date: $it" },
                                                    filter.timeFrom?.let { "Time: $it to ${filter.timeTo ?: ""}" }
                                                ).joinToString(" • ").ifEmpty { "General Query" },
                                                fontSize = 11.sp,
                                                color = ForestGreen,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Matching Records (${queryResult.matchingRecords.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextDark
                                    )
                                }
                            }
                        }

                        if (matchingPairs.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF7FAF8),
                                    border = BorderStroke(1.dp, Color(0xFFE2ECE7))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("No matching attendance records in the database.", fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            }
                        } else {
                            items(matchingPairs) { pair ->
                                AdminDailyPairRecordCard(pair = pair)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Staff Confirmation Dialog
    staffToDelete?.let { staff ->
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            containerColor = CardWhite,
            title = { Text("Delete Staff Member", fontWeight = FontWeight.Bold, color = TextDark) },
            text = { Text("Are you sure you want to remove ${staff.name} (${staff.employeeId})? This will delete their registered credentials and attendance history.", color = TextMuted) },
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
                    Text("Cancel", color = TextMuted)
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Color(0xFFE2ECE7))
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
                        .background(AccentMint),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = staff.name.take(1).uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = staff.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${staff.employeeId} • @${staff.username}",
                    fontSize = 12.sp,
                    color = ForestGreen,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (lastCheckInTime != null) "Last: $lastCheckInTime" else "No check-ins yet",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (totalCheckIns > 0) AccentMint else Color(0xFFF0F4F2)
                ) {
                    Text(
                        text = "$totalCheckIns Check-in${if (totalCheckIns == 1) "" else "s"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totalCheckIns > 0) ForestGreen else TextMuted,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * PAIRED ATTENDANCE RECORD CARD (Part B: check-in, check-out, and total hours worked)
 */
@Composable
fun AdminDailyPairRecordCard(pair: DailyAttendancePair) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Color(0xFFE2ECE7))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Staff Name, ID, Date, and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = pair.staffName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "ID: ${pair.employeeId} • ${pair.formattedDate}",
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (pair.checkOut != null) AccentMint else Color(0xFFFFF3E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (pair.checkOut != null) Icons.Default.CheckCircle else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (pair.checkOut != null) ForestGreen else Color(0xFFE65100),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (pair.checkOut != null) "Completed" else "Checked In",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pair.checkOut != null) ForestGreen else Color(0xFFE65100)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F4F2))
            Spacer(modifier = Modifier.height(10.dp))

            // Check In & Check Out Pair Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Check In Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = SoftOffWhite,
                    border = BorderStroke(1.dp, Color(0xFFE2ECE7))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selfie = pair.checkIn?.selfiePath?.let { File(it) }
                        if (selfie != null && selfie.exists()) {
                            AsyncImage(
                                model = selfie,
                                contentDescription = "Check-in selfie",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentMint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Login, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("CHECK IN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                            Text(
                                text = pair.checkIn?.formattedShortTime ?: "--:--",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            pair.checkIn?.address?.let {
                                Text(
                                    text = it,
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Check Out Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = SoftOffWhite,
                    border = BorderStroke(1.dp, Color(0xFFE2ECE7))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val selfie = pair.checkOut?.selfiePath?.let { File(it) }
                        if (selfie != null && selfie.exists()) {
                            AsyncImage(
                                model = selfie,
                                contentDescription = "Check-out selfie",
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (pair.checkOut != null) Color(0xFFFFEBEE) else Color(0xFFF0F4F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = null,
                                    tint = if (pair.checkOut != null) RoseRed else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "CHECK OUT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pair.checkOut != null) RoseRed else TextMuted
                            )
                            Text(
                                text = pair.checkOut?.formattedShortTime ?: "Pending",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pair.checkOut != null) TextDark else TextMuted
                            )
                            pair.checkOut?.address?.let {
                                Text(
                                    text = it,
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Total Hours Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Total Hours Worked:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (pair.totalHours > 0) ForestGreen else Color(0xFFF0F4F2)
                ) {
                    Text(
                        text = if (pair.totalHours > 0) pair.formattedHours else "In Progress",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pair.totalHours > 0) Color.White else TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * TIME RANGE PICKER DIALOG (Part C)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeRangePickerDialog(
    initialTimeFrom: String?,
    initialTimeTo: String?,
    initialEventType: String,
    onApply: (from: String?, to: String?, eventType: String) -> Unit,
    onDismiss: () -> Unit
) {
    var timeFrom by remember { mutableStateOf(initialTimeFrom ?: "09:00") }
    var timeTo by remember { mutableStateOf(initialTimeTo ?: "17:00") }
    var selectedEventType by remember { mutableStateOf(initialEventType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = {
            Text("Filter by Time Range", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select time range and event type:", fontSize = 12.sp, color = TextMuted)

                // Quick presets
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = timeFrom == "09:00" && timeTo == "10:00",
                        onClick = { timeFrom = "09:00"; timeTo = "10:00" },
                        label = { Text("9–10 AM", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = timeFrom == "10:00" && timeTo == "12:00",
                        onClick = { timeFrom = "10:00"; timeTo = "12:00" },
                        label = { Text("10–12 PM", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = timeFrom == "14:00" && timeTo == "18:00",
                        onClick = { timeFrom = "14:00"; timeTo = "18:00" },
                        label = { Text("2–6 PM", fontSize = 11.sp) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = timeFrom,
                        onValueChange = { timeFrom = it },
                        label = { Text("Start (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = timeTo,
                        onValueChange = { timeTo = it },
                        label = { Text("End (HH:mm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Text("Filter Event Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedEventType == "EITHER",
                        onClick = { selectedEventType = "EITHER" },
                        label = { Text("Either") }
                    )
                    FilterChip(
                        selected = selectedEventType == "CHECK_IN",
                        onClick = { selectedEventType = "CHECK_IN" },
                        label = { Text("Check In") }
                    )
                    FilterChip(
                        selected = selectedEventType == "CHECK_OUT",
                        onClick = { selectedEventType = "CHECK_OUT" },
                        label = { Text("Check Out") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(timeFrom.trim().ifEmpty { null }, timeTo.trim().ifEmpty { null }, selectedEventType)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onApply(null, null, "EITHER")
                onDismiss()
            }) {
                Text("Clear Filter", color = RoseRed)
            }
        }
    )
}

/**
 * MARKDOWN TEXT RENDERER
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextDark,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 19.sp,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val annotatedString = remember(text, color) {
        buildAnnotatedString {
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
