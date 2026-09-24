package com.attendance.app.ui.screens.staff

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.attendance.app.data.model.Staff
import com.attendance.app.ui.components.CameraCaptureView
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import com.attendance.app.ui.viewmodel.AttendanceUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAttendanceScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val staffList by viewModel.staffList.collectAsStateWithLifecycle()
    val attendanceState by viewModel.attendanceState.collectAsStateWithLifecycle()

    var selectedStaff by remember { mutableStateOf<Staff?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var isCameraOpen by remember { mutableStateOf(false) }
    var capturedSelfieBitmap by remember { mutableStateOf<Bitmap?>(null) }

    fun checkCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun checkLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val cameraGranted = results[Manifest.permission.CAMERA] ?: checkCameraPermission()
        if (cameraGranted) {
            isCameraOpen = true
        }
    }

    fun startVerification() {
        val hasCamera = checkCameraPermission()
        val hasLocation = checkLocationPermission()

        if (hasCamera && hasLocation) {
            isCameraOpen = true
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Auto-select first staff if available
    LaunchedEffect(staffList) {
        if (selectedStaff == null && staffList.isNotEmpty()) {
            selectedStaff = staffList.first()
        }
    }

    if (isCameraOpen) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraCaptureView(
                title = "Hold steady: ${selectedStaff?.name ?: ""}",
                onImageCaptured = { bitmap ->
                    capturedSelfieBitmap = bitmap
                    isCameraOpen = false
                    selectedStaff?.let { staff ->
                        viewModel.markAttendance(staff, bitmap)
                    }
                },
                onError = {
                    isCameraOpen = false
                }
            )

            IconButton(
                onClick = { isCameraOpen = false },
                modifier = Modifier
                    .padding(top = 40.dp, start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = Color.White)
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Staff Attendance", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Slate900) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Slate700)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Slate50)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (staffList.isEmpty()) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape).background(Slate100),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.GroupOff, contentDescription = null, tint = Slate400, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No Enrolled Staff Found", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "An Admin must first add staff and enrol their face before attendance can be marked.",
                                fontSize = 13.sp,
                                color = Slate500,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Return to Home")
                            }
                        }
                    }
                } else {
                    // Profile Selection Card
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Select Employee",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            ExposedDropdownMenuBox(
                                expanded = isDropdownExpanded,
                                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedStaff?.let { "${it.name} (${it.employeeId})" } ?: "Select staff...",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Slate50,
                                        unfocusedContainerColor = Slate50,
                                        focusedBorderColor = PrimaryBlue,
                                        unfocusedBorderColor = Slate200
                                    )
                                )

                                ExposedDropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false }
                                ) {
                                    staffList.forEach { staff ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    val photoFile = staff.photoPath?.let { File(it) }
                                                    if (photoFile != null && photoFile.exists()) {
                                                        AsyncImage(
                                                            model = photoFile,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(32.dp).clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                    }
                                                    Column {
                                                        Text(staff.name, fontWeight = FontWeight.SemiBold, color = Slate900)
                                                        Text("ID: ${staff.employeeId}", fontSize = 12.sp, color = Slate500)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedStaff = staff
                                                isDropdownExpanded = false
                                                viewModel.resetStates()
                                            }
                                        )
                                    }
                                }
                            }

                            // Selected Employee Banner
                            selectedStaff?.let { staff ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Slate100, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val photoFile = staff.photoPath?.let { File(it) }
                                    if (photoFile != null && photoFile.exists()) {
                                        AsyncImage(
                                            model = photoFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp).clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(44.dp).clip(CircleShape).background(PrimaryBlue),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(staff.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(staff.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                                        Text("Enrolled face active • Ready for scan", fontSize = 12.sp, color = EmeraldDark)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Verification Action Card (when not in success state)
                    if (attendanceState !is AttendanceUiState.Success) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Slate200),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Mark Attendance",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Position your face in front of the camera. The app will verify your identity and capture current GPS location.",
                                    fontSize = 13.sp,
                                    color = Slate500,
                                    modifier = Modifier.align(Alignment.Start)
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                if (attendanceState is AttendanceUiState.Processing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = PrimaryBlue,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Verifying face & fetching location...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PrimaryBlue
                                    )
                                } else {
                                    Button(
                                        onClick = { startVerification() },
                                        enabled = selectedStaff != null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open Camera & Check In", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Result Display: Error or Success
                    when (val state = attendanceState) {
                        is AttendanceUiState.Error -> {
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = RoseLight),
                                border = BorderStroke(1.dp, RoseRed.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = RoseRed,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Verification Failed",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RoseRed
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.message,
                                        fontSize = 13.sp,
                                        color = Slate700,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.resetStates()
                                            startVerification()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, RoseRed),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseRed)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Try Again")
                                    }
                                }
                            }
                        }

                        is AttendanceUiState.Success -> {
                            val record = state.record
                            OutlinedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                                border = BorderStroke(1.5.dp, EmeraldGreen)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Success Header
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(EmeraldLight),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Attendance Marked", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                            Text(record.staffName, fontSize = 13.sp, color = Slate600)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = EmeraldLight
                                        ) {
                                            Text(
                                                text = "${state.matchPercentage}% Match",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldDark,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Captured selfie thumbnail
                                    capturedSelfieBitmap?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Verified Selfie",
                                            modifier = Modifier
                                                .size(110.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                    }

                                    HorizontalDivider(color = Slate200)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Details list
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Time
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate500, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = record.formattedTime,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Slate800
                                            )
                                        }

                                        // Location
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = RoseRed, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = record.address,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Slate800
                                                )
                                                if (record.latitude != 0.0 || record.longitude != 0.0) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "GPS: %.5f, %.5f".format(record.latitude, record.longitude),
                                                        fontSize = 11.sp,
                                                        color = Slate500
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Button(
                                        onClick = {
                                            viewModel.resetStates()
                                            onNavigateBack()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) {
                                        Text("Done", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}
