package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PrivacyXRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun claimUsernameAndProfile_validPayload_succeedsAndBlocksDuplicates() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().replace("-", "").take(6)
        val username = "alice_$uniqueSuffix"

        val aliceUid = signInTestUser("alice_$uniqueSuffix@test.com")
        val authRepo = AuthRepository(firestore)
        val result = withTimeout(TIMEOUT_MS) {
            authRepo.claimUsernameAndCreateProfile(
                displayName = "Alice Tester",
                rawUsername = username,
                bio = "PrivacyX user",
                photoUrl = "",
                email = "alice_$uniqueSuffix@test.com"
            )
        }
        assertTrue("Profile claim failed: ${result.exceptionOrNull()}", result.isSuccess)
        assertFalse(withTimeout(TIMEOUT_MS) { authRepo.isUsernameAvailable(username) })

        // Bob cannot steal Alice's username
        signInTestUser("bob_$uniqueSuffix@test.com")
        val bobAttempt = withTimeout(TIMEOUT_MS) {
            authRepo.claimUsernameAndCreateProfile(
                displayName = "Bob Tester",
                rawUsername = username,
                bio = "",
                photoUrl = "",
                email = "bob_$uniqueSuffix@test.com"
            )
        }
        assertTrue("Bob should be blocked from claiming Alice's username", bobAttempt.isFailure)
        assertTrue(aliceUid.isNotBlank())
    }

    @Test
    fun messagingRepository_createDirectChatAndSendMessage_emitsRealtimeAndIsolatesNonMembers() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
        val aliceUid = signInTestUser("msg_alice_$suffix@test.com")
        val bobUid = signInTestUser("msg_bob_$suffix@test.com")
        val charlieUid = signInTestUser("msg_charlie_$suffix@test.com")

        // Switch back to Alice
        signInTestUser("msg_alice_$suffix@test.com")
        val msgRepo = MessagingRepository(firestore)

        val convId = withTimeout(TIMEOUT_MS) {
            msgRepo.createOrGetDirectConversation(bobUid, "Bob", "").getOrThrow()
        }

        val conversations = withTimeout(FLOW_TIMEOUT_MS) {
            msgRepo.observeMyConversations(aliceUid).first { list -> list.any { it.id == convId } }
        }
        val conv = conversations.first { it.id == convId }

        val msgId = withTimeout(TIMEOUT_MS) {
            msgRepo.sendMessage(
                conversation = conv,
                senderName = "Alice",
                senderAvatarUrl = "",
                text = "Hello Bob over PrivacyX!"
            ).getOrThrow()
        }

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            msgRepo.observeMessages(convId).first { list -> list.any { it.id == msgId } }
        }
        assertEquals("Hello Bob over PrivacyX!", messages.first { it.id == msgId }.text)

        // Charlie (non-member) must be denied
        signInTestUser("msg_charlie_$suffix@test.com")
        assertTrue(charlieUid.isNotBlank())
        var denied = false
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("conversations").document(convId).get().await()
            }
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            denied = fsErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Charlie must receive PERMISSION_DENIED on Alice-Bob DM", denied)
    }

    @Test
    fun unauthenticatedCaller_observeConversations_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val msgRepo = MessagingRepository(firestore)
        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                msgRepo.observeMyConversations("some_uid").first()
            }
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            denied = fsErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Unauthenticated listener must fail with PERMISSION_DENIED", denied)
    }

    @Test
    fun adminRepository_ordinaryUser_isRejectedFromAdminCollections() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
        signInTestUser("normie_$suffix@test.com")
        var denied = false
        try {
            withTimeout(TIMEOUT_MS) {
                firestore.collection("adminLogs").get().await()
            }
        } catch (e: Exception) {
            val fsErr = e.unwrapFirestoreException()
            denied = fsErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Ordinary user must receive PERMISSION_DENIED on adminLogs", denied)
    }

    @Test
    fun groupManagement_promoteDemoteAndAdminsOnlyMessaging_enforcedByRules() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
        val aliceUid = signInTestUser("grp_alice_$suffix@test.com")
        val bobUid = signInTestUser("grp_bob_$suffix@test.com")

        signInTestUser("grp_alice_$suffix@test.com")
        val msgRepo = MessagingRepository(firestore)
        val convId = withTimeout(TIMEOUT_MS) {
            msgRepo.createGroupOrCommunityOrChannel(
                type = "group",
                title = "Study Squad",
                description = "Physics notes",
                isPublic = false,
                memberUids = listOf(bobUid)
            ).getOrThrow()
        }

        // Update settings to adminsOnlyMessaging = true and slowModeSeconds = 15
        val updateRes = withTimeout(TIMEOUT_MS) {
            msgRepo.updateConversationSettings(
                conversationId = convId,
                title = "Study Squad VIP",
                slowModeSeconds = 15L,
                adminsOnlyMessaging = true
            )
        }
        assertTrue("Admin settings update failed: ${updateRes.exceptionOrNull()}", updateRes.isSuccess)

        val conv = withTimeout(FLOW_TIMEOUT_MS) {
            msgRepo.observeMyConversations(aliceUid).first { list ->
                list.any { it.id == convId && it.adminsOnlyMessaging }
            }.first { it.id == convId }
        }

        // Bob (not yet admin) should be blocked from posting while adminsOnlyMessaging = true
        signInTestUser("grp_bob_$suffix@test.com")
        val bobBlockedRes = withTimeout(TIMEOUT_MS) {
            msgRepo.sendMessage(
                conversation = conv,
                senderName = "Bob",
                senderAvatarUrl = "",
                text = "Should fail before promotion"
            )
        }
        assertTrue("Non-admin Bob must be blocked when adminsOnlyMessaging is true", bobBlockedRes.isFailure)

        // Alice promotes Bob -> Bob can post -> Alice demotes Bob
        signInTestUser("grp_alice_$suffix@test.com")
        assertTrue(withTimeout(TIMEOUT_MS) { msgRepo.promoteMemberToAdmin(convId, bobUid) }.isSuccess)

        val convAfterPromote = withTimeout(FLOW_TIMEOUT_MS) {
            msgRepo.observeMyConversations(aliceUid).first { list ->
                list.any { it.id == convId && bobUid in it.adminUids }
            }.first { it.id == convId }
        }
        signInTestUser("grp_bob_$suffix@test.com")
        val bobAdminRes = withTimeout(TIMEOUT_MS) {
            msgRepo.sendMessage(
                conversation = convAfterPromote,
                senderName = "Bob",
                senderAvatarUrl = "",
                text = "Now an admin!"
            )
        }
        assertTrue("Promoted admin Bob should send successfully: ${bobAdminRes.exceptionOrNull()}", bobAdminRes.isSuccess)

        signInTestUser("grp_alice_$suffix@test.com")
        assertTrue(withTimeout(TIMEOUT_MS) { msgRepo.demoteMemberFromAdmin(convId, bobUid) }.isSuccess)
    }

    @Test
    fun messagingRepository_videoMessageAllowedAndMimeCategoryValidated() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(6)
        val aliceUid = signInTestUser("vid_alice_$suffix@test.com")
        val bobUid = signInTestUser("vid_bob_$suffix@test.com")

        signInTestUser("vid_alice_$suffix@test.com")
        val msgRepo = MessagingRepository(firestore)
        val convId = withTimeout(TIMEOUT_MS) {
            msgRepo.createOrGetDirectConversation(bobUid, "Bob", "").getOrThrow()
        }
        val conv = withTimeout(FLOW_TIMEOUT_MS) {
            msgRepo.observeMyConversations(aliceUid).first { list -> list.any { it.id == convId } }
        }.first { it.id == convId }

        val videoTypeRes = withTimeout(TIMEOUT_MS) {
            msgRepo.sendMessage(
                conversation = conv,
                senderName = "Alice",
                senderAvatarUrl = "",
                text = "Video test",
                mediaType = "video",
                mimeType = "video/mp4"
            )
        }
        assertTrue("Video mediaType should be accepted", videoTypeRes.isSuccess)

        val videoMimeRes = withTimeout(TIMEOUT_MS) {
            msgRepo.sendMessage(
                conversation = conv,
                senderName = "Alice",
                senderAvatarUrl = "",
                text = "Video mime test",
                mediaType = "document",
                mimeType = "video/mp4"
            )
        }
        assertTrue("Video MIME type must not be disguised as a document", videoMimeRes.isFailure)
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
