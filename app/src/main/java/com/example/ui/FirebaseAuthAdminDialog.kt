package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.FirebaseAuthManager
import com.example.data.MediaRepository
import kotlinx.coroutines.launch

@Composable
fun FirebaseAuthAdminStatusCard(
    repository: MediaRepository,
    onOpenAuthDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val authManager = repository.authManager
    val isLoggedIn = authManager.isUserLoggedIn()
    val userEmail = authManager.getLoggedInEmail() ?: ""
    val isAuthorized = authManager.isAuthorizedAdmin()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoggedIn) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFF7F1D1D).copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            1.2.dp,
            if (isLoggedIn) Color(0xFF10B981) else Color(0xFFEF4444)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (isLoggedIn) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLoggedIn) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                        contentDescription = null,
                        tint = if (isLoggedIn) Color(0xFF10B981) else Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (isLoggedIn) "ফায়ারবেস অথেন্টিকেশন: সফল" else "ফায়ারবেস অথেন্টিকেশন: লগইন করা নেই",
                        color = if (isLoggedIn) Color(0xFF34D399) else Color(0xFFFCA5A5),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLoggedIn) {
                            if (isAuthorized) "অ্যাডমিন: $userEmail (অনুমোদিত)" else "ইউজার: $userEmail"
                        } else {
                            "ডাটাবেসে চ্যানেল আপডেট ও রাইট করতে লগইন করুন"
                        },
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onOpenAuthDialog,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoggedIn) Color(0xFF1E293B) else Color(0xFFEF4444)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (isLoggedIn) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Login,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isLoggedIn) "ম্যানেজ" else "লগইন করুন",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

enum class AuthDialogMode {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD
}

@Composable
fun FirebaseAuthAdminDialog(
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authManager = repository.authManager

    var mode by remember { mutableStateOf(AuthDialogMode.SIGN_IN) }
    var email by remember { mutableStateOf(authManager.getLoggedInEmail() ?: FirebaseAuthManager.PRIMARY_ADMIN_EMAIL) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val isLoggedIn = authManager.isUserLoggedIn()

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.2.dp, Color(0xFF334155)),
            shadowElevation = 24.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
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
                                .background(Color(0xFF6366F1).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ফায়ারবেস অ্যাডমিন সিকিউরিটি",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { if (!isLoading) onDismiss() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoggedIn) {
                    // Logged In Status View
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF064E3B).copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "বর্তমানে অ্যাডমিন হিসেবে লগইন আছেন",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = authManager.getLoggedInEmail() ?: "",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            val uid = authManager.getLoggedInUid()
                            if (!uid.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "UID: $uid",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ঠিক আছে", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                authManager.signOut()
                                Toast.makeText(context, "লগআউট সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
                                onAuthSuccess()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Logout,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("লগআউট", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Mode Selector Tabs (Sign In / Sign Up / Reset)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = {
                                mode = AuthDialogMode.SIGN_IN
                                errorMessage = null
                                successMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mode == AuthDialogMode.SIGN_IN) Color(0xFF00E5FF) else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(
                                text = "লগইন",
                                color = if (mode == AuthDialogMode.SIGN_IN) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                mode = AuthDialogMode.SIGN_UP
                                errorMessage = null
                                successMessage = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mode == AuthDialogMode.SIGN_UP) Color(0xFF00E5FF) else Color.Transparent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Text(
                                text = "নতুন অ্যাকাউন্ট",
                                color = if (mode == AuthDialogMode.SIGN_UP) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("ফায়ারবেস অ্যাডমিন জিমেইল", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Email, contentDescription = null, tint = Color(0xFF00E5FF))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF020617),
                            unfocusedContainerColor = Color(0xFF020617)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (mode != AuthDialogMode.FORGOT_PASSWORD) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Password Field
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            label = { Text("অ্যাকাউন্ট পাসওয়ার্ড", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color(0xFF00E5FF))
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle Password",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF020617),
                                unfocusedContainerColor = Color(0xFF020617)
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Error or Success Banner
                    AnimatedVisibility(visible = errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF7F1D1D).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = successMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Text(
                                text = successMessage ?: "",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Button
                    Button(
                        onClick = {
                            if (isLoading) return@Button
                            errorMessage = null
                            successMessage = null
                            isLoading = true

                            scope.launch {
                                when (mode) {
                                    AuthDialogMode.SIGN_IN -> {
                                        val result = authManager.signInWithEmail(email, password)
                                        isLoading = false
                                        if (result.first) {
                                            successMessage = result.second
                                            Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                        } else {
                                            errorMessage = result.second
                                        }
                                    }
                                    AuthDialogMode.SIGN_UP -> {
                                        val result = authManager.signUpWithEmail(email, password)
                                        isLoading = false
                                        if (result.first) {
                                            successMessage = result.second
                                            Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                                            onAuthSuccess()
                                        } else {
                                            errorMessage = result.second
                                        }
                                    }
                                    AuthDialogMode.FORGOT_PASSWORD -> {
                                        val result = authManager.sendPasswordReset(email)
                                        isLoading = false
                                        if (result.first) {
                                            successMessage = result.second
                                            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                                        } else {
                                            errorMessage = result.second
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("প্রসেসিং হচ্ছে...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = when (mode) {
                                    AuthDialogMode.SIGN_IN -> Icons.Rounded.Login
                                    AuthDialogMode.SIGN_UP -> Icons.Rounded.PersonAdd
                                    AuthDialogMode.FORGOT_PASSWORD -> Icons.Rounded.LockReset
                                },
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (mode) {
                                    AuthDialogMode.SIGN_IN -> "ফায়ারবেসে লগইন করুন"
                                    AuthDialogMode.SIGN_UP -> "অ্যাকাউন্ট তৈরি ও লগইন"
                                    AuthDialogMode.FORGOT_PASSWORD -> "পাসওয়ার্ড রিসেট লিংক পাঠান"
                                },
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Forgot Password Toggle
                    if (mode == AuthDialogMode.SIGN_IN) {
                        TextButton(
                            onClick = {
                                mode = AuthDialogMode.FORGOT_PASSWORD
                                errorMessage = null
                                successMessage = null
                            }
                        ) {
                            Text(
                                text = "পাসওয়ার্ড ভুলে গেছেন?",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.5.sp
                            )
                        }
                    } else if (mode == AuthDialogMode.FORGOT_PASSWORD) {
                        TextButton(
                            onClick = {
                                mode = AuthDialogMode.SIGN_IN
                                errorMessage = null
                                successMessage = null
                            }
                        ) {
                            Text(
                                text = "লগইন স্ক্রিনে ফিরে যান",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
