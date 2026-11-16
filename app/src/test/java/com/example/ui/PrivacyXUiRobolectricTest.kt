package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.example.core.AppConstants
import com.example.data.model.PlatformSettings
import com.example.data.model.UserProfile
import com.example.data.repository.AuthRepository
import com.example.ui.components.RadialAction
import com.example.ui.components.RadialFabMenuOverlay
import com.example.ui.screens.AuthSignInScreen
import com.example.ui.screens.SuperAdminDashboardScreen
import com.example.ui.screens.UsernameOnboardingScreen
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.PrivacyXTheme
import com.example.ui.theme.buildThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyXUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allThreeThemes_produceDistinctTokensAndRenderCleanly() {
        val liquid = buildThemeTokens(AppThemeId.MATERIAL_LIQUID_GLASS, isDark = false, customAccentColor = null)
        val iosGlass = buildThemeTokens(AppThemeId.PURE_IOS_GLASS, isDark = false, customAccentColor = null)
        val classicBw = buildThemeTokens(AppThemeId.CLASSIC_BW, isDark = true, customAccentColor = null)

        assertTrue(liquid.usesGlass)
        assertTrue(liquid.usesBlur)
        assertTrue(iosGlass.usesGlass)
        assertFalse(classicBw.usesGlass)
        assertFalse(classicBw.usesBlur)
        assertTrue(iosGlass.cardCornerRadius > classicBw.cardCornerRadius)

        composeRule.setContent {
            var activeTheme by remember { mutableStateOf(AppThemeId.MATERIAL_LIQUID_GLASS) }
            PrivacyXTheme(themeId = activeTheme, appearanceMode = AppearanceMode.LIGHT) {
                AuthSignInScreen(isBusy = false, errorMessage = null, onGoogleSignIn = {
                    activeTheme = AppThemeId.CLASSIC_BW
                })
            }
        }
        composeRule.onNodeWithText("PrivacyX").assertIsDisplayed()
        composeRule.onNodeWithTag("google_sign_in_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
    }

    @Test
    fun radialFabMenu_expandsAllTwelveActionsAndDismissesOnScrim() {
        var lastAction: RadialAction? = null
        composeRule.setContent {
            PrivacyXTheme {
                var expanded by remember { mutableStateOf(false) }
                RadialFabMenuOverlay(
                    isExpanded = expanded,
                    reducedMotion = true,
                    onToggle = { expanded = !expanded },
                    onDismiss = { expanded = false },
                    onActionSelected = {
                        lastAction = it
                        expanded = false
                    }
                )
            }
        }

        // Open radial menu
        composeRule.onNodeWithTag("radial_fab_button").performClick()
        composeRule.waitForIdle()

        // Verify all 12 radial actions exist
        RadialAction.entries.forEach { action ->
            composeRule.onNodeWithTag(action.testTag).assertExists()
        }

        // Tap AI Assistant action
        composeRule.onNodeWithTag(RadialAction.AI_ASSISTANT.testTag).performClick()
        composeRule.waitForIdle()
        assertEquals(RadialAction.AI_ASSISTANT, lastAction)

        // Re-open and dismiss via scrim top-left corner
        composeRule.onNodeWithTag("radial_fab_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("radial_menu_scrim").performTouchInput {
            click(Offset(16f, 16f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RadialAction.NEW_CHAT.testTag).assertDoesNotExist()
    }

    @Test
    fun usernameOnboarding_validatesAndSubmitsNormalizedUsername() {
        var submittedUsername = ""
        composeRule.setContent {
            PrivacyXTheme {
                UsernameOnboardingScreen(
                    suggestedName = "Alice",
                    email = "alice@example.com",
                    isBusy = false,
                    errorMessage = null,
                    onCheckAvailability = { candidate -> candidate != "taken_name" },
                    onSubmitProfile = { _, username, _ -> submittedUsername = username },
                    onSignOut = {}
                )
            }
        }

        // Invalid short username disables submit
        composeRule.onNodeWithTag("onboarding_username_input").performTextReplacement("ab")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_submit_button").assertIsNotEnabled()

        // Valid available username enables submit after debounce
        composeRule.onNodeWithTag("onboarding_username_input").performTextReplacement("Alice_99")
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_submit_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("alice_99", submittedUsername)
    }

    @Test
    fun superAdminDashboard_strictlyGatesToExactUid() {
        assertEquals("2bL4lJTTt9WMILpCclHeKNYOdz62", AppConstants.SUPER_ADMIN_UID)
        composeRule.setContent {
            PrivacyXTheme {
                SuperAdminDashboardScreen(
                    isAuthorizedSuperAdmin = false,
                    users = listOf(UserProfile(userId = "u1", username = "bob", displayName = "Bob")),
                    conversations = emptyList(),
                    reports = emptyList(),
                    logs = emptyList(),
                    platformSettings = PlatformSettings(),
                    onBack = {},
                    onToggleSuspendUser = {},
                    onDeleteConversation = {},
                    onUpdateReportStatus = { _, _ -> },
                    onPublishAnnouncement = { _, _, _ -> },
                    onSavePlatformSettings = { _, _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText(
            "Access Denied: This console is strictly locked to the designated Super Admin UID."
        ).assertIsDisplayed()
    }

    @Test
    fun usernameHelpersAndSecretCleaner_workCorrectly() {
        assertEquals("johndoe99", AuthRepository.normalizeUsername("@John Doe!! 99"))
        assertEquals("john_doe", AuthRepository.normalizeUsername("@John_Doe"))
        assertTrue(AuthRepository.isValidUsernameFormat("john_doe99"))
        assertFalse(AuthRepository.isValidUsernameFormat("ab"))
        assertEquals("", AppConstants.cleanSecret("MY_SUPABASE_URL"))
        assertEquals("https://demo.supabase.co", AppConstants.cleanSecret("https://demo.supabase.co"))
    }

    @Test
    fun supabaseMediaService_validatesFileM4aVoiceUriAsAudioMp4() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val tempFile = java.io.File(context.cacheDir, "test_voice_${System.currentTimeMillis()}.m4a")
        tempFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))
        try {
            val service = com.example.media.SupabaseMediaService(context)
            val result = service.validateUri(android.net.Uri.fromFile(tempFile))
            assertTrue("Expected .m4a file URI to validate cleanly: ${result.exceptionOrNull()}", result.isSuccess)
            val validated = result.getOrThrow()
            assertEquals("voice", validated.category)
            assertEquals("audio/mp4", validated.mimeType)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun groupChatDetail_showsGroupCallButtonsSearchCustomizationAndAdminControls() {
        var voiceCallClicked = false
        var videoCallClicked = false
        var selectedColorId = ""
        var selectedWallpaperId = ""
        var kickedUid = ""

        val currentUser = UserProfile(userId = "u_admin", username = "alice", displayName = "Alice")
        val groupConv = com.example.data.model.Conversation(
            id = "grp_1",
            type = "group",
            title = "Study Squad",
            description = "Team channel",
            ownerId = "u_admin",
            memberUids = listOf("u_admin", "u_bob"),
            adminUids = listOf("u_admin"),
            inviteCode = "SQUAD123"
        )
        val directory = listOf(
            com.example.data.model.UsernameReservation(username = "alice", userId = "u_admin", displayName = "Alice"),
            com.example.data.model.UsernameReservation(username = "bob", userId = "u_bob", displayName = "Bob")
        )
        val sampleMsgs = listOf(
            com.example.data.model.ChatMessage(
                id = "m1",
                conversationId = "grp_1",
                senderId = "u_bob",
                senderName = "Bob",
                text = "Physics notes uploaded",
                mediaType = "text",
                isPinned = true
            )
        )

        composeRule.setContent {
            PrivacyXTheme {
                com.example.ui.screens.ChatDetailScreen(
                    currentUser = currentUser,
                    conversation = groupConv,
                    messages = sampleMsgs,
                    allConversations = listOf(groupConv),
                    members = emptyList(),
                    topics = emptyList(),
                    directoryUsers = directory,
                    draftText = "",
                    uploadState = com.example.ui.viewmodel.UploadUiState(),
                    recordingState = com.example.media.VoiceRecordingState(),
                    playbackState = com.example.media.VoicePlaybackState(),
                    smartReplies = emptyList(),
                    aiHelperResult = null,
                    aiConsentGranted = false,
                    onBack = {},
                    onDraftChange = {},
                    onTyping = {},
                    onLoadMore = {},
                    onSendMessage = { _, _, _, _, _ -> },
                    onSendSticker = {},
                    onUploadMedia = { _, _, _ -> },
                    onCancelUpload = {},
                    onRetryUpload = {},
                    onStartRecording = {},
                    onPauseRecording = {},
                    onCancelRecording = {},
                    onSendRecording = {},
                    onTogglePlayVoice = {},
                    onCycleVoiceSpeed = {},
                    onEditMessage = { _, _ -> },
                    onDeleteMessage = {},
                    onToggleReaction = { _, _ -> },
                    onVotePoll = { _, _ -> },
                    onTogglePin = {},
                    onForwardMessage = { _, _ -> },
                    onBookmarkMessage = {},
                    onCreatePoll = { _, _ -> },
                    onCreateTopic = { _, _ -> },
                    onSummarizeChat = {},
                    onTranslateMessage = {},
                    onGenerateSmartReplies = {},
                    onDismissAiHelper = {},
                    onStartVoiceCall = { voiceCallClicked = true },
                    onStartVideoCall = { videoCallClicked = true },
                    onToggleMute = {},
                    onLeaveConversation = {},
                    onReportConversation = { _, _ -> },
                    onSelectColorPreset = { selectedColorId = it },
                    onSelectWallpaperPreset = { selectedWallpaperId = it },
                    onRemoveMember = { kickedUid = it }
                )
            }
        }

        // Verify group voice and video call buttons exist and trigger callbacks
        composeRule.onNodeWithTag("chat_voice_call_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("chat_video_call_button").assertIsDisplayed().performClick()
        assertTrue(voiceCallClicked)
        assertTrue(videoCallClicked)

        // Open in-chat search and find matching message
        composeRule.onNodeWithTag("chat_search_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_search_input").performTextReplacement("Physics")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1/1").assertIsDisplayed()

        // Open Colors & Wallpaper studio and select ocean_blue + lavender_aurora
        assertEquals(12, com.example.ui.theme.ChatColorPreset.entries.size)
        assertEquals(10, com.example.ui.theme.ChatWallpaperPreset.entries.size)
        composeRule.onNodeWithTag("chat_theme_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("color_preset_ocean_blue").performClick()
        composeRule.onNodeWithTag("wallpaper_preset_lavender_aurora").performClick()
        assertEquals("ocean_blue", selectedColorId)
        assertEquals("lavender_aurora", selectedWallpaperId)
        composeRule.onNodeWithTag("appearance_close_button").performClick()
        composeRule.waitForIdle()

        // Open Info & Admin Management sheet and kick Bob
        composeRule.onNodeWithTag("chat_info_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("space_info_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("kick_member_u_bob"))
        composeRule.onNodeWithTag("kick_member_u_bob").performClick()
        assertEquals("u_bob", kickedUid)
    }
}
