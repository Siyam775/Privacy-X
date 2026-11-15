package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AbuseReport
import com.example.data.model.AdminLog
import com.example.data.model.Conversation
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformSettings
import com.example.data.model.SavedMessage
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.PrivacyXBackground
import com.example.ui.theme.LocalPrivacyXTokens

@Composable
fun NotificationsScreen(
    notifications: List<NotificationItem>,
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val tokens = LocalPrivacyXTokens.current
    PrivacyXBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Notifications", style = MaterialTheme.typography.headlineMedium)
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (notifications.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Notifications,
                            title = "All caught up",
                            subtitle = "New messages, calls, story reactions, and mentions appear here."
                        )
                    }
                } else {
                    items(notifications, key = { it.id }) { item ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevated = !item.isRead,
                            onClick = { onMarkRead(item.id) }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (!item.isRead) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Unread", tint = tokens.accentPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedMessagesScreen(
    savedMessages: List<SavedMessage>,
    onBack: () -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    var noteInput by remember { mutableStateOf("") }
    PrivacyXBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Saved Messages & Cloud Notes", style = MaterialTheme.typography.titleLarge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Save a personal cloud note...") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (noteInput.isNotBlank()) {
                        onAddNote(noteInput)
                        noteInput = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Save Note")
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (savedMessages.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Bookmark,
                            title = "No saved messages",
                            subtitle = "Long-press any chat message to bookmark it, or save quick notes above."
                        )
                    }
                } else {
                    items(savedMessages, key = { it.id }) { item ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (item.sourceSenderName.isNotBlank()) {
                                        Text(item.sourceSenderName, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(item.content, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { onDeleteItem(item.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove Bookmark")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuperAdminDashboardScreen(
    isAuthorizedSuperAdmin: Boolean,
    users: List<UserProfile>,
    conversations: List<Conversation>,
    reports: List<AbuseReport>,
    logs: List<AdminLog>,
    platformSettings: PlatformSettings,
    onBack: () -> Unit,
    onToggleSuspendUser: (UserProfile) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    onUpdateReportStatus: (String, String) -> Unit,
    onPublishAnnouncement: (String, String, String) -> Unit,
    onSavePlatformSettings: (Long, Boolean, Boolean) -> Unit
) {
    BackHandler(onBack = onBack)
    val tokens = LocalPrivacyXTokens.current
    var announceTitle by remember { mutableStateOf("") }
    var announceBody by remember { mutableStateOf("") }
    var maxMbText by remember(platformSettings) { mutableStateOf(platformSettings.maxUploadMb.toString()) }
    var allowReg by remember(platformSettings) { mutableStateOf(platformSettings.allowNewRegistrations) }
    var maintMode by remember(platformSettings) { mutableStateOf(platformSettings.maintenanceMode) }

    PrivacyXBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Super Admin Governance", style = MaterialTheme.typography.headlineMedium)
            }

            if (!isAuthorizedSuperAdmin) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Access Denied: This console is strictly locked to the designated Super Admin UID.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.dangerRed
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // KPI Metrics Row
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Users", users.size.toString(), Modifier.weight(1f))
                            MetricTile("Spaces", conversations.size.toString(), Modifier.weight(1f))
                            MetricTile("Open Reports", reports.count { it.status == "open" }.toString(), Modifier.weight(1f))
                        }
                    }

                    // Publish Announcement
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Broadcast Platform Announcement", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(value = announceTitle, onValueChange = { announceTitle = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = announceBody, onValueChange = { announceBody = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = {
                                        onPublishAnnouncement(announceTitle, announceBody, "urgent")
                                        announceTitle = ""
                                        announceBody = ""
                                    },
                                    enabled = announceTitle.isNotBlank() && announceBody.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
                                ) { Text("Broadcast") }
                            }
                        }
                    }

                    // Global Platform Settings
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Global Platform Controls", style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = maxMbText,
                                    onValueChange = { maxMbText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Max Media Upload (MB)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Allow New Registrations")
                                    Switch(checked = allowReg, onCheckedChange = { allowReg = it })
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Maintenance Mode")
                                    Switch(checked = maintMode, onCheckedChange = { maintMode = it })
                                }
                                Button(
                                    onClick = { onSavePlatformSettings(maxMbText.toLongOrNull() ?: 25L, allowReg, maintMode) },
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Save Platform Settings") }
                            }
                        }
                    }

                    // Reports Triage
                    if (reports.isNotEmpty()) {
                        item { Text("ABUSE REPORTS (${reports.size})", style = MaterialTheme.typography.labelSmall) }
                        items(reports, key = { it.id }) { rep ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${rep.targetType.uppercase()} • ${rep.reason}", style = MaterialTheme.typography.titleMedium)
                                    Text("Target: ${rep.targetId} • Status: ${rep.status}", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onUpdateReportStatus(rep.id, "resolved") }) { Text("Resolve") }
                                        OutlinedButton(onClick = { onUpdateReportStatus(rep.id, "dismissed") }) { Text("Dismiss") }
                                    }
                                }
                            }
                        }
                    }

                    // Users Moderation
                    item { Text("USERS (${users.size})", style = MaterialTheme.typography.labelSmall) }
                    items(users.take(30), key = { it.userId }) { u ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${u.displayName} (@${u.username})", style = MaterialTheme.typography.bodyMedium)
                                    Text("Suspended: ${u.isSuspended}", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { onToggleSuspendUser(u) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (u.isSuspended) tokens.onlineGreen else tokens.dangerRed
                                    )
                                ) { Text(if (u.isSuspended) "Restore" else "Suspend") }
                            }
                        }
                    }

                    // Conversations Moderation
                    item { Text("CONVERSATIONS (${conversations.size})", style = MaterialTheme.typography.labelSmall) }
                    items(conversations.take(20), key = { it.id }) { c ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(c.title, style = MaterialTheme.typography.bodyMedium)
                                    Text("${c.type} • ${c.memberUids.size} members", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { onDeleteConversation(c) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete Space", tint = tokens.dangerRed)
                                }
                            }
                        }
                    }

                    // Admin Audit Logs
                    if (logs.isNotEmpty()) {
                        item { Text("IMMUTABLE AUDIT LOGS", style = MaterialTheme.typography.labelSmall) }
                        items(logs.take(15), key = { it.id }) { log ->
                            GlassCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("${log.action} -> ${log.targetId}", style = MaterialTheme.typography.labelLarge)
                                    Text(log.details, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, elevated = true) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
