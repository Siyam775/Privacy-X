package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppConstants
import com.example.data.model.AbuseReport
import com.example.data.model.AdminLog
import com.example.data.model.Conversation
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.PlatformSettings
import com.example.data.model.UserProfile
import com.example.data.repository.AdminRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val adminRepo = AdminRepository(application.applicationContext)
    private val auth = Firebase.auth

    private val _users = MutableStateFlow<List<UserProfile>>(emptyList())
    val users: StateFlow<List<UserProfile>> = _users.asStateFlow()

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val _reports = MutableStateFlow<List<AbuseReport>>(emptyList())
    val reports: StateFlow<List<AbuseReport>> = _reports.asStateFlow()

    private val _logs = MutableStateFlow<List<AdminLog>>(emptyList())
    val logs: StateFlow<List<AdminLog>> = _logs.asStateFlow()

    private val _announcements = MutableStateFlow<List<PlatformAnnouncement>>(emptyList())
    val announcements: StateFlow<List<PlatformAnnouncement>> = _announcements.asStateFlow()

    private val _platformSettings = MutableStateFlow(PlatformSettings())
    val platformSettings: StateFlow<PlatformSettings> = _platformSettings.asStateFlow()

    private val _adminNotice = MutableStateFlow<String?>(null)
    val adminNotice: StateFlow<String?> = _adminNotice.asStateFlow()

    private var adminJobs: Job? = null

    fun bindAdminDashboard() {
        if (auth.currentUser?.uid != AppConstants.SUPER_ADMIN_UID) {
            _adminNotice.value = "Unauthorized: Restricted to Super Admin UID."
            return
        }
        adminJobs?.cancel()
        adminJobs = viewModelScope.launch {
            launch { adminRepo.observeAllUsersForAdmin().catch {}.collect { _users.value = it } }
            launch { adminRepo.observeAllConversationsForAdmin().catch {}.collect { _conversations.value = it } }
            launch { adminRepo.observeReportsForAdmin().catch {}.collect { _reports.value = it } }
            launch { adminRepo.observeAdminLogs().catch {}.collect { _logs.value = it } }
            launch { adminRepo.observePlatformAnnouncements().catch {}.collect { _announcements.value = it } }
            launch { adminRepo.observePlatformSettings().catch {}.collect { _platformSettings.value = it } }
        }
    }

    fun toggleUserSuspension(user: UserProfile) {
        viewModelScope.launch {
            val res = adminRepo.setUserSuspended(user.userId, !user.isSuspended)
            _adminNotice.value = if (res.isSuccess) {
                if (user.isSuspended) "Restored @${user.username}" else "Suspended @${user.username}"
            } else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun deleteConversation(conv: Conversation) {
        viewModelScope.launch {
            val res = adminRepo.deleteConversationAsAdmin(conv.id, conv.title)
            _adminNotice.value = if (res.isSuccess) "Removed ${conv.title}" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun updateReportStatus(reportId: String, status: String) {
        viewModelScope.launch {
            val res = adminRepo.updateReportStatus(reportId, status)
            _adminNotice.value = if (res.isSuccess) "Report marked $status" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun publishAnnouncement(title: String, body: String, priority: String) {
        if (title.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            val res = adminRepo.publishAnnouncement(title, body, priority)
            _adminNotice.value = if (res.isSuccess) "Announcement broadcast to all users" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun savePlatformSettings(maxUploadMb: Long, allowReg: Boolean, maintenance: Boolean) {
        viewModelScope.launch {
            val res = adminRepo.updatePlatformSettings(maxUploadMb, allowReg, maintenance)
            _adminNotice.value = if (res.isSuccess) "Platform settings saved" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun clearNotice() {
        _adminNotice.value = null
    }
}
