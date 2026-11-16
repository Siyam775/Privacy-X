package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupCryptoZip
import com.example.backup.BackupPreferences
import com.example.backup.BackupSchedule
import com.example.core.ThemePreferencesStore
import com.example.data.local.LocalMediaEntity
import com.example.data.local.LocalMediaRepository
import com.example.data.local.MediaAvailabilityState
import com.example.data.local.PendingOutgoingMessageEntity
import com.example.data.local.StorageStatsSnapshot
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.ui.screens.StorageAndBackupScreen
import com.example.ui.theme.PrivacyXTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseMediaAndBackupTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun optimizeImageToWebP_scalesDownOver2000pxAndProducesThumbnail() {
        val rawFile = File(context.cacheDir, "big_photo_${System.currentTimeMillis()}.png")
        val largeBitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        FileOutputStream(rawFile).use { out ->
            largeBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        largeBitmap.recycle()

        try {
            val service = SupabaseMediaService(context)
            val result = service.optimizeImageToWebP(Uri.fromFile(rawFile), compressImage = true)
            assertNotNull("Expected WebP optimization output", result)
            val (mainBytes, thumbBytes) = result!!
            assertNotNull("Expected 320px thumbnail bytes", thumbBytes)

            val mainDecoded = BitmapFactory.decodeByteArray(mainBytes, 0, mainBytes.size)
            assertNotNull(mainDecoded)
            assertTrue("Longest edge should be <= 1600px, was ${mainDecoded.width}", mainDecoded.width <= 1600)
            assertEquals("Aspect ratio 2:1 should preserve height 800", 800, mainDecoded.height)

            val thumbDecoded = BitmapFactory.decodeByteArray(thumbBytes!!, 0, thumbBytes.size)
            assertNotNull(thumbDecoded)
            assertTrue("Thumbnail longest edge should be <= 320px, was ${thumbDecoded.width}", thumbDecoded.width <= 320)
        } finally {
            rawFile.delete()
        }
    }

    @Test
    fun validateUri_rejectsVideoFilesWithVideoMessagingDisabledException() {
        val videoFile = File(context.cacheDir, "clip_${System.currentTimeMillis()}.mp4")
        videoFile.writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x18))
        try {
            val service = SupabaseMediaService(context)
            val res = service.validateUri(Uri.fromFile(videoFile))
            assertTrue(res.isFailure)
            assertTrue(
                "Expected VideoMessagingDisabledException, got ${res.exceptionOrNull()}",
                res.exceptionOrNull() is VideoMessagingDisabledException
            )
        } finally {
            videoFile.delete()
        }
    }

    @Test
    fun sevenDayExpiration_transitionsToMissingOrLocalOnlyWithoutDeletingText(): Unit = runBlocking {
        val repo = LocalMediaRepository(context)
        val now = System.currentTimeMillis()
        val expiredTimestamp = now - (8L * 24L * 60L * 60L * 1000L)

        val msg = ChatMessage(
            id = "msg_exp_1",
            conversationId = "conv_1",
            senderId = "u1",
            senderName = "Alice",
            text = "Important caption stays forever",
            mediaId = "media_exp_1",
            mediaType = "image",
            mediaUrl = "https://supabase.example/expired.webp",
            mediaExpiresAt = expiredTimestamp,
            mediaExpired = true
        )

        // 1) No local copy and not in backup -> MISSING
        assertEquals(
            MediaAvailabilityState.MISSING,
            repo.resolveAvailabilityState(msg, localMedia = null, knownInBackup = false, nowMs = now)
        )

        // 2) Not local, but recorded in backup -> BACKUP_ONLY
        assertEquals(
            MediaAvailabilityState.BACKUP_ONLY,
            repo.resolveAvailabilityState(msg, localMedia = null, knownInBackup = true, nowMs = now)
        )

        // 3) Saved local file exists -> LOCAL_ONLY even after cloud expiry
        val localFile = File(context.filesDir, "local_media/conv_1/media_exp_1.webp").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        try {
            val localEntity = LocalMediaEntity(
                mediaId = "media_exp_1",
                messageId = "msg_exp_1",
                conversationId = "conv_1",
                mediaType = "image",
                localFilePath = localFile.absolutePath,
                mimeType = "image/webp",
                fileName = "media_exp_1.webp",
                sizeBytes = localFile.length(),
                createdAtMs = expiredTimestamp - (7L * 24L * 60L * 60L * 1000L),
                expiresAtMs = expiredTimestamp,
                cloudDeleted = true
            )
            assertEquals(
                MediaAvailabilityState.LOCAL_ONLY,
                repo.resolveAvailabilityState(msg, localMedia = localEntity, knownInBackup = true, nowMs = now)
            )
        } finally {
            localFile.delete()
        }
    }

    @Test
    fun encryptedBackupAndRestore_roundTripsDirectlyToLocalStorageAndRejectsWrongPassword(): Unit = runBlocking {
        val repo = LocalMediaRepository(context)
        val themeStore = ThemePreferencesStore(context)
        val now = System.currentTimeMillis()

        val localDir = File(context.filesDir, "local_media/conv_bk").apply { mkdirs() }
        val localImg = File(localDir, "m_bk_1.webp").apply {
            writeBytes(byteArrayOf(9, 8, 7, 6, 5, 4, 3, 2, 1))
        }

        repo.cacheConversations(
            listOf(Conversation(id = "conv_bk", type = "direct", title = "Backup Peer", memberUids = listOf("u1", "u2")))
        )
        repo.cacheMessages(
            listOf(
                ChatMessage(
                    id = "msg_bk_1",
                    conversationId = "conv_bk",
                    senderId = "u1",
                    senderName = "Alice",
                    text = "Saved photo",
                    mediaId = "m_bk_1",
                    mediaType = "image",
                    mediaUrl = "",
                    mediaExpiresAt = now - 10_000L,
                    mediaExpired = true
                )
            )
        )
        repo.saveLocalMediaRecord(
            LocalMediaEntity(
                mediaId = "m_bk_1",
                messageId = "msg_bk_1",
                conversationId = "conv_bk",
                mediaType = "image",
                localFilePath = localImg.absolutePath,
                mimeType = "image/webp",
                fileName = "m_bk_1.webp",
                sizeBytes = localImg.length(),
                createdAtMs = now - 20_000L,
                expiresAtMs = now - 10_000L,
                cloudDeleted = true
            )
        )

        val built = BackupCryptoZip.buildBackupArchive(
            context = context,
            localRepo = repo,
            themeStore = themeStore,
            userId = "u1",
            includeMedia = true,
            passphrase = "StrongPassword#2026"
        ).getOrThrow()

        assertTrue(BackupCryptoZip.isEncryptedPayload(built.bytes))
        assertTrue(built.manifest.encrypted)
        assertTrue("m_bk_1" in built.manifest.mediaIds)

        // Wrong password must fail
        val wrongRes = BackupCryptoZip.restoreFromBackupBytes(
            context = context,
            rawBytes = built.bytes,
            passphrase = "WrongPassword",
            localRepo = repo,
            themeStore = themeStore
        )
        assertTrue(wrongRes.isFailure)
        assertTrue(wrongRes.exceptionOrNull()?.message?.contains("Incorrect backup password") == true)

        // Delete local file to simulate clean device restore without cloud
        assertTrue(localImg.delete())
        assertFalse(localImg.exists())

        val okSummary = BackupCryptoZip.restoreFromBackupBytes(
            context = context,
            rawBytes = built.bytes,
            passphrase = "StrongPassword#2026",
            localRepo = repo,
            themeStore = themeStore
        ).getOrThrow()

        assertEquals(1, okSummary.mediaFilesRestored)
        assertTrue("Restored media should exist directly on device", localImg.exists())
        localImg.delete()
    }

    @Test
    fun offlineOutbox_queuesAndDeduplicatesByClientMessageId(): Unit = runBlocking {
        val repo = LocalMediaRepository(context)
        val item = PendingOutgoingMessageEntity(
            clientMessageId = "msg_offline_unique_1",
            conversationId = "conv_off",
            senderId = "u1",
            senderName = "Alice",
            senderAvatarUrl = "",
            text = "Sent on airplane mode",
            replyToId = "",
            replyToPreview = "",
            topicId = ""
        )
        repo.enqueueOfflineMessage(item)
        repo.enqueueOfflineMessage(item) // duplicate client ID
        val pending = repo.getPendingOutgoingMessages()
        assertEquals(1, pending.count { it.clientMessageId == "msg_offline_unique_1" })
        repo.removePendingOutgoing("msg_offline_unique_1")
        assertTrue(repo.getPendingOutgoingMessages().none { it.clientMessageId == "msg_offline_unique_1" })
    }

    @Test
    fun storageAndBackupScreen_rendersScheduleIncludeMediaToggleAndClearCache() {
        var cleared = false
        var selectedSchedule = BackupSchedule.OFF
        var includedMedia = true

        composeRule.setContent {
            PrivacyXTheme {
                var prefs by remember {
                    mutableStateOf(
                        BackupPreferences(
                            schedule = BackupSchedule.OFF,
                            includeMediaInBackup = true,
                            driveConnected = false
                        )
                    )
                }
                StorageAndBackupScreen(
                    storageStats = StorageStatsSnapshot(
                        localImagesCount = 4,
                        localVoiceCount = 2,
                        localDocumentsCount = 1,
                        localTotalBytes = 2048L * 1024L,
                        tempCacheBytes = 512L * 1024L,
                        activeCloudFilesCount = 3,
                        estimatedCloudBytes = 900L * 1024L
                    ),
                    backupPrefs = prefs,
                    driveBackups = emptyList(),
                    backupHistory = emptyList(),
                    isBackupBusy = false,
                    onBack = {},
                    onClearTemporaryCache = { cleared = true },
                    onConnectDrive = {},
                    onDisconnectDrive = {},
                    onRefreshDriveBackups = {},
                    onBackupNowToDrive = {},
                    onSelectSchedule = {
                        selectedSchedule = it
                        prefs = prefs.copy(schedule = it)
                    },
                    onToggleIncludeMedia = {
                        includedMedia = it
                        prefs = prefs.copy(includeMediaInBackup = it)
                    },
                    onRestoreDriveFile = { _, _ -> },
                    onExportLocalZip = {},
                    onRestoreLocalZip = {}
                )
            }
        }

        composeRule.onNodeWithText("7-Day Cloud Media Auto-Delete").assertIsDisplayed()
        composeRule.onNodeWithTag("storage_backup_list")
            .performScrollToNode(hasTestTag("clear_temp_cache_button"))
        composeRule.onNodeWithTag("clear_temp_cache_button").performClick()
        assertTrue(cleared)

        composeRule.onNodeWithTag("storage_backup_list")
            .performScrollToNode(hasTestTag("backup_schedule_weekly"))
        composeRule.onNodeWithTag("backup_schedule_weekly").performClick()
        assertEquals(BackupSchedule.WEEKLY, selectedSchedule)

        composeRule.onNodeWithTag("storage_backup_list")
            .performScrollToNode(hasTestTag("include_media_backup_switch"))
        composeRule.onNodeWithTag("include_media_backup_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Exclude Media From Backups?").assertIsDisplayed()
        composeRule.onNodeWithText("Exclude Media").performClick()
        composeRule.waitForIdle()
        assertFalse(includedMedia)
    }
}
