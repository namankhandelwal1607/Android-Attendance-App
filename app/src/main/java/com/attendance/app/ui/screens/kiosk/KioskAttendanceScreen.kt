package com.attendance.app.ui.screens.kiosk

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.attendance.app.data.repository.KioskAttendanceResult
import com.attendance.app.ui.components.CameraCaptureView
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import kotlinx.coroutines.delay

@Composable
fun KioskAttendanceScreen(
    viewModel: AppViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val kioskState by viewModel.kioskState.collectAsStateWithLifecycle()
    var capturedSelfie by remember { mutableStateOf<Bitmap?>(null) }

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

    // Auto dismiss on success after 4 seconds if user doesn't tap Done
    LaunchedEffect(kioskState) {
        if (kioskState is KioskAttendanceResult.Success) {
            delay(4000)
            viewModel.resetKioskAttendance()
            onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (kioskState is KioskAttendanceResult.Idle || kioskState is KioskAttendanceResult.Processing) {
            if (hasPermissions) {
                CameraCaptureView(
                    title = "Kiosk Face ID — Hold steady facing camera",
                    onImageCaptured = { bitmap ->
                        capturedSelfie = bitmap
                        viewModel.markKioskAttendance(bitmap)
                    },
                    onError = {
                        onNavigateBack()
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Slate900),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Camera & Location Permission Required", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Permissions are needed to detect your face and record attendance GPS location.", color = Slate300, textAlign = TextAlign.Center, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CAMERA,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }

            // Processing Overlay
            if (kioskState is KioskAttendanceResult.Processing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Searching Roster (1:N Match)...", fontWeight = FontWeight.Bold, color = Slate900)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Verifying facial features & GPS location", fontSize = 12.sp, color = Slate500)
                        }
                    }
                }
            }

            // Top Close Button
            IconButton(
                onClick = {
                    viewModel.resetKioskAttendance()
                    onNavigateBack()
                },
                modifier = Modifier
                    .padding(top = 42.dp, start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        } else {
            // RESULT DIALOG / OVERLAY SCREEN
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate900.copy(alpha = 0.95f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                when (val result = kioskState) {
                    is KioskAttendanceResult.Success -> {
                        val staff = result.staff
                        val record = result.record

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(36.dp))
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Attendance Marked!",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = staff.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = "Employee ID: ${staff.employeeId}",
                                    fontSize = 13.sp,
                                    color = Slate500
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Captured Selfie Preview
                                capturedSelfie?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Verified Selfie",
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = EmeraldLight
                                ) {
                                    Text(
                                        text = "${result.matchPercentage}% Face Match Confidence",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldDark,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Slate200)
                                Spacer(modifier = Modifier.height(14.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate500, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = record.formattedTime, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Slate800)
                                    }
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = RoseRed, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = record.address, fontSize = 12.sp, color = Slate600)
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        viewModel.resetKioskAttendance()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Text("Done", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    is KioskAttendanceResult.Unrecognized -> {
                        val scorePercent = (result.highestScore * 100).toInt()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(RoseLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = RoseRed, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Face Not Recognized",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoseRed
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Please contact an Admin to register your face into the system.",
                                    fontSize = 13.sp,
                                    color = Slate600,
                                    textAlign = TextAlign.Center
                                )
                                if (scorePercent > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Best similarity: $scorePercent% (Requires >= 70%)",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                }

                                Spacer(modifier = Modifier.height(22.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.resetKioskAttendance()
                                            onNavigateBack()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                    Button(
                                        onClick = { viewModel.resetKioskAttendance() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) {
                                        Text("Try Again")
                                    }
                                }
                            }
                        }
                    }

                    is KioskAttendanceResult.NoFaceDetected -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(AmberLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Face, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("No Face Detected", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Please position your face directly inside the oval frame and ensure good lighting.",
                                    fontSize = 13.sp,
                                    color = Slate600,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(22.dp))
                                Button(
                                    onClick = { viewModel.resetKioskAttendance() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }

                    is KioskAttendanceResult.NoStaffRegistered -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Slate100),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.GroupOff, contentDescription = null, tint = Slate500, modifier = Modifier.size(32.dp))
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("No Staff Registered", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No staff members are registered yet. An Admin must register staff with face enrolment first.",
                                    fontSize = 13.sp,
                                    color = Slate600,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(22.dp))
                                Button(
                                    onClick = {
                                        viewModel.resetKioskAttendance()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Text("Back to Home")
                                }
                            }
                        }
                    }

                    is KioskAttendanceResult.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RoseRed, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Error", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RoseRed)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = result.message, fontSize = 13.sp, color = Slate600, textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { viewModel.resetKioskAttendance() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Retry")
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
