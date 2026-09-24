package com.attendance.app.ui.screens.kiosk

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.attendance.app.data.model.AttendanceRecord
import com.attendance.app.data.repository.KioskIdentificationState
import com.attendance.app.data.repository.StaffAttendanceTodayStatus
import com.attendance.app.ui.components.CameraCaptureView
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskAttendanceScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val kioskState by viewModel.kioskState.collectAsStateWithLifecycle()
    var capturedSelfie by remember { mutableStateOf<Bitmap?>(null) }
    var selectedAction by remember { mutableStateOf<String?>(null) }

    fun checkCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun checkLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var hasPermissions by remember {
        mutableStateOf(checkCameraPermission() && checkLocationPermission())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = (results[Manifest.permission.CAMERA] ?: checkCameraPermission()) &&
                ((results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false) || (results[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false))
    }

    LaunchedEffect(Unit) {
        viewModel.resetKioskAttendance()
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Face Attendance Kiosk", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestSlate900) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ForestSlate700)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftOffWhite)
            )
        },
        containerColor = SoftOffWhite
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = kioskState) {
                is KioskIdentificationState.Idle, is KioskIdentificationState.Processing -> {
                    if (hasPermissions) {
                        CameraCaptureView(
                            title = "Look directly at the camera to identify yourself",
                            onImageCaptured = { bitmap ->
                                capturedSelfie = bitmap
                                viewModel.identifyKioskStaff(bitmap)
                            },
                            onError = { onNavigateBack() }
                        )

                        if (state is KioskIdentificationState.Processing) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = CardWhite)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(color = ForestGreen, strokeWidth = 3.dp)
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text("Matching 1:N face embeddings...", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ForestSlate900)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Camera permission required", color = ForestSlate500)
                        }
                    }
                }

                // STEP 2: FACE MATCHED -> REFERENCE SCREEN 2 LAYOUT
                is KioskIdentificationState.Identified -> {
                    val staff = state.staff
                    val status = state.status
                    val expectedAction = selectedAction ?: status.expectedAction
                    val isCheckIn = expectedAction == AttendanceRecord.TYPE_CHECK_IN

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Header with Hey Name and Avatar (matching reference screen 2)
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
                                    text = if (status.canCheckIn) "Good day! Time to check in." else "You are currently checked in.",
                                    fontSize = 13.sp,
                                    color = ForestSlate500
                                )
                            }

                            // Staff Avatar
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

                        Spacer(modifier = Modifier.height(24.dp))

                        // Big Time & Date Display (matching reference "09:00 AM" layout)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentTimeStr.ifEmpty { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) },
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestSlate900,
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentDateStr.ifEmpty { SimpleDateFormat("MMM -dd yyyy • EEEE", Locale.getDefault()).format(Date()) },
                                fontSize = 13.sp,
                                color = ForestSlate500,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Large Concentric Ring Button (matching reference screen 2)
                        ConcentricRingButton(
                            text = if (isCheckIn) "Check in" else "Check out",
                            subtext = if (isCheckIn) "Tap to record entry" else "Tap to record departure",
                            isCheckIn = isCheckIn,
                            enabled = (isCheckIn && status.canCheckIn) || (!isCheckIn && status.canCheckOut),
                            onClick = {
                                val bitmap = capturedSelfie ?: state.selfieBitmap
                                viewModel.recordKioskAction(staff, expectedAction, bitmap, state.matchPercentage)
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Switcher Tabs (Check In vs Check Out with validation cues)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(CircleShape)
                                .background(ForestSlate100)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(enabled = status.canCheckIn) {
                                        selectedAction = AttendanceRecord.TYPE_CHECK_IN
                                    },
                                shape = CircleShape,
                                color = if (isCheckIn) ForestGreen else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Check In",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isCheckIn) Color.White else if (status.canCheckIn) ForestSlate700 else ForestSlate400
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(enabled = status.canCheckOut) {
                                        selectedAction = AttendanceRecord.TYPE_CHECK_OUT
                                    },
                                shape = CircleShape,
                                color = if (!isCheckIn) ForestGreen else Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Check Out",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (!isCheckIn) Color.White else if (status.canCheckOut) ForestSlate700 else ForestSlate400
                                    )
                                }
                            }
                        }

                        // Validation error / notice
                        if (!status.canCheckIn && isCheckIn) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Already checked in at ${status.formattedCheckInTime}. Select Check Out.",
                                color = AmberWarning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (!status.canCheckOut && !isCheckIn) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No open check-in found today. You must Check In first.",
                                color = AmberWarning,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        // Three Stat Tiles (matching reference screen 2)
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
                                value = status.formattedCheckInTime
                            )
                            AttendanceStatTile(
                                icon = Icons.Default.Logout,
                                label = "Check out",
                                value = status.formattedCheckOutTime
                            )
                            AttendanceStatTile(
                                icon = Icons.Default.CheckCircle,
                                label = "Total Hrs",
                                value = status.formattedHours
                            )
                        }
                    }
                }

                // STEP 3: RECORDED SUCCESSFULLY
                is KioskIdentificationState.Recorded -> {
                    val staff = state.staff
                    val record = state.record
                    val status = state.status

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(CircleShape)
                                .background(ForestGreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(54.dp))
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (record.isCheckIn) "Checked In Successfully!" else "Checked Out Successfully!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestSlate900
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${staff.name} (${staff.employeeId}) • ${record.formattedShortTime}",
                            fontSize = 14.sp,
                            color = ForestSlate600
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Summary Stat Tiles
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(CardWhite)
                                .border(1.dp, ForestSlate200, RoundedCornerShape(20.dp))
                                .padding(vertical = 20.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            AttendanceStatTile(
                                icon = Icons.Default.Login,
                                label = "Check in",
                                value = status.formattedCheckInTime
                            )
                            AttendanceStatTile(
                                icon = Icons.Default.Logout,
                                label = "Check out",
                                value = status.formattedCheckOutTime
                            )
                            AttendanceStatTile(
                                icon = Icons.Default.Schedule,
                                label = "Total Hrs",
                                value = status.formattedHours
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                viewModel.resetKioskAttendance()
                                onNavigateBack()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is KioskIdentificationState.Unrecognized -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape).background(RoseLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonOff, contentDescription = null, tint = RoseRed, modifier = Modifier.size(44.dp))
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text("Face Not Recognized", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ForestSlate900)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We couldn't match your face with any registered staff member. Please position your face clearly or contact an Admin.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = ForestSlate600
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = { viewModel.resetKioskAttendance() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Try Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is KioskIdentificationState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Validation Notice", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = RoseRed)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = state.message, textAlign = TextAlign.Center, fontSize = 14.sp, color = ForestSlate700)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.resetKioskAttendance() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Back to Camera", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is KioskIdentificationState.NoFaceDetected -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No Face Detected", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestSlate900)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Please face the camera directly in good lighting.", color = ForestSlate600, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.resetKioskAttendance() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Try Again", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is KioskIdentificationState.NoStaffRegistered -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No Staff Registered", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ForestSlate900)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("An Admin must first register staff with facial enrolment.", color = ForestSlate600, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = onNavigateBack, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)) {
                            Text("Go to Home", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConcentricRingButton(
    text: String,
    subtext: String? = null,
    isCheckIn: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val ringColor = if (isCheckIn) ForestGreen else AmberWarning
    Box(
        modifier = Modifier
            .size(230.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Outer concentric rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.width / 2f

            drawCircle(color = ringColor.copy(alpha = 0.05f), radius = maxR, center = center)
            drawCircle(color = ringColor.copy(alpha = 0.10f), radius = maxR * 0.82f, center = center)
            drawCircle(color = ringColor.copy(alpha = 0.16f), radius = maxR * 0.66f, center = center)
            drawCircle(color = ringColor.copy(alpha = 0.24f), radius = maxR * 0.52f, center = center)
        }

        // Inner solid circular button
        Surface(
            modifier = Modifier.size(105.dp),
            shape = CircleShape,
            color = CardWhite,
            shadowElevation = 8.dp,
            border = BorderStroke(1.5.dp, ringColor.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isCheckIn) Icons.Default.TouchApp else Icons.Default.Logout,
                    contentDescription = null,
                    tint = ringColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestSlate900
                )
            }
        }
    }
}

@Composable
fun AttendanceStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(ForestGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ForestSlate900
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = ForestSlate500
        )
    }
}
