package com.attendance.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.attendance.app.ui.theme.*
import com.attendance.app.ui.viewmodel.AppViewModel
import com.attendance.app.ui.viewmodel.LoginResult
import com.attendance.app.ui.viewmodel.LoginViewModel
import com.attendance.app.ui.viewmodel.UserRole

@Composable
fun LoginScreen(
    appViewModel: AppViewModel,
    loginViewModel: LoginViewModel = viewModel(),
    onNavigateToAdmin: () -> Unit,
    onNavigateToStaff: () -> Unit,
    onNavigateToKiosk: () -> Unit
) {
    val username by loginViewModel.username.collectAsStateWithLifecycle()
    val password by loginViewModel.password.collectAsStateWithLifecycle()
    val loginResult by loginViewModel.loginResult.collectAsStateWithLifecycle()

    var passwordVisible by remember { mutableStateOf(false) }
    var showPortalLogin by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun performLogin() {
        focusManager.clearFocus()
        loginViewModel.login { role, staff ->
            when (role) {
                UserRole.ADMIN -> {
                    appViewModel.login(UserRole.ADMIN)
                    loginViewModel.resetState()
                    onNavigateToAdmin()
                }
                UserRole.STAFF -> {
                    appViewModel.loginStaff(staff)
                    loginViewModel.resetState()
                    onNavigateToStaff()
                }
                else -> Unit
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SoftOffWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // REFERENCE SCREEN 1: Large Shield + Clock + Checkmark Logo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(ForestGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    // Shield Illustration with checkmark and clock
                    Canvas(modifier = Modifier.size(90.dp)) {
                        val w = size.width
                        val h = size.height

                        // Outer shield path
                        val shieldPath = Path().apply {
                            moveTo(w * 0.5f, 0f)
                            lineTo(w * 0.95f, h * 0.22f)
                            cubicTo(w * 0.95f, h * 0.65f, w * 0.65f, h * 0.92f, w * 0.5f, h)
                            cubicTo(w * 0.35f, h * 0.92f, w * 0.05f, h * 0.65f, w * 0.05f, h * 0.22f)
                            close()
                        }
                        drawPath(path = shieldPath, color = ForestGreen)

                        // Inner checkmark in accent mint
                        val checkPath = Path().apply {
                            moveTo(w * 0.28f, h * 0.52f)
                            lineTo(w * 0.44f, h * 0.68f)
                            lineTo(w * 0.74f, h * 0.34f)
                        }
                        drawPath(
                            path = checkPath,
                            color = Color.White,
                            style = Stroke(width = 12f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Headline matching reference: "Attendance Made Effortless"
                Text(
                    text = "Attendance Made\nEffortless",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestSlate900,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Facial recognition check-in with GPS verification",
                    fontSize = 14.sp,
                    color = ForestSlate500,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Dot indicator row (matching reference)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ForestSlate300))
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(AccentMint)
                    )
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ForestSlate300))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(ForestSlate300))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ACTION BUTTONS SECTION
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // PRIMARY PILL BUTTON ("Get started" style matching reference screen 1)
                Button(
                    onClick = onNavigateToKiosk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Mark Attendance (Face Kiosk)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Portal Sign-in button
                OutlinedButton(
                    onClick = { showPortalLogin = !showPortalLogin },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, ForestSlate300),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestSlate800)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (showPortalLogin) Icons.Default.ExpandLess else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = ForestSlate600
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showPortalLogin) "Hide Portal Sign In" else "Sign In to Portal (Admin / Staff)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestSlate800
                        )
                    }
                }

                // EXPANDABLE PORTAL LOGIN FORM
                AnimatedVisibility(visible = showPortalLogin) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.outlinedCardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, ForestSlate200)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Portal Credentials",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestSlate900
                                )
                                Text(
                                    text = "Access Admin Dashboard or personal attendance records",
                                    fontSize = 12.sp,
                                    color = ForestSlate500
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Username / ID
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { loginViewModel.onUsernameChange(it) },
                                    label = { Text("Username or Employee ID", fontSize = 13.sp) },
                                    placeholder = { Text("admin or staff username", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ForestSlate400) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Password
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { loginViewModel.onPasswordChange(it) },
                                    label = { Text("Password", fontSize = 13.sp) },
                                    placeholder = { Text("Enter password", fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ForestSlate400) },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = ForestSlate400
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { performLogin() }),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                // Inline Error
                                if (loginResult is LoginResult.Error) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = (loginResult as LoginResult.Error).message,
                                        color = RoseRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { performLogin() },
                                    enabled = loginResult !is LoginResult.Loading,
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                                ) {
                                    if (loginResult is LoginResult.Loading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Demo Admin Pill
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { loginViewModel.setCredentials("admin", "admin123") },
                                    shape = RoundedCornerShape(10.dp),
                                    color = ForestGreenLight,
                                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Admin: admin / admin123 (Tap to fill)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ForestGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
