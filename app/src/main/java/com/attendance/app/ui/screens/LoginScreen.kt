package com.attendance.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
    onNavigateToStaff: () -> Unit
) {
    val username by loginViewModel.username.collectAsStateWithLifecycle()
    val password by loginViewModel.password.collectAsStateWithLifecycle()
    val loginResult by loginViewModel.loginResult.collectAsStateWithLifecycle()

    var passwordVisible by remember { mutableStateOf(false) }
    var showDemoPills by remember { mutableStateOf(true) }
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
        color = Slate50
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SmartAttendance",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = "Face Recognition & Geolocation",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Sign In",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter your credentials to access the portal",
                    fontSize = 14.sp,
                    color = Slate600
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Form Section
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Username / Employee-ID
                    Text(
                        text = "Username or Employee ID",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { loginViewModel.onUsernameChange(it) },
                        placeholder = { Text("admin, staff, rohan, EMP-101...", fontSize = 14.sp, color = Slate400) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Slate400
                            )
                        },
                        trailingIcon = {
                            if (username.isNotEmpty()) {
                                IconButton(onClick = { loginViewModel.onUsernameChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate400)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate50,
                            unfocusedContainerColor = Slate50,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Slate200
                        )
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Password
                    Text(
                        text = "Password",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { loginViewModel.onPasswordChange(it) },
                        placeholder = { Text("Enter your password", fontSize = 14.sp, color = Slate400) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Slate400
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Slate400
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { performLogin() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Slate50,
                            unfocusedContainerColor = Slate50,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Slate200
                        )
                    )

                    // Inline Error Banner
                    AnimatedVisibility(
                        visible = loginResult is LoginResult.Error,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (loginResult is LoginResult.Error) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = RoseLight,
                                border = BorderStroke(1.dp, RoseRed.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = RoseRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = (loginResult as LoginResult.Error).message,
                                        color = RoseRed,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Login Button
                    Button(
                        onClick = { performLogin() },
                        enabled = loginResult !is LoginResult.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (loginResult is LoginResult.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Demo Quick Fill Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Demo Credentials (Tap to fill)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        }
                        Text(
                            text = if (showDemoPills) "Hide" else "Show",
                            fontSize = 12.sp,
                            color = PrimaryBlue,
                            modifier = Modifier.clickable { showDemoPills = !showDemoPills }
                        )
                    }

                    if (showDemoPills) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Admin Portal:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DemoCredentialPill(
                                label = "Admin (admin / admin123)",
                                isPrimary = true,
                                onClick = {
                                    loginViewModel.setCredentials("admin", "admin123")
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Staff Accounts (5 seeded users):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate500
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DemoCredentialPill(
                                    label = "Rohan (rohan123)",
                                    modifier = Modifier.weight(1f),
                                    onClick = { loginViewModel.setCredentials("rohan", "rohan123") }
                                )
                                DemoCredentialPill(
                                    label = "Priya (priya123)",
                                    modifier = Modifier.weight(1f),
                                    onClick = { loginViewModel.setCredentials("priya", "priya123") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DemoCredentialPill(
                                    label = "Aman (aman123)",
                                    modifier = Modifier.weight(1f),
                                    onClick = { loginViewModel.setCredentials("aman", "aman123") }
                                )
                                DemoCredentialPill(
                                    label = "Sneha (sneha123)",
                                    modifier = Modifier.weight(1f),
                                    onClick = { loginViewModel.setCredentials("sneha", "sneha123") }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DemoCredentialPill(
                                    label = "Karan (karan123)",
                                    modifier = Modifier.weight(1f),
                                    onClick = { loginViewModel.setCredentials("karan", "karan123") }
                                )
                                DemoCredentialPill(
                                    label = "Generic (staff123)",
                                    modifier = Modifier.weight(1f),
                                    onClick = { loginViewModel.setCredentials("staff", "staff123") }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Slate100
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Local SQLite & On-Device ML",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoCredentialPill(
    label: String,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (isPrimary) PrimaryBlueLight else Slate100,
        border = BorderStroke(1.dp, if (isPrimary) PrimaryBlue.copy(alpha = 0.3f) else Slate200)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isPrimary) PrimaryBlue else Slate700,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
