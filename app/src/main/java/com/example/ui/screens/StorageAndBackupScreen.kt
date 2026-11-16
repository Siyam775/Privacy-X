package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.backup.BackupPreferences
import com.example.backup.BackupSchedule
import com.example.backup.DriveBackupFile
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.StorageStatsSnapshot
import com.example.ui.components.GlassCard
import com.example.ui.components.PrivacyXBackground
import com.example.ui.theme.LocalPrivacyXTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PassphrasePurpose {
    DRIVE_BACKUP_NOW,
    LOCAL_EXPORT_ZIP,
    RESTORE_DRIVE_FILE,
    RESTORE_LOCAL_FILE
}

@Composable
fun StorageAndBackupScreen(
    storageStats: StorageStatsSnapshot,
    backupPrefs: BackupPreferences,
    driveBackups: List<DriveBackupFile>,
    backupHistory: List<BackupHistoryEntity>,
    isBackupBusy: Boolean,
    onBack: () -> Unit,
    onClearTemporaryCache: () -> Unit,
    onConnectDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onRefreshDriveBackups: () -> Unit,
    onBackupNowToDrive: (passphrase: String) -> Unit,
    onSelectSchedule: (BackupSchedule) -> Unit,
    onToggleIncludeMedia: (Boolean) -> Unit,
    onRestoreDriveFile: (file: DriveBackupFile, passphrase: String) -> Unit,
    onExportLocalZip: (passphrase: String) -> Unit,
    onRestoreLocalZip: (passphrase: String) -> Unit
) {
    BackHandler(onBack = onBack)
    val tokens = LocalPrivacyXTokens.current

    var showDisableMediaWarning by remember { mutableStateOf(false) }
    var passphraseDialogPurpose by remember { mutableStateOf<PassphrasePurpose?>(null) }
    var selectedDriveRestoreFile by remember { mutableStateOf<DriveBackupFile?>(null) }
    var pendingRestoreConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRestoreLabel by remember { mutableStateOf("") }
    var passphraseInput by remember { mutableStateOf("") }

    PrivacyXBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("storage_backup_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("storage_backup_back")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Storage & Backup", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "7-Day Cloud Retention • Local Vault • Google Drive",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isBackupBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else if (backupPrefs.driveConnected) {
                            IconButton(
                                onClick = onRefreshDriveBackups,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Drive Backups", tint = tokens.accentPrimary)
                            }
                        }
                    }
                }
            }

            // 7-Day Cloud Retention Banner
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = "7-day cloud policy", tint = tokens.accentPrimary)
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("7-Day Cloud Media Auto-Delete", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Uploaded photos and voice notes are stored in Supabase Cloud for 7 days and then automatically deleted to keep cloud usage free/low-cost. Message text, saved local copies, and Drive backups are never wiped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Local & Cloud Storage Breakdown
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, contentDescription = "Storage", tint = tokens.accentPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Device & Cloud Footprint", style = MaterialTheme.typography.titleLarge)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            StorageStatTile("Local Photos", "${storageStats.localImagesCount}", Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            StorageStatTile("Voice Notes", "${storageStats.localVoiceCount}", Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            StorageStatTile("Documents", "${storageStats.localDocumentsCount}", Modifier.weight(1f))
                        }
                        HorizontalDivider(color = tokens.surfaceBorder)
                        StorageMetricRow("Permanent Local Media", formatBytes(storageStats.localTotalBytes))
                        StorageMetricRow("Active Cloud Media (<7 days)", "${storageStats.activeCloudFilesCount} files (${formatBytes(storageStats.estimatedCloudBytes)})")
                        StorageMetricRow("Temporary App Cache", formatBytes(storageStats.tempCacheBytes))
                        StorageMetricRow("Pending Offline Messages", "${storageStats.pendingOfflineMessagesCount}")

                        OutlinedButton(
                            onClick = onClearTemporaryCache,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("clear_temp_cache_button")
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = "Clear Temporary Cache")
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Temporary Cache")
                        }
                        Text(
                            "Clearing temporary cache preserves your saved local photos, voice notes, wallpapers, and chat database.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Google Drive Backup Controls
            item {
                GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Backup, contentDescription = "Google Drive", tint = tokens.accentPrimary)
                                Spacer(Modifier.width(8.dp))
                                Text("Google Drive Backup", style = MaterialTheme.typography.titleLarge)
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (backupPrefs.driveConnected) tokens.onlineGreen.copy(alpha = 0.18f) else tokens.surfaceGlass
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (backupPrefs.driveConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                        contentDescription = "Drive Status",
                                        tint = if (backupPrefs.driveConnected) tokens.onlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (backupPrefs.driveConnected) "Connected" else "Disconnected",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Text(
                            text = backupPrefs.lastBackupStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (backupPrefs.lastBackupAtMs > 0L) {
                            Text(
                                text = "Last backup: ${formatTimestamp(backupPrefs.lastBackupAtMs)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (backupPrefs.nextScheduledBackupMs > 0L && backupPrefs.schedule != BackupSchedule.OFF) {
                            Text(
                                text = "Next scheduled: ${formatTimestamp(backupPrefs.nextScheduledBackupMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.accentPrimary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!backupPrefs.driveConnected) {
                                Button(
                                    onClick = onConnectDrive,
                                    enabled = !isBackupBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("drive_connect_button")
                                ) {
                                    Text("Connect Google Drive")
                                }
                            } else {
                                Button(
                                    onClick = {
                                        passphraseInput = ""
                                        passphraseDialogPurpose = PassphrasePurpose.DRIVE_BACKUP_NOW
                                    },
                                    enabled = !isBackupBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("drive_backup_now_button")
                                ) {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = "Back Up Now", modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Back Up Now")
                                }
                                OutlinedButton(
                                    onClick = onDisconnectDrive,
                                    enabled = !isBackupBusy,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Disconnect")
                                }
                            }
                        }

                        HorizontalDivider(color = tokens.surfaceBorder)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, contentDescription = "Schedule", tint = tokens.accentPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Automatic Schedule", style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BackupSchedule.entries.forEach { schedule ->
                                val selected = backupPrefs.schedule == schedule
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) tokens.accentPrimary else tokens.surfaceGlass,
                                    border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                                    modifier = Modifier
                                        .clickable { onSelectSchedule(schedule) }
                                        .testTag("backup_schedule_${schedule.id}")
                                ) {
                                    Text(
                                        text = schedule.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Include Images and Voice Messages in Backup", style = MaterialTheme.typography.bodyMedium)
                                if (!backupPrefs.includeMediaInBackup) {
                                    Text(
                                        "Media files will not be included in backups. Expired cloud media cannot be recovered without backups.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tokens.warningAmber
                                    )
                                }
                            }
                            Switch(
                                checked = backupPrefs.includeMediaInBackup,
                                onCheckedChange = { wantInclude ->
                                    if (!wantInclude) {
                                        showDisableMediaWarning = true
                                    } else {
                                        onToggleIncludeMedia(true)
                                    }
                                },
                                modifier = Modifier.testTag("include_media_backup_switch")
                            )
                        }
                    }
                }
            }

            // Drive Backups List + Local ZIP Export / Import
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Restore, contentDescription = "Restore", tint = tokens.accentPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Restore & Local ZIP Archives", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "Restoring writes chats and media directly to device local storage without re-uploading to cloud or resetting 7-day timers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    passphraseInput = ""
                                    passphraseDialogPurpose = PassphrasePurpose.LOCAL_EXPORT_ZIP
                                },
                                enabled = !isBackupBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("export_local_zip_button")
                            ) {
                                Icon(Icons.Rounded.FolderZip, contentDescription = "Export ZIP", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export ZIP")
                            }
                            OutlinedButton(
                                onClick = {
                                    passphraseInput = ""
                                    passphraseDialogPurpose = PassphrasePurpose.RESTORE_LOCAL_FILE
                                },
                                enabled = !isBackupBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("restore_local_zip_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = "Restore ZIP", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore ZIP")
                            }
                        }

                        if (driveBackups.isNotEmpty()) {
                            HorizontalDivider(color = tokens.surfaceBorder)
                            Text("Available Google Drive Backups", style = MaterialTheme.typography.titleSmall)
                            driveBackups.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${formatBytes(file.sizeBytes)} • ${file.createdTimeIso.take(16).replace('T', ' ')}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedDriveRestoreFile = file
                                            passphraseInput = ""
                                            passphraseDialogPurpose = PassphrasePurpose.RESTORE_DRIVE_FILE
                                        },
                                        enabled = !isBackupBusy
                                    ) {
                                        Text("Restore")
                                    }
                                }
                            }
                        } else if (backupPrefs.driveConnected) {
                            Text(
                                "Tap refresh or Back Up Now to see archives in Drive folder 'My Messaging App Backups'.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Backup & Restore History
            if (backupHistory.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, contentDescription = "History", tint = tokens.accentPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text("Backup & Restore History", style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(backupHistory, key = { it.backupId }) { entry ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = if (entry.provider == "google_drive") "Google Drive" else "Local ZIP",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (entry.isEncrypted) {
                                        Icon(Icons.Rounded.Lock, contentDescription = "AES-256 Encrypted", tint = tokens.accentPrimary, modifier = Modifier.size(14.dp))
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = tokens.accentPrimary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = entry.status.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tokens.accentPrimary,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(entry.summary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${formatBytes(entry.sizeBytes)} • ${formatTimestamp(entry.createdAtMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDisableMediaWarning) {
        AlertDialog(
            onDismissRequest = { showDisableMediaWarning = false },
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = "Warning", tint = tokens.warningAmber) },
            title = { Text("Exclude Media From Backups?") },
            text = {
                Text("Media files will not be included in backups. Expired cloud media cannot be recovered without backups.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisableMediaWarning = false
                        onToggleIncludeMedia(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.warningAmber)
                ) { Text("Exclude Media") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableMediaWarning = false }) { Text("Keep Media") }
            }
        )
    }

    passphraseDialogPurpose?.let { purpose ->
        val isRestore = purpose == PassphrasePurpose.RESTORE_DRIVE_FILE || purpose == PassphrasePurpose.RESTORE_LOCAL_FILE
        AlertDialog(
            onDismissRequest = { passphraseDialogPurpose = null },
            title = {
                Text(
                    when (purpose) {
                        PassphrasePurpose.DRIVE_BACKUP_NOW -> "Encrypt & Upload Drive Backup"
                        PassphrasePurpose.LOCAL_EXPORT_ZIP -> "Export Local Backup Archive"
                        PassphrasePurpose.RESTORE_DRIVE_FILE -> "Decrypt Google Drive Backup"
                        PassphrasePurpose.RESTORE_LOCAL_FILE -> "Restore Local Backup File"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isRestore) {
                            "If this backup is password-encrypted, enter the passphrase. Leave blank for plain ZIP."
                        } else {
                            "Optional AES-256-GCM encryption password. Your password is never stored on device or servers."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = passphraseInput,
                        onValueChange = { passphraseInput = it },
                        label = { Text("Optional Passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pw = passphraseInput
                        passphraseDialogPurpose = null
                        when (purpose) {
                            PassphrasePurpose.DRIVE_BACKUP_NOW -> onBackupNowToDrive(pw)
                            PassphrasePurpose.LOCAL_EXPORT_ZIP -> onExportLocalZip(pw)
                            PassphrasePurpose.RESTORE_DRIVE_FILE -> {
                                val target = selectedDriveRestoreFile
                                if (target != null) {
                                    pendingRestoreLabel = "Restore '${target.name}' directly to local storage? Existing chats will merge cleanly without re-uploading media to Supabase."
                                    pendingRestoreConfirmAction = { onRestoreDriveFile(target, pw) }
                                }
                            }
                            PassphrasePurpose.RESTORE_LOCAL_FILE -> {
                                pendingRestoreLabel = "Select and restore a local ZIP / .pxbackup file directly to device storage? Media timers remain unchanged and nothing is re-uploaded."
                                pendingRestoreConfirmAction = { onRestoreLocalZip(pw) }
                            }
                        }
                    }
                ) {
                    Text(if (isRestore) "Continue" else "Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { passphraseDialogPurpose = null }) { Text("Cancel") }
            }
        )
    }

    pendingRestoreConfirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingRestoreConfirmAction = null },
            title = { Text("Confirm Backup Restore") },
            text = { Text(pendingRestoreLabel) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreConfirmAction = null
                        action()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
                ) { Text("Restore Now") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreConfirmAction = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StorageStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val tokens = LocalPrivacyXTokens.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tokens.surfaceGlass,
        border = BorderStroke(0.8.dp, tokens.surfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = tokens.accentPrimary)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StorageMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

private fun formatTimestamp(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(ms))
