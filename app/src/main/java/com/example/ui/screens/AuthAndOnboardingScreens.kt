package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.repository.AuthRepository
import com.example.ui.components.GlassCard
import com.example.ui.components.PrivacyXBackground
import com.example.ui.theme.LocalPrivacyXTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthSignInScreen(
    isBusy: Boolean,
    errorMessage: String?,
    onGoogleSignIn: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    PrivacyXBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                elevated = true
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(tokens.radialFabGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "PrivacyX Shield",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "PrivacyX",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Liquid glass messaging, real-time WebRTC calls, ephemeral stories, and built-in Gemini AI.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(22.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = tokens.accentPrimary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Secured",
                                tint = tokens.accentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Protected by Google Credential Manager, Firebase App Check & strict zero-trust rules.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = tokens.dangerRed.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.dangerRed,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onGoogleSignIn,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.accentPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Signing in...", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UsernameOnboardingScreen(
    suggestedName: String,
    email: String,
    isBusy: Boolean,
    errorMessage: String?,
    onCheckAvailability: suspend (String) -> Boolean,
    onSubmitProfile: (displayName: String, username: String, bio: String) -> Unit,
    onSignOut: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    var displayName by remember(suggestedName) { mutableStateOf(suggestedName.ifBlank { "PrivacyX User" }) }
    var rawUsername by remember {
        mutableStateOf(
            AuthRepository.normalizeUsername(suggestedName.ifBlank { email.substringBefore("@") })
                .take(20)
        )
    }
    var bio by remember { mutableStateOf("") }
    var availabilityState by remember { mutableStateOf<Boolean?>(null) }
    var checking by remember { mutableStateOf(false) }

    val normalized = remember(rawUsername) { AuthRepository.normalizeUsername(rawUsername) }
    val validFormat = remember(normalized) { AuthRepository.isValidUsernameFormat(normalized) }

    LaunchedEffect(normalized) {
        if (!validFormat) {
            availabilityState = null
            return@LaunchedEffect
        }
        checking = true
        delay(350L)
        availabilityState = onCheckAvailability(normalized)
        checking = false
    }

    PrivacyXBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                elevated = true
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Claim Your Identity",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose a unique @username so friends can discover and message you privately.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(60) },
                        label = { Text("Display Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_display_name_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = rawUsername,
                        onValueChange = { rawUsername = it.take(30) },
                        label = { Text("Unique Username") },
                        leadingIcon = {
                            Icon(Icons.Rounded.AlternateEmail, contentDescription = "Username")
                        },
                        trailingIcon = {
                            when {
                                checking -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                availabilityState == true -> Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = "Available",
                                    tint = tokens.onlineGreen
                                )
                                availabilityState == false || (!validFormat && rawUsername.isNotEmpty()) -> Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = "Unavailable",
                                    tint = tokens.dangerRed
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                when {
                                    !validFormat -> "Use 3-30 lowercase letters, numbers, or underscores. Normalized: @$normalized"
                                    checking -> "Checking @$normalized..."
                                    availabilityState == true -> "@$normalized is available!"
                                    availabilityState == false -> "@$normalized is already taken."
                                    else -> "@$normalized"
                                }
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_username_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(300) },
                        label = { Text("Bio (optional)") },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_bio_input")
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.dangerRed
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("Switch Account")
                        }
                        Button(
                            onClick = { onSubmitProfile(displayName, normalized, bio) },
                            enabled = !isBusy && validFormat && availabilityState != false,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp)
                                .testTag("onboarding_submit_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text("Enter PrivacyX")
                            }
                        }
                    }
                }
            }
        }
    }
}
