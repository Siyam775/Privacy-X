package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.LocalThemePreferences
import com.example.data.model.BlockEdge
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import com.example.data.model.UserSettings
import com.example.ui.components.GlassCard
import com.example.ui.components.LiveThemePreviewCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.LocalPrivacyXTokens
import kotlinx.coroutines.launch

@Composable
fun ProfileAndSettingsScreen(
    profile: UserProfile,
    email: String,
    isSuperAdmin: Boolean,
    themePrefs: LocalThemePreferences,
    userSettings: UserSettings,
    sessions: List<UserSession>,
    blockedUsers: List<BlockEdge>,
    followersCount: Int,
    followingCount: Int,
    openThemeStudioExternal: Boolean,
    onDismissExternalThemeStudio: () -> Unit,
    onSelectTheme: (AppThemeId) -> Unit,
    onSelectAppearance: (AppearanceMode) -> Unit,
    onSelectAccentHex: (String) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onUpdateSettings: (UserSettings) -> Unit,
    onUpdateProfile: (
        displayName: String,
        bio: String,
        statusText: String,
        photoUrl: String,
        profileVisibility: String,
        lastSeenPrivacy: String,
        storyPrivacy: String,
        customAccentHex: String
    ) -> Unit,
    onUploadAvatar: suspend (Uri) -> Result<String>,
    onRevokeSession: (String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onOpenSavedMessages: () -> Unit,
    onOpenAiAssistant: () -> Unit,
    onOpenStorageAndBackup: () -> Unit = {},
    onOpenAdminDashboard: () -> Unit,
    onClearCache: () -> Unit,
    onExportData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalPrivacyXTokens.current
    var showEditProfile by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdateSettings(userSettings.copy(notificationsEnabled = granted))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Lavender Glass Profile Hero Card (matching Screenshot 3 & 5)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(tokens.radialFabGradient))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            UserAvatar(
                                name = profile.displayName,
                                photoUrl = profile.photoUrl,
                                size = 82.dp,
                                isOnline = true
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = profile.displayName,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "@${profile.username} • ${profile.statusText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                            if (email.isNotBlank()) {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }
                    if (profile.bio.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$followersCount", style = MaterialTheme.typography.titleLarge)
                            Text("Followers", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$followingCount", style = MaterialTheme.typography.titleLarge)
                            Text("Following", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = { showEditProfile = true },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Profile")
                        }
                    }
                }
            }
        }

        // Quick Shortcuts: Saved Messages, AI Assistant, and Strictly-Gated Super Admin
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassCard(modifier = Modifier.weight(1f).testTag("profile_saved_messages_card"), onClick = onOpenSavedMessages) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bookmark, contentDescription = "Saved", tint = tokens.accentPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Saved Notes", style = MaterialTheme.typography.labelLarge)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f).testTag("profile_ai_card"), onClick = onOpenAiAssistant) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI", tint = tokens.accentSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("AI Studio", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth().testTag("profile_storage_backup_card"),
                elevated = true,
                onClick = onOpenStorageAndBackup
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Backup, contentDescription = "Storage & Backup", tint = tokens.accentPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Storage, 7-Day Cloud & Drive Backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Local media manager, 7-day auto-delete policy & encrypted Google Drive restore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (isSuperAdmin) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("super_admin_entry_card"),
                    elevated = true,
                    onClick = onOpenAdminDashboard
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = "Super Admin", tint = tokens.accentPrimary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Super Admin Governance Console", style = MaterialTheme.typography.titleMedium)
                            Text("Verified UID governance, abuse reports & announcements", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Three-Theme Live Switcher Studio
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Themes", tint = tokens.accentPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Theme Studio (Instant Live Switch)", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppearanceMode.entries.forEach { mode ->
                            val active = themePrefs.appearanceMode == mode
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (active) tokens.accentPrimary else tokens.surfaceGlass,
                                border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectAppearance(mode) }
                                    .testTag("appearance_${mode.id}")
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                                )
                            }
                        }
                    }
                    AppThemeId.entries.forEach { candidate ->
                        LiveThemePreviewCard(
                            themeId = candidate,
                            isDarkPreview = tokens.isDark,
                            isSelected = themePrefs.themeId == candidate,
                            onClick = { onSelectTheme(candidate) }
                        )
                    }
                    Text("Custom Accent Color", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("" to Color(0xFF7C3AED), "#2563EB" to Color(0xFF2563EB), "#EC4899" to Color(0xFFEC4899), "#10B981" to Color(0xFF10B981), "#F59E0B" to Color(0xFFF59E0B)).forEach { (hex, col) ->
                            val selected = themePrefs.accentColorHex == hex
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.White, CircleShape)
                                    .clickable { onSelectAccentHex(hex) }
                            )
                        }
                    }
                    SettingsToggleRow(
                        label = "Reduced Motion Mode",
                        checked = themePrefs.reducedMotion,
                        onCheckedChange = onToggleReducedMotion
                    )
                }
            }
        }

        // Notifications, Media & AI Privacy Controls
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Preferences & Privacy", style = MaterialTheme.typography.titleLarge)
                    SettingsToggleRow(
                        label = "Notifications Enabled",
                        checked = userSettings.notificationsEnabled,
                        onCheckedChange = { wantEnable ->
                            if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onUpdateSettings(userSettings.copy(notificationsEnabled = wantEnable))
                            }
                        }
                    )
                    SettingsToggleRow(
                        label = "Quiet Hours",
                        checked = userSettings.quietHoursEnabled,
                        onCheckedChange = { onUpdateSettings(userSettings.copy(quietHoursEnabled = it)) }
                    )
                    SettingsToggleRow(
                        label = "Show Message Previews",
                        checked = userSettings.showNotificationPreview,
                        onCheckedChange = { onUpdateSettings(userSettings.copy(showNotificationPreview = it)) }
                    )
                    SettingsToggleRow(
                        label = "Auto-Download on Wi-Fi",
                        checked = userSettings.autoDownloadWifi,
                        onCheckedChange = { onUpdateSettings(userSettings.copy(autoDownloadWifi = it)) }
                    )
                    SettingsToggleRow(
                        label = "Firebase AI Assistant Consent",
                        checked = userSettings.aiConsentGranted,
                        onCheckedChange = { onUpdateSettings(userSettings.copy(aiConsentGranted = it)) }
                    )
                }
            }
        }

        // Active Device Sessions
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Devices, contentDescription = "Sessions", tint = tokens.accentPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Active Sessions (${sessions.size})", style = MaterialTheme.typography.titleLarge)
                    }
                    sessions.forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.deviceName, style = MaterialTheme.typography.bodyMedium)
                                Text(session.platform, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onRevokeSession(session.id) }) { Text("Revoke") }
                        }
                    }
                }
            }
        }

        // Data Management, Blocked List & Account Actions
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account & Data", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = { showBlockedDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Block, contentDescription = "Blocked Users")
                        Spacer(Modifier.width(8.dp))
                        Text("Blocked Users (${blockedUsers.size})")
                    }
                    OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = "Clear Temporary Cache")
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Temporary Cache (Keeps Saved Media)")
                    }
                    OutlinedButton(onClick = onExportData, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Download, contentDescription = "Export Data")
                        Spacer(Modifier.width(8.dp))
                        Text("Export Account Data (JSON)")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sign Out")
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.dangerRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete Account")
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Account")
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            profile = profile,
            onUploadAvatar = onUploadAvatar,
            onDismiss = { showEditProfile = false },
            onSave = { name, bio, status, photo, vis, lastSeen, storyPriv ->
                showEditProfile = false
                onUpdateProfile(name, bio, status, photo, vis, lastSeen, storyPriv, profile.customAccentHex)
            }
        )
    }

    if (openThemeStudioExternal) {
        AlertDialog(
            onDismissRequest = onDismissExternalThemeStudio,
            title = { Text("Quick Theme Switcher") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeId.entries.forEach { candidate ->
                        LiveThemePreviewCard(
                            themeId = candidate,
                            isDarkPreview = tokens.isDark,
                            isSelected = themePrefs.themeId == candidate,
                            onClick = {
                                onSelectTheme(candidate)
                                onDismissExternalThemeStudio()
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissExternalThemeStudio) { Text("Close") } }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Users") },
            text = {
                if (blockedUsers.isEmpty()) Text("You haven't blocked any users.")
                else Column {
                    blockedUsers.forEach { edge ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(edge.blockedId.take(16), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onUnblockUser(edge.blockedId) }) { Text("Unblock") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlockedDialog = false }) { Text("Done") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account Permanently?") },
            text = { Text("This removes your profile, @username reservation, sessions, and Firebase Auth account.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.dangerRed)
                ) { Text("Delete Forever") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile,
    onUploadAvatar: suspend (Uri) -> Result<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, bio: String, status: String, photo: String, vis: String, lastSeen: String, storyPriv: String) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var status by remember { mutableStateOf(profile.statusText) }
    var photoUrl by remember { mutableStateOf(profile.photoUrl) }
    var uploading by remember { mutableStateOf(false) }
    var uploadErr by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                uploading = true
                uploadErr = null
                onUploadAvatar(uri)
                    .onSuccess { photoUrl = it }
                    .onFailure { uploadErr = it.localizedMessage }
                uploading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true)
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, maxLines = 3)
                OutlinedTextField(value = status, onValueChange = { status = it }, label = { Text("Status") }, singleLine = true)
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !uploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = "Pick Avatar")
                    Spacer(Modifier.width(8.dp))
                    Text(if (uploading) "Uploading..." else "Upload Avatar (Photo Picker)")
                }
                if (!uploadErr.isNullOrBlank()) {
                    Text(uploadErr!!, style = MaterialTheme.typography.bodySmall, color = Color.Red)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, bio, status, photoUrl, profile.profileVisibility, profile.lastSeenPrivacy, profile.storyPrivacy)
                },
                enabled = name.isNotBlank() && !uploading
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
